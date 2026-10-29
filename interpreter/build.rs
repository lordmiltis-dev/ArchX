
fn main(){
    // protoc kommt aus dem Paket protoc-bin-vendored, es muss nichts installiert sein
    let protoc = protoc_bin_vendored::protoc_bin_path().expect("protoc nicht gefunden");

    prost_build::Config::new()
        .protoc_executable(protoc)
        .compile_protos(&["../schema/showfile.proto"], &["../schema"])
        .expect("Schema konnte nicht übersetzt werden");

    // Cargo soll neu bauen, sobald sich etwas im Schema-Ordner ändert
    println!("cargo:rerun-if-changed=../schema");


}
