// The code generated from schema/showfile.proto; it is produced during the build via build.rs.
pub mod archx {
    include!(concat!(env!("OUT_DIR"), "/archx.rs"));
}

mod autosave;
mod container;

use archx::Showfile;
use prost::Message;
use std::fs;
use std::ops::Add;
use std::path::Path;
use std::process::ExitCode;

const USAGE: &str = "Usage:
  archx-interpreter pack <show.bin> <show.archx>     pack raw Protobuf bytes into a .archx file
  archx-interpreter unpack <show.archx> <show.bin>   unpack a .archx file back into raw bytes
  archx-interpreter info <show.archx>                display the contents of a .archx file";

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();
    match run(&args) {
        Ok(()) => ExitCode::SUCCESS,
        Err(message) => {
            eprintln!("Error: {message}");
            ExitCode::FAILURE
        }
    }
}

// Select the command based on the first argument.
fn run(args: &[String]) -> Result<(), String> {
    match args.get(1).map(String::as_str) {
        Some("pack") if args.len() == 4 => pack(Path::new(&args[2]), Path::new(&args[3])),
        Some("unpack") if args.len() == 4 => unpack(Path::new(&args[2]), Path::new(&args[3])),
        Some("info") if args.len() == 3 => info(Path::new(&args[2])),
        _ => Err(USAGE.to_string()),
    }
}

// Pack raw Protobuf bytes (for example exported from Kotlin) into a .archx file.
fn pack(input: &Path, output: &Path) -> Result<(), String> {
    let bytes = fs::read(input)
        .map_err(|e| format!("{} cannot be read: {e}", input.display()))?;
    let show = Showfile::decode(bytes.as_slice())
        .map_err(|e| format!("This is not a valid show: {e}"))?;
    container::save_show(output, &show)
        .map_err(|e| format!("Save failed: {e:?}"))?;
    println!("{} bytes from {} to {} packed", bytes.len(), input.display(), output.display());
    Ok(())
}

// Unpack a .archx file back to raw Protobuf bytes.
fn unpack(input: &Path, output: &Path) -> Result<(), String> {
    let show = container::load_show(input)
        .map_err(|e| format!("Load failed: {e:?}"))?;
    let bytes = show.encode_to_vec();
    fs::write(output, &bytes)
        .map_err(|e| format!("{} cannot be written: {e}", output.display()))?;
    println!("{} bytes from {} to {} unpacked", bytes.len(), input.display(), output.display());
    Ok(())
}

// Display the contents of a .archx file.
fn info(path: &Path) -> Result<(), String> {
    let show = container::load_show(path)
        .map_err(|e| format!("Load failed: {e:?}"))?;
    println!("{}", describe(&show));
    Ok(())
}

/// Describe a show as text. The format is:
///
/// ```text
/// Show: Testshow
/// Types: 1
/// Patches: 2
///   Patch 1: Par 1 (Universe 1, Address 1)
///   Patch 2: Par 2 (Universe 1, Address 7)
/// ```
fn describe(show: &Showfile) -> String {
    let mut text = String::new();
    text.push_str(&format!("Show: {} \n", show.name));
    text.push_str(&format!("Typen: {} \n", show.fixture_types.len()));
    text.push_str(&format!("Patches: {} \n", show.patch.len()));

    for patch in &show.patch {
        text.push_str(&format!("Patch {}: {} (Universum {}, Adresse {}) \n", patch.id, patch.name, patch.universe, patch.address));
    }
    print!("{}", text);

    text
}

#[cfg(test)]
mod tests {
    use super::archx::{Attribute, Precedence};
    use super::*;
    use tempfile::tempdir;

    const GOLDEN_BIN: &str = "../schema/testdata/showfile_example.bin";

    // Read the file written by the Kotlin program and verify the values.
    #[test]
    fn reads_showfile_written_by_kotlin() {
        let bytes = fs::read(GOLDEN_BIN)
            .expect("Test datei missing: run exportToBinary in Kotlin first");
        let show = Showfile::decode(bytes.as_slice()).expect("The bytes are not a valid Showfile");

        assert_eq!(show.name, "Testshow");
        assert_eq!(show.next_patch_id, 3);

        // One type, two patches
        assert_eq!(show.fixture_types.len(), 1);
        assert_eq!(show.patch.len(), 2);
        assert_eq!(show.patch[0].fixture_type_id, show.patch[1].fixture_type_id);
        assert_eq!(show.patch[0].address, 1);
        assert_eq!(show.patch[1].address, 7);

        // The type and its channels
        let typ = &show.fixture_types[0];
        assert_eq!(typ.name, "Platinum Mini TRI-PAR");
        assert_eq!(typ.channel_count, 6);
        assert_eq!(typ.channels.len(), 6);

        let intensity = &typ.channels[0];
        assert_eq!(intensity.offset, 1);
        assert_eq!(intensity.attribute(), Attribute::Intensity);
        assert_eq!(intensity.highlight_value, 255);
        assert_eq!(intensity.precedence(), Precedence::Htp);
    }

    // Packing and unpacking yields the same show again.
    #[test]
    fn pack_then_unpack_keeps_the_show() {
        let dir = tempdir().unwrap();
        let packed = dir.path().join("test.archx");
        let unpacked = dir.path().join("test.bin");

        pack(Path::new(GOLDEN_BIN), &packed).unwrap();
        unpack(&packed, &unpacked).unwrap();

        let original = Showfile::decode(fs::read(GOLDEN_BIN).unwrap().as_slice()).unwrap();
        let result = Showfile::decode(fs::read(&unpacked).unwrap().as_slice()).unwrap();
        assert_eq!(original, result);
    }

    #[test]
    fn pack_rejects_a_file_that_is_not_a_show() {
        let dir = tempdir().unwrap();
        let input = dir.path().join("kein-show.bin");
        fs::write(&input, [0xFFu8; 10]).unwrap();

        assert!(pack(&input, &dir.path().join("out.archx")).is_err());
    }

    #[test]
    fn describe_lists_name_counts_and_patches() {
        let show = Showfile::decode(fs::read(GOLDEN_BIN).unwrap().as_slice()).unwrap();

        let text = describe(&show);

        assert!(text.contains("Show: Testshow"));
        assert!(text.contains("Typen: 1"));
        assert!(text.contains("Patches: 2"));
        assert!(text.contains("Patch 1: Par 1 (Universum 1, Adresse 1)"));
        assert!(text.contains("Patch 2: Par 2 (Universum 1, Adresse 7)"));
    }
}
