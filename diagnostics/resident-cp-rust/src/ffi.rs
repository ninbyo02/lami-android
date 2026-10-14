//! Token handles and synchronous commands; QNN always stays on its owning thread.
use crate::{EmbeddedBackend, ResidentCp};
use std::{collections::HashMap, ffi::{c_char, CStr}, panic::{catch_unwind, AssertUnwindSafe},
    sync::{mpsc, Mutex, OnceLock, atomic::{AtomicU64, Ordering}}, thread};

#[cfg(all(feature = "ffi-host-test", target_os = "android"))]
compile_error!("The fake FFI backend must never be compiled for Android");

#[cfg(target_os = "android")]
fn cp_android_error(message: &str) {
    use std::ffi::CString;
    extern "C" { fn __android_log_write(priority: i32, tag: *const c_char, text: *const c_char) -> i32; }
    if let (Ok(tag), Ok(message)) = (CString::new("LamiCpRust"), CString::new(message)) {
        unsafe { __android_log_write(6, tag.as_ptr(), message.as_ptr()); }
    }
}
#[cfg(not(target_os = "android"))]
fn cp_android_error(_message: &str) {}

const INVALID: i32 = 1;
const STALE: i32 = 2;
const BACKEND: i32 = 3;
const INTERNAL: i32 = 4;
const WIDTH: usize = 1024;
pub const STATS: usize = 7;
type Stats = [u64; STATS];
enum Operation { Reset, Step(u8, Vec<f32>), Cancel, Stats, Close }
enum Reply { Unit, Hidden(Vec<f32>), Stats(Stats) }
struct Request { operation: Operation, reply: mpsc::Sender<Result<Reply, i32>> }
struct Worker { sender: mpsc::Sender<Request>, thread: Option<thread::JoinHandle<()>> }

impl Worker {
    fn spawn<B, F, S>(factory: F, stats: S) -> Result<Self, i32>
    where B: EmbeddedBackend + 'static, B::Error: 'static, F: FnOnce() -> Result<B, i32> + Send + 'static,
          S: Fn(&B) -> Stats + Send + 'static {
        let (sender, receiver) = mpsc::channel::<Request>();
        let (ready, waiting) = mpsc::channel();
        let worker = thread::Builder::new().name("lami-cp-diag".into()).spawn(move || {
            let backend = match factory() { Ok(b) => b, Err(e) => {
                cp_android_error(&format!("worker_factory_failed={e}"));
                let _ = ready.send(Err(e)); return;
            } };
            let mut cp = ResidentCp::new(backend);
            if let Err(error) = cp.load_once() {
                eprintln!("lami_cp_worker_open_error={error:?}");
                cp_android_error(&format!("worker_open_failed={error:?}"));
                // 101..108 identify C QNN open stages; 3 remains a generic backend failure.
                #[cfg(feature = "qnn-sdk")]
                let code = match &error {
                    crate::CpError::Backend(error) => {
                        match (error as &dyn std::any::Any).downcast_ref::<crate::qnn::QnnError>() {
                            Some(crate::qnn::QnnError::Sdk(encoded)) => {
                                let stage = (encoded >> 56) as i32;
                                if (1..=8).contains(&stage) { 100 + stage } else { BACKEND }
                            }
                            _ => BACKEND,
                        }
                    }
                    _ => BACKEND
                };
                #[cfg(not(feature = "qnn-sdk"))]
                let code = BACKEND;
                let _ = ready.send(Err(code)); return;
            }
            if ready.send(Ok(())).is_err() { return; }
            let (mut resets, mut cancels) = (0, 0);
            let mut faulted = false;
            while let Ok(request) = receiver.recv() {
                let close = matches!(request.operation, Operation::Close);
                let response = if faulted && !matches!(request.operation, Operation::Stats | Operation::Close) {
                    Err(BACKEND)
                } else { match request.operation {
                    Operation::Reset => cp.cancel_frame().and_then(|()| cp.start_frame())
                        .map(|()| { resets += 1; Reply::Unit }).map_err(|_| BACKEND),
                    Operation::Step(position, input) => cp.step_with_embedding(position, &input)
                        .map(|hidden| Reply::Hidden(hidden.to_vec())).map_err(|_| BACKEND),
                    Operation::Cancel => cp.cancel_frame().map(|()| { cancels += 1; Reply::Unit }).map_err(|_| BACKEND),
                    Operation::Stats => { let mut values = stats(cp.backend_ref()); values[3] = resets; values[4] = cancels; Ok(Reply::Stats(values)) },
                    Operation::Close => { cp.close(); Ok(Reply::Unit) },
                } };
                if response.is_err() { faulted = true; }
                let _ = request.reply.send(response);
                if close { break; }
            }
        }).map_err(|_| INTERNAL)?;
        match waiting.recv() {
            Ok(Ok(())) => Ok(Self { sender, thread: Some(worker) }),
            response => { let _ = worker.join(); Err(response.ok().and_then(Result::err).unwrap_or(INTERNAL)) }
        }
    }
    fn call(&self, operation: Operation) -> Result<Reply, i32> {
        let (reply, waiting) = mpsc::channel();
        self.sender.send(Request { operation, reply }).map_err(|_| INTERNAL)?;
        waiting.recv().map_err(|_| INTERNAL)?
    }
}
impl Drop for Worker {
    fn drop(&mut self) {
        let _ = self.call(Operation::Close);
        if let Some(worker) = self.thread.take() { let _ = worker.join(); }
    }
}
fn registry() -> &'static Mutex<HashMap<u64, Worker>> {
    static REGISTRY: OnceLock<Mutex<HashMap<u64, Worker>>> = OnceLock::new();
    REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}
