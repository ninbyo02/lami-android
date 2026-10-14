//! Resident SDK-backed driver. RAW client buffers are used, not registered DMA memory.
//! The caller must verify context bytes against the pinned artifact before construction.
use std::{ffi::{c_char, c_void, CString}, ptr::NonNull, time::Instant};
use crate::{Backend, EmbeddedBackend};
use crate::tensors::{CpTensors, TensorError, EMBEDDING_ELEMENTS, KV_LAYERS, KV_ELEMENTS_PER_LAYER};

#[repr(C)]
struct Buffer { name: *const c_char, data: *mut c_void, bytes: u32 }

extern "C" {
    fn lami_qnn_open(backend: *const c_char, system: *const c_char, rpc: *const c_char, binary: *mut c_void, bytes: usize,
        inputs: *const Buffer, outputs: *const Buffer, result: *mut *mut c_void) -> u64;
    fn lami_qnn_execute(session: *mut c_void) -> u64;
    fn lami_qnn_close(session: *mut c_void);
    fn lami_qnn_memory_stats(session: *mut c_void, stats: *mut u64);
    fn lami_qnn_validate_metadata(system: *const c_char, binary: *mut c_void, bytes: usize,
        inputs: *const Buffer, outputs: *const Buffer) -> u64;
}

#[derive(Debug, PartialEq, Eq)]
pub enum QnnError {
    InvalidContext, InvalidPath, InvalidEmbedding, InvalidState, InputNotStaged,
    Tensor(TensorError), Sdk(u64), CacheChangedOutsidePosition,
}

#[derive(Default, Debug, Clone)]
pub struct DriverTimings {
    pub opens: usize,
    pub executions: usize,
    pub context_open_ns: u128,
    pub graph_execute_ns: u128,
    pub verification_and_cache_copy_ns: u128,
}

pub struct QnnBackend {
    // Boxing also keeps inline mask/input_ids addresses stable when ResidentCp moves.
    tensors: Box<CpTensors>,
    context_bytes: Vec<u8>,
    backend_path: CString,
    system_path: CString,
    rpc_path: Option<CString>,
    session: Option<NonNull<c_void>>,
    staged: bool,
    timings: DriverTimings,
}

impl QnnBackend {
    pub fn from_verified_bytes(bytes: Vec<u8>, backend: &str, system: &str) -> Result<Self, QnnError> {
        if bytes.is_empty() || bytes.len() > 512 * 1024 * 1024 { return Err(QnnError::InvalidContext); }
        Ok(Self { tensors: Box::new(CpTensors::zeroed()), context_bytes: bytes,
            backend_path: CString::new(backend).map_err(|_| QnnError::InvalidPath)?,
            system_path: CString::new(system).map_err(|_| QnnError::InvalidPath)?,
            rpc_path: None, session: None, staged: false, timings: DriverTimings::default() })
    }

    pub fn timings(&self) -> &DriverTimings { &self.timings }

    pub fn use_registered_rpc_buffers(&mut self, library: &str) -> Result<(), QnnError> {
        if self.session.is_some() { return Err(QnnError::InvalidState); }
        self.rpc_path = Some(CString::new(library).map_err(|_| QnnError::InvalidPath)?);
        Ok(())
    }

    pub fn sdk_memory_stats(&self) -> [u64;4] {
        let mut result = [0;4];
        if let Some(handle) = self.session {
            // The SDK keeps all registered allocations alive for this session.
            unsafe { lami_qnn_memory_stats(handle.as_ptr(),result.as_mut_ptr()) };
        }
        result
    }

    pub fn diagnostic_tensors(&self) -> &CpTensors { &self.tensors }

    pub fn execute_fixed_diagnostic(&mut self, position: u8, input: &CpTensors) -> Result<bool, QnnError> {
        input.check().map_err(QnnError::Tensor)?;
        if input.input_ids[0] != i32::from(position) { return Err(QnnError::InvalidState); }
        for layer in 0..KV_LAYERS {
            self.tensors.keys[layer].copy_from_slice(&input.keys[layer]);
            self.tensors.values[layer].copy_from_slice(&input.values[layer]);
        }
        self.stage_embedding(&input.embeddings)?;
        self.execute_graph(position, false)
    }

