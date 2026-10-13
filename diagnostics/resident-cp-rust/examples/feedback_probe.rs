#[cfg(feature = "qnn-sdk")]
fn main() -> Result<(), Box<dyn std::error::Error>> {
    use std::{fs,path::Path,time::Instant};
    use lami_resident_cp::{ResidentCp,qnn::QnnBackend,sampling::{JavaRandom,select}};
    fn floats(path: &Path,count: usize) -> Result<Vec<f32>,Box<dyn std::error::Error>> {
        let bytes = fs::read(path)?;
        if bytes.len() != count*4 { return Err("Wrong tensor byte length".into()); }
        let values: Vec<f32> = bytes.as_chunks::<4>().0.iter().map(|b| f32::from_le_bytes(*b)).collect();
        if !values.iter().all(|v| v.is_finite()) { return Err("Nonfinite input asset".into()); }
        Ok(values)
    }
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 9 { return Err("Usage: feedback_probe CONTEXT BACKEND SYSTEM ASSETS OUTPUT MAIN_CODE RNG_STATE RPC_LIB".into()); }
    let assets = Path::new(&args[4]); let output = Path::new(&args[5]);
    if output.exists() { return Err("Refusing output overwrite".into()); }
    let main_code: usize = args[6].parse()?;
    if main_code >= 2048 { return Err("Not a speech main code".into()); }
    let mut random = JavaRandom::from_internal_state(args[7].parse()?)?;
    let started = Instant::now();
    let h0 = floats(&assets.join("initial-hidden.f32"),1024)?;
    let initial = floats(&assets.join("initial-embedding.f32"),1024)?;
    let mut heads = Vec::new(); let mut embeddings = Vec::new();
    for group in 0..15 {
        heads.push(floats(&assets.join(format!("cp.head.{group}.f32")),2048*1024)?);
        embeddings.push(floats(&assets.join(format!("cp.embedding.{group}.f32")),2048*1024)?);
    }
    let mut backend = QnnBackend::from_verified_bytes(fs::read(&args[1])?,&args[2],&args[3]).map_err(|e| format!("driver: {e:?}"))?;
    backend.use_registered_rpc_buffers(&args[8]).map_err(|e| format!("buffers: {e:?}"))?;
    println!("asset_read_ns={}",started.elapsed().as_nanos());
    let mut cp = ResidentCp::new(backend);cp.start_frame().map_err(|e| format!("start: {e:?}"))?;
    let mut hidden_trace: Vec<(u8,Vec<f32>)> = Vec::with_capacity(16);
    let mut input = h0;let mut codes = vec![main_code];let mut total = initial.clone();
    let mut sampler_ns = 0;
    let mut draws = Vec::with_capacity(15);
    for position in 0..16_u8 {
        let hidden = cp.step_with_embedding(position,&input).map_err(|e| format!("step {position}: {e:?}"))?;
        hidden_trace.push((position,hidden.to_vec()));
        if position == 0 { input = initial.clone(); continue; }
        let group = usize::from(position)-1;
        let before = Instant::now();
        let draw = random.draw();let code = select(&heads[group],hidden,draw)?;
        sampler_ns += before.elapsed().as_nanos();
        codes.push(code);
        let next = &embeddings[group][code*1024..(code+1)*1024];
        for (value,&addition) in total.iter_mut().zip(next) { *value += addition; }
        input.copy_from_slice(next);
        draws.push((position,code,draw));
    }
    for (position,code,draw) in draws { println!("position={position} code={code} draw={draw:.17}"); }
    fs::create_dir_all(output)?;
    for (position,hidden) in hidden_trace {
        let bytes: Vec<u8> = hidden.iter().flat_map(|x| x.to_le_bytes()).collect();
        fs::write(output.join(format!("hidden-{position:02}.f32")),bytes)?;
    }
    let tensor = cp.backend_ref().diagnostic_tensors();
    for layer in 0..5 {
        for (kind,values) in [("k",&tensor.keys[layer]),("v",&tensor.values[layer])] {
            fs::write(output.join(format!("final-kv-{kind}-{layer}.f32")),values.iter().flat_map(|x| x.to_le_bytes()).collect::<Vec<_>>())?;
        }
    }
    fs::write(output.join("code-row.txt"),codes.iter().map(|c| c.to_string()).collect::<Vec<_>>().join(","))?;
    fs::write(output.join("summed-embedding.f32"),total.iter().flat_map(|x| x.to_le_bytes()).collect::<Vec<_>>())?;
    let t = cp.backend_ref().timings(); let memory = cp.backend_ref().sdk_memory_stats();
    println!("context_opens={} graph_executions={} graph_execute_ns={} sampler_ns={} registered_buffers={} sdk_graph_ns={} input_copy_ns={} output_copy_ns={} frames=1 eos_checked=false audio_generated=false diagnostic_only=true",
        t.opens,t.executions,t.graph_execute_ns,sampler_ns,memory[0],memory[1],memory[2],memory[3]);
    cp.close();Ok(())
}

#[cfg(not(feature = "qnn-sdk"))]
fn main() { eprintln!("Enable qnn-sdk for continuous CP diagnostics"); }
