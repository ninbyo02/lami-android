//! Exact host-side shape guard for the previously inspected QNN AOT CP graph.
//! No claim of native tensor binding or inference is made.
pub const EMBEDDING_ELEMENTS: usize = 1024;
pub const MASK_ELEMENTS: usize = 32;
pub const KV_LAYERS: usize = 5;
pub const KV_ELEMENTS_PER_LAYER: usize = 32 * 8 * 128;
pub const LOGITS_ELEMENTS: usize = 15 * 2048;
pub const INPUT_TENSORS: usize = 13;
pub const OUTPUT_TENSORS: usize = 11;

#[derive(Debug, PartialEq, Eq)]
pub enum TensorError {
    IncorrectLength { tensor: &'static str, expected: usize, actual: usize },
    PositionOutOfBounds,
}

#[derive(Clone)]
pub struct CpTensors {
    pub embeddings: Vec<f32>,
    pub input_ids: [i32; 1],
    pub mask: [f32; MASK_ELEMENTS],
    pub keys: [Vec<f32>; KV_LAYERS],
    pub values: [Vec<f32>; KV_LAYERS],
    pub logits: Vec<f32>,
}

impl CpTensors {
    pub fn zeroed() -> Self {
        Self {
            embeddings: vec![0.0; EMBEDDING_ELEMENTS],
            input_ids: [0],
            mask: [0.0; MASK_ELEMENTS],
            keys: std::array::from_fn(|_| vec![0.0; KV_ELEMENTS_PER_LAYER]),
            values: std::array::from_fn(|_| vec![0.0; KV_ELEMENTS_PER_LAYER]),
            logits: vec![0.0; LOGITS_ELEMENTS],
        }
    }
    pub fn check(&self) -> Result<(), TensorError> {
        fn length(tensor: &'static str, actual: usize, expected: usize) -> Result<(), TensorError> {
            if actual != expected { Err(TensorError::IncorrectLength { tensor, expected, actual }) }
            else { Ok(()) }
        }
        length("embeddings", self.embeddings.len(), EMBEDDING_ELEMENTS)?;
        for layer in 0..KV_LAYERS {
            length("kv_key", self.keys[layer].len(), KV_ELEMENTS_PER_LAYER)?;
            length("kv_value", self.values[layer].len(), KV_ELEMENTS_PER_LAYER)?;
        }
        length("logits", self.logits.len(), LOGITS_ELEMENTS)
    }
    pub fn clear_kv(&mut self) {
        for layer in 0..KV_LAYERS {
            self.keys[layer].fill(0.0);
            self.values[layer].fill(0.0);
        }
    }
    pub fn set_position_mask(&mut self, position: usize) -> Result<(), TensorError> {
        if position >= 16 { return Err(TensorError::PositionOutOfBounds); }
        self.mask.fill(-1.0e9);
        for i in 0..=position { self.mask[i] = 0.0; }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn conforms_to_aot_shapes() {
        let tensors = CpTensors::zeroed();
        assert_eq!((INPUT_TENSORS, OUTPUT_TENSORS), (13, 11));
        assert!(tensors.check().is_ok());
    }
    #[test] fn rejects_wrong_kv_length() {
        let mut tensors = CpTensors::zeroed();
        tensors.keys[3].pop();
        assert_eq!(tensors.check(), Err(TensorError::IncorrectLength {
            tensor: "kv_key", expected: KV_ELEMENTS_PER_LAYER, actual: KV_ELEMENTS_PER_LAYER - 1
        }));
    }
    #[test] fn reset_does_not_destroy_allocations() {
        let mut tensors = CpTensors::zeroed();
        tensors.keys[0][0] = 8.0;
        tensors.values[4][12] = 3.0;
        let ptr = tensors.keys[0].as_ptr();
        tensors.clear_kv();
        assert_eq!(tensors.keys[0].as_ptr(), ptr);
        assert_eq!(tensors.keys[0][0], 0.0);
        assert_eq!(tensors.values[4][12], 0.0);
    }
    #[test] fn mask_boundary_is_checked() {
        let mut tensors = CpTensors::zeroed();
        tensors.set_position_mask(15).unwrap();
        assert_eq!(tensors.mask[15], 0.0);
        assert!(tensors.set_position_mask(16).is_err());
    }
}