    fn execute_graph(&mut self, position: u8, strict_cache: bool) -> Result<bool, QnnError> {
        let handle = self.session.ok_or(QnnError::InvalidState)?;
        if !self.staged { return Err(QnnError::InputNotStaged); }
        self.tensors.set_position_mask(position.into()).map_err(QnnError::Tensor)?;
        self.tensors.check().map_err(QnnError::Tensor)?;
        self.staged = false;
        let started = Instant::now();
        // Synchronous completion precedes all output access. This is the only per-step SDK call.
        let status = unsafe { lami_qnn_execute(handle.as_ptr()) };
        self.timings.graph_execute_ns += started.elapsed().as_nanos();
        if status != 0 { return Err(QnnError::Sdk(status)); }
        self.timings.executions += 1;
        let verify = Instant::now();
        let unchanged = self.validate_cache_write(position).is_ok();
        if strict_cache && !unchanged { return Err(QnnError::CacheChangedOutsidePosition); }
        // Fixed diagnostics may expose FP16 rounding of a CPU-origin cache. They never route audio.
        self.tensors.accept_output_cache().map_err(QnnError::Tensor)?;
        self.timings.verification_and_cache_copy_ns += verify.elapsed().as_nanos();
        Ok(unchanged)
    }

    pub fn validate_metadata_without_backend(&mut self) -> Result<(), QnnError> {
        if self.session.is_some() { return Err(QnnError::InvalidState); }
        self.tensors.check().map_err(QnnError::Tensor)?;
        let (input_names, output_names, inputs, outputs) = self.buffers();
        // The System library only parses metadata; no backend, device or graph is created.
        let status = unsafe { lami_qnn_validate_metadata(self.system_path.as_ptr(),
            self.context_bytes.as_mut_ptr().cast(), self.context_bytes.len(), inputs.as_ptr(), outputs.as_ptr()) };
        drop((input_names, output_names));
        if status != 0 { return Err(QnnError::Sdk(status)); }
        Ok(())
    }

    fn buffers(&mut self) -> (Vec<CString>, Vec<CString>, Vec<Buffer>, Vec<Buffer>) {
        let mut input_names = vec![CString::new("serving_default_embeddings").unwrap(),
            CString::new("serving_default_input_ids").unwrap(), CString::new("serving_default_mask").unwrap()];
        let mut output_names = vec![CString::new("serving_default_hidden_output").unwrap()];
        for kind in ["k", "v"] {
            for layer in 0..KV_LAYERS {
                input_names.push(CString::new(format!("serving_default_kv_cache_{kind}_{layer}")).unwrap());
                output_names.push(CString::new(format!("serving_default_kv_cache_{kind}_{layer}_output")).unwrap());
            }
        }
        let mut inputs = vec![
            Buffer { name: input_names[0].as_ptr(), data: self.tensors.embeddings.as_mut_ptr().cast(), bytes: 4096 },
            Buffer { name: input_names[1].as_ptr(), data: self.tensors.input_ids.as_mut_ptr().cast(), bytes: 4 },
            Buffer { name: input_names[2].as_ptr(), data: self.tensors.mask.as_mut_ptr().cast(), bytes: 128 }];
        let mut outputs = vec![Buffer { name: output_names[0].as_ptr(), data: self.tensors.hidden.as_mut_ptr().cast(), bytes: 4096 }];
        for kind in 0..2 {
            for layer in 0..KV_LAYERS {
                let input = if kind == 0 { &mut self.tensors.keys[layer] } else { &mut self.tensors.values[layer] };
                inputs.push(Buffer { name: input_names[3+kind*KV_LAYERS+layer].as_ptr(),
                    data: input.as_mut_ptr().cast(), bytes: (KV_ELEMENTS_PER_LAYER * 4) as u32 });
                let output = if kind == 0 { &mut self.tensors.output_keys[layer] } else { &mut self.tensors.output_values[layer] };
                outputs.push(Buffer { name: output_names[1+kind*KV_LAYERS+layer].as_ptr(),
                    data: output.as_mut_ptr().cast(), bytes: (KV_ELEMENTS_PER_LAYER * 4) as u32 });
            }
        }
        (input_names, output_names, inputs, outputs)
    }

    fn validate_cache_write(&self, position: u8) -> Result<(), QnnError> {
        let start = usize::from(position) * 8 * 128;
        let end = start + 8 * 128;
        for layer in 0..KV_LAYERS {
            for (before, after) in [(&self.tensors.keys[layer], &self.tensors.output_keys[layer]),
                                    (&self.tensors.values[layer], &self.tensors.output_values[layer])] {
                if before.iter().zip(after).enumerate().any(|(i,(a,b))|
                    (i < start || i >= end) && a.to_bits() != b.to_bits()) {
                    return Err(QnnError::CacheChangedOutsidePosition);
                }
            }
        }
        Ok(())
    }
}