fn insert(worker: Worker) -> Result<u64, i32> {
    static NEXT: AtomicU64 = AtomicU64::new(1);
    let mut value = NEXT.load(Ordering::Relaxed);
    let handle = loop {
        if value >= i64::MAX as u64 { return Err(INTERNAL); }
        match NEXT.compare_exchange_weak(value, value + 1, Ordering::Relaxed, Ordering::Relaxed) {
            Ok(_) => break value,
            Err(actual) => value = actual,
        }
    };
    registry().lock().map_err(|_| INTERNAL)?.insert(handle, worker);
    Ok(handle)
}
fn command(handle: u64, operation: Operation) -> Result<Reply, i32> {
    registry().lock().map_err(|_| INTERNAL)?.get(&handle).ok_or(STALE)?.call(operation)
}
fn release(handle: u64) -> Result<(), i32> {
    let worker = registry().lock().map_err(|_| INTERNAL)?.remove(&handle).ok_or(STALE)?;
    drop(worker); Ok(())
}
fn guarded(operation: impl FnOnce() -> Result<(), i32>) -> i32 {
    match catch_unwind(AssertUnwindSafe(operation)) { Ok(Ok(())) => 0, Ok(Err(code)) => code, Err(_) => INTERNAL }
}

/// # Safety
/// All pointers must reference valid immutable bytes/C strings for this call.
/// The output must reference writable u64 storage. No caller pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn lami_cp_create(bytes: *const u8, length: usize, backend: *const c_char,
    system: *const c_char, rpc: *const c_char, handle: *mut u64) -> i32 {
    if handle.is_null() { return INVALID; }
    handle.write(0);
    guarded(|| {
        if bytes.is_null() || length == 0 || length > 512 * 1024 * 1024 || backend.is_null() || system.is_null() || rpc.is_null() { return Err(INVALID); }
        let bytes = std::slice::from_raw_parts(bytes, length).to_vec();
        let paths: Vec<String> = [backend, system, rpc].into_iter().map(|path|
            CStr::from_ptr(path).to_str().map(str::to_owned).map_err(|_| INVALID)).collect::<Result<_, _>>()?;
        #[cfg(feature = "ffi-host-test")]
        if paths[0] == "host-test" || paths[0].ends_with("/host-test/libQnnHtp.so") {
            if bytes != b"LAMI_CP_HOST_TEST" { return Err(INVALID); }
            handle.write(insert(test_backend::worker(false)?)?); return Ok(());
        }
        #[cfg(feature = "qnn-sdk")]
        {
        let worker = Worker::spawn(move || {
            let mut qnn = crate::qnn::QnnBackend::from_verified_bytes(bytes, &paths[0], &paths[1]).map_err(|_| BACKEND)?;
            qnn.use_registered_rpc_buffers(&paths[2]).map_err(|_| BACKEND)?;
            Ok(qnn)
        }, |qnn| {
            let t = qnn.timings(); let memory = qnn.sdk_memory_stats();
            [t.opens as u64, t.executions as u64, memory[0], 0, 0,
             u64::try_from(t.context_open_ns).unwrap_or(u64::MAX), memory[1]]
        })?;
        handle.write(insert(worker)?); Ok(())
        }
        #[cfg(not(feature = "qnn-sdk"))]
        { let _ = (bytes, paths); Err(BACKEND) }
    })
}
#[no_mangle]
pub extern "C" fn lami_cp_reset(handle: u64) -> i32 { guarded(|| command(handle, Operation::Reset).map(|_| ())) }
#[no_mangle]
pub extern "C" fn lami_cp_cancel(handle: u64) -> i32 { guarded(|| command(handle, Operation::Cancel).map(|_| ())) }
#[no_mangle]
pub extern "C" fn lami_cp_release(handle: u64) -> i32 { guarded(|| release(handle)) }
/// # Safety
/// Input/output must reference `length` readable/writable f32 values for this call.
/// Caller arrays are copied; only owned vectors cross the worker-thread boundary.
#[no_mangle]
pub unsafe extern "C" fn lami_cp_step(handle: u64, position: i32, input: *const f32, output: *mut f32, length: usize) -> i32 {
    guarded(|| {
        if !(0..16).contains(&position) || length != WIDTH || input.is_null() || output.is_null() { return Err(INVALID); }
        let values = std::slice::from_raw_parts(input, length).to_vec();
        if !values.iter().all(|x| x.is_finite()) { return Err(INVALID); }
        match command(handle, Operation::Step(position as u8, values))? {
            Reply::Hidden(hidden) if hidden.len() == WIDTH && hidden.iter().all(|x| x.is_finite()) => {
                std::ptr::copy_nonoverlapping(hidden.as_ptr(), output, WIDTH); Ok(())
            }, _ => Err(BACKEND),
        }
    })
}
/// # Safety
/// Output must reference exactly STATS writable u64 values for this call.
#[no_mangle]
pub unsafe extern "C" fn lami_cp_stats(handle: u64, output: *mut u64, length: usize) -> i32 {
    guarded(|| {
        if output.is_null() || length != STATS { return Err(INVALID); }
        match command(handle, Operation::Stats)? {
            Reply::Stats(stats) => { std::ptr::copy_nonoverlapping(stats.as_ptr(), output, STATS); Ok(()) },
            _ => Err(INTERNAL),
        }
    })
}

