
fn main() {
    // protoc comes from the protoc-bin-vendored package, so nothing needs to be installed separately.
    let protoc = protoc_bin_vendored::protoc_bin_path().expect("protoc not found");

    prost_build::Config::new()
        .protoc_executable(protoc)
        .compile_protos(&["../schema/showfile.proto"], &["../schema"])
        .expect("Schema could not be compiled");

    // Cargo should rebuild whenever anything in the schema directory changes.
    println!("cargo:rerun-if-changed=../schema");
}
