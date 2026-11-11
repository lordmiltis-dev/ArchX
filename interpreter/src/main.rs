// Der Code aus schema/showfile.proto. Er entsteht beim Bauen durch build.rs.
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

const USAGE: &str = "Aufruf:
  archx-interpreter pack <show.bin> <show.archx>     rohe Protobuf-Bytes in eine .archx-Datei verpacken
  archx-interpreter unpack <show.archx> <show.bin>   .archx-Datei wieder in rohe Bytes auspacken
  archx-interpreter info <show.archx>                Inhalt einer .archx-Datei anzeigen";

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();
    match run(&args) {
        Ok(()) => ExitCode::SUCCESS,
        Err(message) => {
            eprintln!("Fehler: {message}");
            ExitCode::FAILURE
        }
    }
}

// Wählt anhand des ersten Arguments den Befehl aus
fn run(args: &[String]) -> Result<(), String> {
    match args.get(1).map(String::as_str) {
        Some("pack") if args.len() == 4 => pack(Path::new(&args[2]), Path::new(&args[3])),
        Some("unpack") if args.len() == 4 => unpack(Path::new(&args[2]), Path::new(&args[3])),
        Some("info") if args.len() == 3 => info(Path::new(&args[2])),
        _ => Err(USAGE.to_string()),
    }
}

// Rohe Protobuf-Bytes (zum Beispiel aus Kotlin exportiert) in eine .archx-Datei verpacken
fn pack(input: &Path, output: &Path) -> Result<(), String> {
    let bytes = fs::read(input)
        .map_err(|e| format!("{} lässt sich nicht lesen: {e}", input.display()))?;
    let show = Showfile::decode(bytes.as_slice())
        .map_err(|e| format!("Das ist keine gültige Show: {e}"))?;
    container::save_show(output, &show)
        .map_err(|e| format!("Speichern fehlgeschlagen: {e:?}"))?;
    println!("{} Bytes aus {} nach {} gepackt", bytes.len(), input.display(), output.display());
    Ok(())
}

// Eine .archx-Datei wieder in rohe Protobuf-Bytes auspacken
fn unpack(input: &Path, output: &Path) -> Result<(), String> {
    let show = container::load_show(input)
        .map_err(|e| format!("Laden fehlgeschlagen: {e:?}"))?;
    let bytes = show.encode_to_vec();
    fs::write(output, &bytes)
        .map_err(|e| format!("{} lässt sich nicht schreiben: {e}", output.display()))?;
    println!("{} Bytes aus {} nach {} ausgepackt", bytes.len(), input.display(), output.display());
    Ok(())
}

// Den Inhalt einer .archx-Datei anzeigen
fn info(path: &Path) -> Result<(), String> {
    let show = container::load_show(path)
        .map_err(|e| format!("Laden fehlgeschlagen: {e:?}"))?;
    println!("{}", describe(&show));
    Ok(())
}

/// Beschreibt eine Show als Text. Das Format:
///
/// ```text
/// Show: Testshow
/// Typen: 1
/// Patches: 2
///   Patch 1: Par 1 (Universum 1, Adresse 1)
///   Patch 2: Par 2 (Universum 1, Adresse 7)
/// ```
fn describe(show: &Showfile) -> String {
    let mut text = String::new();
    text.push_str(&format!("Show: {} \n", show.name));
    text.push_str(&format!("Typen: {} \n", show.fixture_types.len()));
    text.push_str(&format!("Patches: {} \n", show.patch.len()));

    for patch in &show.patch{


        text.push_str(&format!("Patch {}: {} (Universum {}, Adresse {}) \n",patch.id, patch.name, patch.universe, patch.address));
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

    // Liest die Datei, die das Kotlin-Programm geschrieben hat, und prüft die Werte
    #[test]
    fn reads_showfile_written_by_kotlin() {
        let bytes = fs::read(GOLDEN_BIN)
            .expect("Testdatei fehlt: zuerst in Kotlin exportToBinary ausführen");
        let show = Showfile::decode(bytes.as_slice()).expect("Die Bytes sind kein gültiges Showfile");

        assert_eq!(show.name, "Testshow");
        assert_eq!(show.next_patch_id, 3);

        // Ein Typ, zwei Patches
        assert_eq!(show.fixture_types.len(), 1);
        assert_eq!(show.patch.len(), 2);
        assert_eq!(show.patch[0].fixture_type_id, show.patch[1].fixture_type_id);
        assert_eq!(show.patch[0].address, 1);
        assert_eq!(show.patch[1].address, 7);

        // Der Typ und seine Kanäle
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

    // Verpacken und wieder Auspacken ergibt dieselbe Show
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
