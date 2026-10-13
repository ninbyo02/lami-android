//! Exact host-side shape guard for the previously inspected QNN AOT CP graph.
//! No claim of native tensor binding or inference is made.
pub const EMBEDDING_ELEMENTS: usize = 1024;
pub const MASK_ELEMENTS: usize = 32;
pub const KV_LAYERS: usize = 5;
pub const KV_ELEMENTS_PER_LAYER: usize = 32 * 8 * 128;
pub const HIDDEN_ELEMENTS: usize = 1024;
pub const MASK_NEGATIVE: f32 = -10000.0;
pub const INPUT_TENSORS: usize = 13;
pub const OUTPUT_TENSORS: usize = 11;

#[derive(Debug, PartialEq, Eq)]
pub enum TensorError {
    IncorrectLength { tensor: &'static str, expected: usize, actual: usize },
    PositionOutOfBounds,
    InvalidMask,
    NonFinite { tensor: &'static str },
}

#[derive(Clone)]
pub struct CpTensors {
    pub embeddings: Vec<f32>,
    pub input_ids: [i32; 1],
    pub mask: [f32; MASK_ELEMENTS],
    pub keys: [Vec<f32>; KV_LAYERS],
    pub values: [Vec<f32>; KV_LAYERS],
    pub hidden: Vec<f32>,
    pub output_keys: [Vec<f32>; KV_LAYERS],
    pub output_values: [Vec<f32>; KV_LAYERS],
}

impl CpTensors {
    pub fn zeroed() -> Self {
        Self {
            embeddings: vec![0.0; EMBEDDING_ELEMENTS],
            input_ids: [0],
            mask: std::array::from_fn(|i| if i == 0 { 0.0 } else { MASK_NEGATIVE }),
            keys: std::array::from_fn(|_| vec![0.0; KV_ELEMENTS_PER_LAYER]),
            values: std::array::from_fn(|_| vec![0.0; KV_ELEMENTS_PER_LAYER]),
            hidden: vec![0.0; HIDDEN_ELEMENTS],
            output_keys: std::array::from_fn(|_| vec![0.0; KV_ELEMENTS_PER_LAYER]),
            output_values: std::array::from_fn(|_| vec![0.0; KV_ELEMENTS_PER_LAYER]),
        }
    }
    pub fn check(&self) -> Result<(), TensorError> {
        fn length(tensor: &'static str, actual: usize, expected: usize) -> Result<(), TensorError> {
            if actual != expected { Err(TensorError::IncorrectLength { tensor, expected, actual }) }
            else { Ok(()) }
        }
        fn finite(tensor: &'static str, values: &[f32]) -> Result<(), TensorError> {
            if values.iter().all(|value| value.is_finite()) { Ok(()) }
            else { Err(TensorError::NonFinite { tensor }) }
        }
        length("embeddings", self.embeddings.len(), EMBEDDING_ELEMENTS)?;
        finite("embeddings", &self.embeddings)?;
        if !(0..16).contains(&self.input_ids[0]) { return Err(TensorError::PositionOutOfBounds); }
        let position = self.input_ids[0] as usize;
        if self.mask.iter().enumerate().any(|(i, value)|
            value.to_bits() != (if i <= position { 0.0_f32 } else { MASK_NEGATIVE }).to_bits()) {
            return Err(TensorError::InvalidMask);
        }
        for layer in 0..KV_LAYERS {
            length("kv_key", self.keys[layer].len(), KV_ELEMENTS_PER_LAYER)?;
            length("kv_value", self.values[layer].len(), KV_ELEMENTS_PER_LAYER)?;
            length("output_kv_key", self.output_keys[layer].len(), KV_ELEMENTS_PER_LAYER)?;
            length("output_kv_value", self.output_values[layer].len(), KV_ELEMENTS_PER_LAYER)?;
            finite("kv_key", &self.keys[layer])?;
            finite("kv_value", &self.values[layer])?;
            finite("output_kv_key", &self.output_keys[layer])?;
            finite("output_kv_value", &self.output_values[layer])?;
        }
        length("hidden", self.hidden.len(), HIDDEN_ELEMENTS)?;
        finite("hidden", &self.hidden)
    }
    pub fn clear_kv(&mut self) {
        for layer in 0..KV_LAYERS {
            self.keys[layer].fill(0.0);
            self.values[layer].fill(0.0);
            self.output_keys[layer].fill(0.0);
            self.output_values[layer].fill(0.0);
        }
        self.hidden.fill(0.0);
    }
    pub fn set_position_mask(&mut self, position: usize) -> Result<(), TensorError> {
        if position >= 16 { return Err(TensorError::PositionOutOfBounds); }
        self.input_ids[0] = position as i32;
        self.mask.fill(MASK_NEGATIVE);
        for i in 0..=position { self.mask[i] = 0.0; }
        Ok(())
    }
    pub fn accept_output_cache(&mut self) -> Result<(), TensorError> {
        self.check()?;
        for layer in 0..KV_LAYERS {
            self.keys[layer].copy_from_slice(&self.output_keys[layer]);
            self.values[layer].copy_from_slice(&self.output_values[layer]);
        }
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
        assert_eq!(tensors.mask[16], MASK_NEGATIVE);
        assert_eq!(tensors.input_ids, [15]);
        assert!(tensors.set_position_mask(16).is_err());
    }
    #[test] fn actual_graph_is_head_free() {
        let tensors = CpTensors::zeroed();
        assert_eq!(tensors.hidden.len(), 1024);
        assert_ne!(tensors.keys[0].as_ptr(), tensors.output_keys[0].as_ptr());
    }
    #[test] fn rejects_wrong_mask_and_nonfinite_state() {
        let mut tensors = CpTensors::zeroed();
        tensors.mask[31] = -1.0e9;
        assert_eq!(tensors.check(), Err(TensorError::InvalidMask));
        tensors.set_position_mask(0).unwrap();
        tensors.hidden[0] = f32::NAN;
        assert_eq!(tensors.check(), Err(TensorError::NonFinite { tensor: "hidden" }));
    }
    #[test] fn output_cache_is_carried_without_reallocation() {
        let mut tensors = CpTensors::zeroed();
        let before = tensors.keys[0].as_ptr();
        tensors.output_keys[0][123] = 2.0;
        tensors.output_values[4][456] = 3.0;
        tensors.accept_output_cache().unwrap();
        assert_eq!(tensors.keys[0].as_ptr(), before);
        assert_eq!(tensors.keys[0][123], 2.0);
        assert_eq!(tensors.values[4][456], 3.0);
    }
}
