#[cfg(feature = "qnn-sdk")]
fn main() -> Result<(), Box<dyn std::error::Error>> {
    use std::{fs, path::Path, time::Instant};
    use lami_resident_cp::{Backend, qnn::QnnBackend, tensors::CpTensors};
    fn rss() -> u64 {
        std::fs::read_to_string("/proc/self/status").ok().and_then(|text|
            text.lines().find(|line| line.starts_with("VmRSS:")).and_then(|line|
                line.split_whitespace().nth(1).and_then(|value| value.parse().ok()))).unwrap_or(0)
    }
    fn floats(path: &Path, count: usize) -> Result<Vec<f32>, Box<dyn std::error::Error>> {
        let bytes = fs::read(path)?;
        if bytes.len() != count*4 { return Err("Incorrect tensor byte length".into()); }
        Ok(bytes.as_chunks::<4>().0.iter().map(|b| f32::from_le_bytes(*b)).collect())
    }
    fn write(path: &Path, values: &[f32]) -> Result<(), Box<dyn std::error::Error>> {
        let bytes: Vec<u8> = values.iter().flat_map(|x| x.to_le_bytes()).collect();
        fs::write(path,bytes)?; Ok(())
    }
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 7 { return Err("Usage: fixed_probe VERIFIED_CONTEXT BACKEND SYSTEM OUTPUT REPEATS CASE...".into()); }
    let repeats: usize = args[5].parse()?;
    if !(1..=100).contains(&repeats) { return Err("Repeat count outside bounded diagnostics".into()); }
    let output = Path::new(&args[4]);
    if output.exists() { return Err("Refusing to overwrite output".into()); }
    let mut cases = Vec::new();
    let setup = Instant::now();
    println!("rss_before_setup_kib={}",rss());
    for name in &args[6..] {
        let path = Path::new(name).join("inputs");
        let mut input = CpTensors::zeroed();
        input.embeddings = floats(&path.join("serving_default_embeddings.raw"),1024)?;
        let ids = fs::read(path.join("serving_default_input_ids.raw"))?;
        if ids.len() != 4 { return Err("Incorrect position byte length".into()); }
        let position = i32::from_le_bytes(ids.try_into().unwrap());
        if !(0..16).contains(&position) { return Err("Position outside CP schedule".into()); }
        input.set_position_mask(position as usize).map_err(|e| format!("mask: {e:?}"))?;
        if floats(&path.join("serving_default_mask.raw"),32)? != input.mask { return Err("Fixed input mask differs".into()); }
        for layer in 0..5 {
            input.keys[layer] = floats(&path.join(format!("serving_default_kv_cache_k_{layer}.raw")),32768)?;
            input.values[layer] = floats(&path.join(format!("serving_default_kv_cache_v_{layer}.raw")),32768)?;
        }
        input.check().map_err(|e| format!("input: {e:?}"))?;
        let label = Path::new(name).file_name().ok_or("No case name")?.to_str().ok_or("Non-UTF8 case name")?.to_owned();
        cases.push((label,input));
    }
    let bytes = fs::read(&args[1])?;
    let mut backend = QnnBackend::from_verified_bytes(bytes,&args[2],&args[3]).map_err(|e| format!("driver: {e:?}"))?;
    if let Ok(library) = std::env::var("LAMI_CP_RPC_LIBRARY") {
        backend.use_registered_rpc_buffers(&library).map_err(|e| format!("registration mode: {e:?}"))?;
    }
    println!("input_and_context_read_ns={}",setup.elapsed().as_nanos());
    println!("rss_after_input_context_kib={}",rss());
    backend.open().map_err(|e| format!("open: {e:?}"))?;
    println!("rss_after_open_kib={}",rss());
    fs::create_dir_all(output)?;
    for (label,input) in &cases {
        let mut outside_unchanged = true;
        for _ in 0..repeats {
            outside_unchanged &= backend.execute_fixed_diagnostic(input.input_ids[0] as u8,input)
                .map_err(|e| format!("execute {label}: {e:?}"))?;
        }
        let directory = output.join(label); fs::create_dir(&directory)?;
        let result = backend.diagnostic_tensors();
        write(&directory.join("serving_default_hidden_output.raw"),&result.hidden)?;
        for layer in 0..5 {
            write(&directory.join(format!("serving_default_kv_cache_k_{layer}_output.raw")),&result.output_keys[layer])?;
            write(&directory.join(format!("serving_default_kv_cache_v_{layer}_output.raw")),&result.output_values[layer])?;
        }
        println!("case={label} repeats={repeats} outside_position_cache_bit_equal={outside_unchanged} rss_kib={}",rss());
    }
    let timing = backend.timings();
    println!("context_opens={} graph_executions={} context_open_ns={} graph_execute_ns={} verification_and_cache_copy_ns={} diagnostic_only=true",
        timing.opens,timing.executions,timing.context_open_ns,timing.graph_execute_ns,timing.verification_and_cache_copy_ns);
    let memory = backend.sdk_memory_stats();
    println!("registered_buffers={} sdk_graph_ns={} rpc_input_copy_ns={} rpc_output_copy_ns={}",memory[0],memory[1],memory[2],memory[3]);
    backend.close();
    println!("rss_after_close_kib={}",rss());
    Ok(())
}

#[cfg(not(feature = "qnn-sdk"))]
fn main() { eprintln!("Enable qnn-sdk for the fixed-input diagnostic"); }
