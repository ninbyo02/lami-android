# LAMI Resident CP Rust core (host prototype)

Implements the CP resident context lifecycle and 16-position ordering as a Rust-owned abstraction. This host crate does **not** implement Qualcomm QNN/HTP, JNI, tensor storage, inference accuracy, or device benchmarking.

Run: `cargo test --offline && cargo clippy --offline --all-targets -- -D warnings`

Planned integration: Kotlin `ResidentCpQnnDriver.NativeApi` -> Android JNI -> Rust `ResidentCp` -> narrowly scoped unsafe QNN C FFI (after matching SDK API headers and exact AOT graph tensors). No device operations are performed by this crate.
