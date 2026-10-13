#[cfg(feature = "qnn-sdk")]
fn main() -> Result<(), Box<dyn std::error::Error>> {
    use lami_resident_cp::{qnn::QnnBackend, sampling::{JavaRandom,select}, ResidentCp};
    use std::{fs, io::{Read,Write}, path::Path, time::Instant};
    fn floats(path: &Path,count: usize) -> Result<Vec<f32>,Box<dyn std::error::Error>> {
        let bytes = fs::read(path)?;
        if bytes.len() != count*4 { return Err("Wrong tensor byte length".into()); }
        let values: Vec<f32> = bytes.as_chunks::<4>().0.iter().map(|b| f32::from_le_bytes(*b)).collect();
        if !values.iter().all(|v| v.is_finite()) { return Err("Nonfinite asset".into()); }
        Ok(values)
    }
    fn save(path: &Path,values: &[f32]) -> std::io::Result<()> {
        fs::write(path,values.iter().flat_map(|x| x.to_le_bytes()).collect::<Vec<_>>())
    }
    fn vector(input: &mut impl Read) -> Result<Vec<f32>,Box<dyn std::error::Error>> {
        let mut bytes = [0_u8;4096]; input.read_exact(&mut bytes)?;
        let values: Vec<f32> = bytes.as_chunks::<4>().0.iter().map(|b| f32::from_le_bytes(*b)).collect();
        if !values.iter().all(|v| v.is_finite()) { return Err("Nonfinite frame input".into()); }
        Ok(values)
    }
    let args: Vec<String> = std::env::args().collect();
    if !(7..=9).contains(&args.len()) { return Err("Usage: stream_probe CONTEXT BACKEND SYSTEM ASSETS OUTPUT RPC_LIB [MAX_FRAMES] [UTTERANCES]".into()); }
    let max_frames: usize = args.get(7).map(|value| value.parse()).transpose()?.unwrap_or(32);
    if !(2..=32).contains(&max_frames) { return Err("Invalid frame budget".into()); }
    let utterances: usize = args.get(8).map(|value| value.parse()).transpose()?.unwrap_or(1);
    if !(1..=2).contains(&utterances) { return Err("Invalid utterance budget".into()); }
    let assets = Path::new(&args[4]);let output = Path::new(&args[5]);
    if output.exists() { return Err("Refusing output overwrite".into()); }
    let assets_started = Instant::now();
    let mut heads = Vec::new();let mut embeddings = Vec::new();
    for group in 0..15 {
        heads.push(floats(&assets.join(format!("cp.head.{group}.f32")),2048*1024)?);
        embeddings.push(floats(&assets.join(format!("cp.embedding.{group}.f32")),2048*1024)?);
    }
    let asset_load_ns = assets_started.elapsed().as_nanos();
    let mut backend = QnnBackend::from_verified_bytes(fs::read(&args[1])?,&args[2],&args[3]).map_err(|e| format!("driver: {e:?}"))?;
    backend.use_registered_rpc_buffers(&args[6]).map_err(|e| format!("buffers: {e:?}"))?;
    let mut cp = ResidentCp::new(backend);
    let mut input = std::io::stdin().lock();let mut replies = std::io::stdout().lock();
    fs::create_dir_all(output)?;
    for utterance in 0..utterances {
    replies.write_all(b"LCS1")?;replies.flush()?;
    let mut frames = 0;let mut sampler_ns = 0;let mut captures = Vec::new();
    let eos_code;
    loop {
        let mut magic = [0_u8;4];input.read_exact(&mut magic)?;
        if &magic == b"LCL1" {
            if frames != max_frames { return Err("Premature frame limit".into()); }
            eos_code = 0;break;
        }
        if &magic == b"LCE1" {
            let mut code = [0_u8;4];input.read_exact(&mut code)?;
            eos_code = u32::from_le_bytes(code);
            if eos_code != 2150 || frames < 2 { return Err("Invalid or premature EOS".into()); }
            break;
        }
        if &magic != b"LCF1" || frames >= max_frames { return Err("Invalid request or frame budget exceeded".into()); }
        let mut code = [0_u8;4];input.read_exact(&mut code)?;
        if u32::from_le_bytes(code) >= 2048 { return Err("Invalid Main speech code".into()); }
        let mut state = [0_u8;8];input.read_exact(&mut state)?;
        let mut random = JavaRandom::from_internal_state(u64::from_le_bytes(state))?;
        let mut embedding = vector(&mut input)?;let initial = vector(&mut input)?;
        let mut total = initial.clone();let mut codes = Vec::with_capacity(15);let mut hidden_trace = Vec::new();
        cp.start_frame().map_err(|e| format!("start frame {frames}: {e:?}"))?;
        for position in 0..16_u8 {
            let hidden = cp.step_with_embedding(position,&embedding).map_err(|e| format!("frame {frames} step {position}: {e:?}"))?;
            if frames < 2 { hidden_trace.push(hidden.to_vec()); }
            if position == 0 { embedding = initial.clone();continue; }
            let group = usize::from(position)-1;let before = Instant::now();
            let token = select(&heads[group],hidden,random.draw())?;
            sampler_ns += before.elapsed().as_nanos();codes.push(token as u32);
            let next = &embeddings[group][token*1024..(token+1)*1024];
            for (value,&addition) in total.iter_mut().zip(next) { *value += addition; }
            embedding.copy_from_slice(next);
        }
        if frames < 2 {
            let tensor = cp.backend_ref().diagnostic_tensors();
            captures.push((hidden_trace,tensor.keys.clone(),tensor.values.clone(),total.clone()));
        }
        replies.write_all(b"LCR1")?;replies.write_all(&random.state.to_le_bytes())?;
        for token in codes { replies.write_all(&token.to_le_bytes())?; }
        for value in total { replies.write_all(&value.to_le_bytes())?; }
        replies.flush()?;frames += 1;
    }
    let directory = if utterances == 1 { output.to_path_buf() } else { output.join(format!("utterance{utterance}")) };
    fs::create_dir_all(&directory)?;
    for (frame,(hidden,keys,values,total)) in captures.iter().enumerate() {
        let directory = directory.join(format!("frame{frame:03}"));fs::create_dir(&directory)?;
        for (position,h) in hidden.iter().enumerate() { save(&directory.join(format!("hidden-{position:02}.f32")),h)?; }
        for layer in 0..5 {
            save(&directory.join(format!("final-kv-k-{layer}.f32")),&keys[layer])?;
            save(&directory.join(format!("final-kv-v-{layer}.f32")),&values[layer])?;
        }
        save(&directory.join("summed-embedding.f32"),total)?;
    }
    let timing = cp.backend_ref().timings();let memory = cp.backend_ref().sdk_memory_stats();
    eprintln!("utterance={} context_opens={} context_open_ns={} head_loads=15 embedding_loads=15 asset_load_ns={} graph_executions={} graph_execute_ns={} sampler_ns={} registered_buffers={} sdk_graph_ns={} input_copy_ns={} output_copy_ns={} frames={} eos_code={} audio_generated=false diagnostic_only=true",
        utterance,timing.opens,timing.context_open_ns,asset_load_ns,timing.executions,timing.graph_execute_ns,sampler_ns,memory[0],memory[1],memory[2],memory[3],frames,eos_code);
    replies.write_all(b"LCD1")?;replies.flush()?;
    }
    cp.close();Ok(())
}

#[cfg(not(feature = "qnn-sdk"))]
fn main() { eprintln!("Enable qnn-sdk for streaming diagnostics"); }