#[cfg(any(test, feature = "ffi-host-test"))]
mod test_backend {
    use super::*;
    struct Fake { hidden: Vec<f32>, opens: u64, executions: u64, fail: bool }
    impl crate::Backend for Fake {
        type Error = &'static str;
        fn open(&mut self) -> Result<(), Self::Error> { self.opens += 1; Ok(()) }
        fn clear_kv(&mut self) -> Result<(), Self::Error> { self.hidden.fill(0.); Ok(()) }
        fn execute(&mut self, _: u8) -> Result<(), Self::Error> { if self.fail { Err("failure") } else { self.executions += 1; Ok(()) } }
        fn close(&mut self) {}
    }
    impl EmbeddedBackend for Fake {
        fn stage_embedding(&mut self, value: &[f32]) -> Result<(), Self::Error> { self.hidden.copy_from_slice(value); Ok(()) }
        fn hidden(&self) -> &[f32] { &self.hidden }
    }
    pub(super) fn worker(fail: bool) -> Result<Worker, i32> {
        Worker::spawn(move || Ok(Fake { hidden: vec![0.; WIDTH], opens: 0, executions: 0, fail }),
            |fake| [fake.opens, fake.executions, 0, 0, 0, 0, 0])
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn reuse_cancel_release_and_stale_handles() {
        let handle = insert(test_backend::worker(false).unwrap()).unwrap();
        for _ in 0..2 {
            assert_eq!(lami_cp_reset(handle), 0);
            for position in 0..16 {
                let input = vec![position as f32; WIDTH]; let mut output = vec![-1.; WIDTH];
                assert_eq!(unsafe { lami_cp_step(handle, position, input.as_ptr(), output.as_mut_ptr(), WIDTH) }, 0);
                assert_eq!(input, output);
            }
        }
        assert_eq!(lami_cp_reset(handle), 0);
        assert_eq!(lami_cp_cancel(handle), 0);
        assert_eq!(lami_cp_reset(handle), 0);
        let mut stats = [0; STATS];
        assert_eq!(unsafe { lami_cp_stats(handle, stats.as_mut_ptr(), STATS) }, 0);
        assert_eq!(&stats[..5], &[1, 32, 0, 4, 1]);
        assert_eq!(lami_cp_release(handle), 0);
        assert_eq!(lami_cp_release(handle), STALE);
        assert_eq!(lami_cp_reset(handle), STALE);
    }
    #[test]
    fn failure_requires_fresh_context() {
        let handle = insert(test_backend::worker(true).unwrap()).unwrap();
        assert_eq!(lami_cp_reset(handle), 0);
        let mut output = [0.; WIDTH]; let input = [1.; WIDTH];
        assert_eq!(unsafe { lami_cp_step(handle, 0, input.as_ptr(), output.as_mut_ptr(), WIDTH) }, BACKEND);
        assert_eq!(lami_cp_reset(handle), BACKEND);
        assert_eq!(lami_cp_release(handle), 0);
        let fresh = insert(test_backend::worker(false).unwrap()).unwrap();
        assert_ne!(fresh, handle); assert_eq!(lami_cp_reset(fresh), 0);
        assert_eq!(lami_cp_release(fresh), 0);
    }
    #[test]
    fn other_calling_thread_and_invalid_buffer_are_safe() {
        let handle = insert(test_backend::worker(false).unwrap()).unwrap();
        assert_eq!(thread::spawn(move || lami_cp_reset(handle)).join().unwrap(), 0);
        assert_eq!(unsafe { lami_cp_step(handle, 0, std::ptr::null(), std::ptr::null_mut(), WIDTH) }, INVALID);
        assert_eq!(lami_cp_release(handle), 0);
    }
}