impl Backend for QnnBackend {
    type Error = QnnError;
    fn open(&mut self) -> Result<(), Self::Error> {
        if self.session.is_some() { return Err(QnnError::InvalidState); }
        self.tensors.check().map_err(QnnError::Tensor)?;
        let (input_names, output_names, inputs, outputs) = self.buffers();
        let mut handle = std::ptr::null_mut();
        let started = Instant::now();
        self.timings.opens += 1;
        // Names/descriptors live through open. C retains only pointers into boxed tensor storage.
        let rpc = self.rpc_path.as_ref().map_or(std::ptr::null(),|path| path.as_ptr());
        let status = unsafe { lami_qnn_open(self.backend_path.as_ptr(), self.system_path.as_ptr(), rpc,
            self.context_bytes.as_mut_ptr().cast(), self.context_bytes.len(), inputs.as_ptr(), outputs.as_ptr(), &mut handle) };
        self.timings.context_open_ns += started.elapsed().as_nanos();
        drop((input_names, output_names));
        if status != 0 { return Err(QnnError::Sdk(status)); }
        self.session = Some(NonNull::new(handle).ok_or(QnnError::InvalidState)?);
        Ok(())
    }
    fn clear_kv(&mut self) -> Result<(), Self::Error> {
        if self.session.is_none() { return Err(QnnError::InvalidState); }
        self.tensors.clear_kv(); self.staged = false; Ok(())
    }
    fn execute(&mut self, position: u8) -> Result<(), Self::Error> {
        self.execute_graph(position, true).map(|_| ())
    }
    fn close(&mut self) {
        if let Some(handle) = self.session.take() {
            // All tensor/context allocations remain alive until the SDK context is released.
            unsafe { lami_qnn_close(handle.as_ptr()) };
        }
        self.staged = false;
    }
}

impl EmbeddedBackend for QnnBackend {
    fn stage_embedding(&mut self, embedding: &[f32]) -> Result<(), Self::Error> {
        if embedding.len() != EMBEDDING_ELEMENTS || !embedding.iter().all(|x| x.is_finite()) {
            return Err(QnnError::InvalidEmbedding);
        }
        self.tensors.embeddings.copy_from_slice(embedding); self.staged = true; Ok(())
    }
    fn hidden(&self) -> &[f32] { &self.tensors.hidden }
}

impl Drop for QnnBackend {
    fn drop(&mut self) { self.close(); }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn missing_rpc_fails_before_sdk_context_or_execution() {
        let mut backend = QnnBackend::from_verified_bytes(vec![0;8], "/missing/libQnnHtp.so", "/missing/libQnnSystem.so").unwrap();
        backend.use_registered_rpc_buffers("/missing/libcdsprpc.so").unwrap();
        assert!(matches!(backend.open(), Err(QnnError::Sdk(_))));
        assert!(backend.session.is_none());
        assert_eq!(backend.timings().executions, 0);
        backend.close(); backend.close();
    }
    #[test]
    fn missing_sdk_library_fails_closed_without_inference() {
        let mut backend = QnnBackend::from_verified_bytes(vec![0;8], "/missing/libQnnHtp.so", "/missing/libQnnSystem.so").unwrap();
        assert!(matches!(backend.open(), Err(QnnError::Sdk(_))));
        assert!(backend.session.is_none());
        assert_eq!(backend.timings().executions,0);
        backend.close(); backend.close();
    }
    #[test]
    fn invalid_embedding_cannot_modify_allocations() {
        let mut backend = QnnBackend::from_verified_bytes(vec![0;8], "/missing/a", "/missing/b").unwrap();
        let ptr = backend.tensors.embeddings.as_ptr();
        assert_eq!(backend.stage_embedding(&[0.0;2]),Err(QnnError::InvalidEmbedding));
        let mut input = vec![0.0;1024]; input[0] = f32::NAN;
        assert_eq!(backend.stage_embedding(&input),Err(QnnError::InvalidEmbedding));
        assert_eq!(backend.tensors.embeddings.as_ptr(),ptr);
        assert!(!backend.staged);
    }
    #[test]
    fn moving_driver_keeps_inline_tensor_addresses_stable() {
        let mut backend = QnnBackend::from_verified_bytes(vec![0;8], "/missing/a", "/missing/b").unwrap();
        let mask = backend.tensors.mask.as_ptr();
        let ids = backend.tensors.input_ids.as_ptr();
        let (_,_,inputs,outputs) = backend.buffers();
        assert_eq!((inputs.len(),outputs.len()),(13,11));
        let moved = Box::new(backend);
        assert_eq!(mask,moved.tensors.mask.as_ptr());
        assert_eq!(ids,moved.tensors.input_ids.as_ptr());
    }
    #[test]
    fn fixed_diagnostic_rejects_different_position_without_sdk_execution() {
        let mut backend = QnnBackend::from_verified_bytes(vec![0;8], "/missing/a", "/missing/b").unwrap();
        let input = CpTensors::zeroed();
        assert_eq!(backend.execute_fixed_diagnostic(5,&input),Err(QnnError::InvalidState));
        assert_eq!(backend.timings.executions,0);
    }
}
