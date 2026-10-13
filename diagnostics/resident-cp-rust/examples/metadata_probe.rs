#[cfg(feature = "qnn-sdk")]
fn main() -> Result<(), Box<dyn std::error::Error>> {
    use lami_resident_cp::qnn::QnnBackend;
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 3 { return Err("Usage: metadata_probe VERIFIED_CONTEXT LIB_QNN_SYSTEM".into()); }
    let bytes = std::fs::read(&args[1])?;
    let mut backend = QnnBackend::from_verified_bytes(bytes, "/unused/backend", &args[2])
        .map_err(|error| format!("driver: {error:?}"))?;
    backend.validate_metadata_without_backend().map_err(|error| format!("metadata: {error:?}"))?;
    println!("metadata_validated=true input_tensors=13 output_tensors=11 backend_created=false graph_executions={}",
        backend.timings().executions);
    Ok(())
}

#[cfg(not(feature = "qnn-sdk"))]
fn main() { eprintln!("Enable qnn-sdk for the metadata-only probe"); }
