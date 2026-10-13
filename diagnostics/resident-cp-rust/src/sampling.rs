//! Same sequential FP32 projection, top-k=50, temperature=.9 and Java draws as the pilot.
pub struct JavaRandom { pub state: u64 }
impl JavaRandom {
    pub fn from_internal_state(state: u64) -> Result<Self, &'static str> {
        if state >= (1 << 48) { return Err("Invalid Java RNG state"); }
        Ok(Self { state })
    }
    fn bits(&mut self, count: u32) -> u64 {
        self.state = self.state.wrapping_mul(0x5deece66d).wrapping_add(0xb) & ((1 << 48)-1);
        self.state >> (48-count)
    }
    pub fn draw(&mut self) -> f64 {
        ((self.bits(26) << 27)+self.bits(27)) as f64 / (1_u64 << 53) as f64
    }
}

pub fn select(weights: &[f32], hidden: &[f32], draw: f64) -> Result<usize, &'static str> {
    if weights.len() != 2048*1024 || hidden.len() != 1024 || !(0.0..1.0).contains(&draw) {
        return Err("Invalid sampler dimensions or draw");
    }
    let mut scores = Vec::with_capacity(2048);
    for (token,row) in weights.as_chunks::<1024>().0.iter().enumerate() {
        let mut score = 0.0_f32;
        for (&weight,&value) in row.iter().zip(hidden) { score += weight*value; }
        if !score.is_finite() { return Err("Nonfinite projected score"); }
        scores.push((token,f64::from(score)/0.9));
    }
    scores.sort_by(|a,b| b.1.total_cmp(&a.1).then(a.0.cmp(&b.0)));
    scores.truncate(50);
    let probabilities: Vec<f64> = scores.iter().map(|(_,score)| (score-scores[0].1).exp()).collect();
    let target = draw*probabilities.iter().sum::<f64>();
    let mut total = 0.0;
    for ((token,_),probability) in scores.iter().zip(probabilities) {
        total += probability;
        if target < total { return Ok(*token); }
    }
    Ok(scores.last().unwrap().0)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn java_draws_and_restored_state_match() {
        let mut random = JavaRandom::from_internal_state(42 ^ 0x5deece66d).unwrap();
        assert_eq!(random.draw(),0.7275636800328681);
        let mut restored = JavaRandom::from_internal_state(random.state).unwrap();
        assert_eq!(random.draw(),0.6832234717598454);
        assert_eq!(restored.draw(),0.6832234717598454);
    }
    #[test] fn rejects_invalid_draw() {
        assert!(select(&vec![0.0;2048*1024],&[0.0;1024],f64::NAN).is_err());
    }
}
