//! Rust ownership boundary for the resident CP graph.
//! Qualcomm QNN FFI and Android JNI are deliberately not implemented here.
//! No graph reload is allowed between CP positions or frames.
use std::fmt::Debug;

pub const STEPS_PER_FRAME: u8 = 16;

pub trait Backend {
    type Error: Debug;
    fn open(&mut self) -> Result<(), Self::Error>;
    fn clear_kv(&mut self) -> Result<(), Self::Error>;
    fn execute(&mut self, position: u8) -> Result<(), Self::Error>;
    fn close(&mut self);
}

#[derive(Debug, PartialEq, Eq)]
pub enum CpError<E> {
    InvalidState,
    WrongPosition { expected: u8, actual: u8 },
    Backend(E),
}

pub struct ResidentCp<B: Backend> {
    backend: B,
    loaded: bool,
    poisoned: bool,
    closed: bool,
    next_position: u8,
}

impl<B: Backend> ResidentCp<B> {
    pub fn new(backend: B) -> Self {
        Self { backend, loaded: false, poisoned: false, closed: false, next_position: STEPS_PER_FRAME }
    }

    pub fn start_frame(&mut self) -> Result<(), CpError<B::Error>> {
        if self.closed || self.poisoned || self.next_position != STEPS_PER_FRAME {
            return Err(CpError::InvalidState);
        }
        if !self.loaded {
            if let Err(e) = self.backend.open() {
                self.poisoned = true;
                return Err(CpError::Backend(e));
            }
            self.loaded = true;
        }
        if let Err(e) = self.backend.clear_kv() {
            self.poisoned = true;
            return Err(CpError::Backend(e));
        }
        self.next_position = 0;
        Ok(())
    }

    pub fn step(&mut self, position: u8) -> Result<(), CpError<B::Error>> {
        if self.closed || self.poisoned || !self.loaded || self.next_position >= STEPS_PER_FRAME {
            return Err(CpError::InvalidState);
        }
        if position != self.next_position {
            return Err(CpError::WrongPosition { expected: self.next_position, actual: position });
        }
        if let Err(e) = self.backend.execute(position) {
            self.poisoned = true;
            self.next_position = STEPS_PER_FRAME;
            return Err(CpError::Backend(e));
        }
        self.next_position += 1;
        Ok(())
    }

    pub fn cancel_frame(&mut self) -> Result<(), CpError<B::Error>> {
        if self.closed || self.poisoned {
            return Err(CpError::InvalidState);
        }
        self.next_position = STEPS_PER_FRAME;
        Ok(())
    }

    pub fn close(&mut self) {
        if !self.closed {
            self.closed = true;
            if self.loaded {
                self.backend.close();
                self.loaded = false;
            }
        }
    }
}

impl<B: Backend> Drop for ResidentCp<B> {
    fn drop(&mut self) { self.close(); }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;
    use std::rc::Rc;

    #[derive(Default)]
    struct Counts { opens: usize, resets: usize, executions: Vec<u8>, closes: usize }
    struct Fake { counts: Rc<RefCell<Counts>>, failure_at: Option<u8>, fail_open: bool }
    impl Backend for Fake {
        type Error = &'static str;
        fn open(&mut self) -> Result<(), Self::Error> {
            self.counts.borrow_mut().opens += 1;
            if self.fail_open { Err("open failed") } else { Ok(()) }
        }
        fn clear_kv(&mut self) -> Result<(), Self::Error> {
            self.counts.borrow_mut().resets += 1; Ok(())
        }
        fn execute(&mut self, position: u8) -> Result<(), Self::Error> {
            if self.failure_at == Some(position) { return Err("execute failed"); }
            self.counts.borrow_mut().executions.push(position); Ok(())
        }
        fn close(&mut self) { self.counts.borrow_mut().closes += 1; }
    }
    fn make(failure_at: Option<u8>, fail_open: bool) -> (ResidentCp<Fake>, Rc<RefCell<Counts>>) {
        let counts = Rc::new(RefCell::new(Counts::default()));
        (ResidentCp::new(Fake { counts: counts.clone(), failure_at, fail_open }), counts)
    }

    #[test]
    fn three_frames_single_open() {
        let (mut cp, counts) = make(None, false);
        for _ in 0..3 {
            cp.start_frame().unwrap();
            for position in 0..16 { cp.step(position).unwrap(); }
        }
        cp.close(); cp.close();
        let c = counts.borrow();
        assert_eq!((c.opens, c.resets, c.executions.len(), c.closes), (1, 3, 48, 1));
    }
    #[test]
    fn wrong_position_cannot_advance() {
        let (mut cp, _) = make(None, false);
        cp.start_frame().unwrap();
        assert_eq!(cp.step(2), Err(CpError::WrongPosition { expected: 0, actual: 2 }));
        cp.step(0).unwrap();
    }
    #[test]
    fn execution_failure_poisoned() {
        let (mut cp, counts) = make(Some(4), false);
        cp.start_frame().unwrap();
        for p in 0..4 { cp.step(p).unwrap(); }
        assert_eq!(cp.step(4), Err(CpError::Backend("execute failed")));
        assert_eq!(cp.start_frame(), Err(CpError::InvalidState));
        cp.close();
        assert_eq!(counts.borrow().closes, 1);
    }
    #[test]
    fn load_failure_not_retried() {
        let (mut cp, counts) = make(None, true);
        assert_eq!(cp.start_frame(), Err(CpError::Backend("open failed")));
        assert_eq!(cp.start_frame(), Err(CpError::InvalidState));
        assert_eq!(counts.borrow().opens, 1);
        assert_eq!(counts.borrow().closes, 0);
    }
    #[test]
    fn canceled_frame_requires_reset() {
        let (mut cp, counts) = make(None, false);
        cp.start_frame().unwrap(); cp.step(0).unwrap();
        cp.cancel_frame().unwrap();
        cp.start_frame().unwrap();
        assert_eq!(counts.borrow().resets, 2);
        assert_eq!(counts.borrow().opens, 1);
    }
}
