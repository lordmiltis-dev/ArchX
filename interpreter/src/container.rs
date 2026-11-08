// Das .archx-Dateiformat: Kopf (17 Bytes) plus Protobuf-Block, siehe docs/adr/0005-archx-dateiformat.md
//
//   Bytes 0 bis 4    Kennung "ARCHX"
//   Bytes 5 bis 8    Formatversion (u32, niedrigstes Byte zuerst)
//   Bytes 9 bis 16   Länge des Protobuf-Blocks (u64, niedrigstes Byte zuerst)
//   ab Byte 17       Protobuf-Block

#![allow(dead_code)] // solange main() das Modul noch nicht benutzt

use crate::archx::Showfile;
use prost::Message;
use std::fs::{self, File};
use std::io::Write;
use std::path::{Path, PathBuf};

pub const MAGIC: &[u8; 5] = b"ARCHX";
pub const FORMAT_VERSION: u32 = 1;
pub const HEADER_LEN: usize = 5 + 4 + 8;

#[derive(Debug)]
pub enum ContainerError {
    /// Lesen oder Schreiben der Datei ist fehlgeschlagen
    Io(std::io::Error),
    /// Die Datei beginnt nicht mit "ARCHX"
    BadMagic,
    /// Die Datei stammt aus einer neueren Version des Formats
    UnsupportedVersion(u32),
    /// Die Datei ist kürzer, als der Kopf angibt (oder kürzer als der Kopf selbst)
    Truncated,
    /// Der Kopf stimmt, aber der Protobuf-Block lässt sich nicht lesen
    Corrupt(String),
}

impl From<std::io::Error> for ContainerError {
    fn from(e: std::io::Error) -> Self {
        ContainerError::Io(e)
    }
}

impl From<prost::DecodeError> for ContainerError {
    fn from(e: prost::DecodeError) -> Self {
        ContainerError::Corrupt(e.to_string())
    }
}

pub(crate) fn temp_path(path: &Path) -> PathBuf {
    let mut name = path.as_os_str().to_owned();
    name.push(".tmp");
    PathBuf::from(name)
}
/// Schreibt die Show sicher in eine Datei: erst in eine Temp-Datei, dann umbenennen.
pub fn save_show(path: &Path, show: &Showfile) -> Result<(), ContainerError> {
    let payload = show.encode_to_vec();
    let mut bytes = Vec::with_capacity(HEADER_LEN + payload.len());
    let tmp = temp_path(path);

    bytes.extend_from_slice(MAGIC);
    bytes.extend_from_slice(&FORMAT_VERSION.to_le_bytes());
    bytes.extend_from_slice(&(payload.len() as u64).to_le_bytes());
    bytes.extend_from_slice(&payload);

    {
        let mut file = File::create(&tmp)?;
        file.write_all(&bytes)?;
        file.sync_all()?;
    }

    fs::rename(&tmp, path)?;
    Ok(())
}

/// Liest eine Show aus einer Datei und prüft dabei Kennung, Version und Länge.
pub fn load_show(path: &Path) -> Result<Showfile, ContainerError> {
    let bytes =   fs::read(path)?;
    if bytes.len() < HEADER_LEN {
        return Err(ContainerError::Truncated);
    }
    if &bytes[0..5] != MAGIC {
        return Err(ContainerError::BadMagic);
    }

    let version = u32::from_le_bytes(bytes[5..9].try_into().unwrap());
    if version > FORMAT_VERSION {
        return Err(ContainerError::UnsupportedVersion(version));
    }

    let payload_len = u64::from_le_bytes(bytes[9..17].try_into().unwrap()) as usize;
    let end = HEADER_LEN
        .checked_add(payload_len)
        .ok_or(ContainerError::Truncated)?;
    if bytes.len() < end {
        return Err(ContainerError::Truncated);
    }

    let show = Showfile::decode(&bytes[HEADER_LEN..end])?;
    Ok(show)

}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::archx::{FixtureType, PatchEntry};
    use std::fs;
    use tempfile::tempdir;

    // Eine kleine Show mit einem Typ und einem Patch
    fn sample_show() -> Showfile {
        Showfile {
            name: "Testshow".into(),
            fixture_types: vec![FixtureType {
                id: 1,
                name: "Platinum Mini TRI-PAR".into(),
                channel_count: 6,
                ..Default::default()
            }],
            patch: vec![PatchEntry {
                id: 1,
                fixture_number: 1,
                name: "Par 1".into(),
                fixture_type_id: 1,
                universe: 1,
                address: 1,
                ..Default::default()
            }],
            next_patch_id: 2,
        }
    }

    // ----- Speichern und Laden -----

    #[test]
    fn save_then_load_returns_same_show() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        let show = sample_show();

        save_show(&path, &show).unwrap();
        let loaded = load_show(&path).unwrap();

        assert_eq!(show, loaded);
    }

    #[test]
    fn saving_again_replaces_the_old_file() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");

        save_show(&path, &sample_show()).unwrap();
        let mut second = sample_show();
        second.name = "Zweite Show".into();
        save_show(&path, &second).unwrap();

        assert_eq!(load_show(&path).unwrap().name, "Zweite Show");
    }

    #[test]
    fn saving_leaves_no_temp_file_behind() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");

        save_show(&path, &sample_show()).unwrap();

        // Im Ordner darf nur die fertige Datei liegen
        assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
    }

    // ----- Aufbau der Datei -----

    #[test]
    fn file_starts_with_magic_and_version() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        save_show(&path, &sample_show()).unwrap();

        let bytes = fs::read(&path).unwrap();

        assert_eq!(&bytes[0..5], MAGIC);
        assert_eq!(u32::from_le_bytes(bytes[5..9].try_into().unwrap()), FORMAT_VERSION);
    }

    #[test]
    fn header_stores_the_payload_length() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        save_show(&path, &sample_show()).unwrap();

        let bytes = fs::read(&path).unwrap();
        let stored_len = u64::from_le_bytes(bytes[9..17].try_into().unwrap());

        assert_eq!(stored_len as usize, bytes.len() - HEADER_LEN);
    }

    // ----- Ungültige Dateien -----

    #[test]
    fn missing_file_is_an_io_error() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("gibt-es-nicht.archx");

        assert!(matches!(load_show(&path), Err(ContainerError::Io(_))));
    }

    #[test]
    fn empty_file_is_rejected_as_truncated() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("leer.archx");
        fs::write(&path, b"").unwrap();

        assert!(matches!(load_show(&path), Err(ContainerError::Truncated)));
    }

    #[test]
    fn wrong_magic_is_rejected() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        save_show(&path, &sample_show()).unwrap();

        let mut bytes = fs::read(&path).unwrap();
        bytes[0..5].copy_from_slice(b"NOPE!");
        fs::write(&path, bytes).unwrap();

        assert!(matches!(load_show(&path), Err(ContainerError::BadMagic)));
    }

    #[test]
    fn newer_format_version_is_rejected() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        save_show(&path, &sample_show()).unwrap();

        let mut bytes = fs::read(&path).unwrap();
        let newer = FORMAT_VERSION + 1;
        bytes[5..9].copy_from_slice(&newer.to_le_bytes());
        fs::write(&path, bytes).unwrap();

        match load_show(&path) {
            Err(ContainerError::UnsupportedVersion(v)) => assert_eq!(v, newer),
            other => panic!("erwartet UnsupportedVersion, erhalten: {:?}", other),
        }
    }

    #[test]
    fn truncated_payload_is_rejected() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        save_show(&path, &sample_show()).unwrap();

        let mut bytes = fs::read(&path).unwrap();
        bytes.truncate(bytes.len() - 3); // die letzten drei Bytes fehlen
        fs::write(&path, bytes).unwrap();

        assert!(matches!(load_show(&path), Err(ContainerError::Truncated)));
    }

    #[test]
    fn garbage_payload_is_rejected_as_corrupt() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");

        // Gültiger Kopf, aber der Block besteht aus Unsinn
        let garbage = [0xFFu8; 10];
        let mut bytes = Vec::new();
        bytes.extend_from_slice(MAGIC);
        bytes.extend_from_slice(&FORMAT_VERSION.to_le_bytes());
        bytes.extend_from_slice(&(garbage.len() as u64).to_le_bytes());
        bytes.extend_from_slice(&garbage);
        fs::write(&path, bytes).unwrap();

        assert!(matches!(load_show(&path), Err(ContainerError::Corrupt(_))));
    }

    #[test]
    fn huge_length_in_header_is_rejected_as_truncated() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");

        let mut bytes = Vec::new();
        bytes.extend_from_slice(MAGIC);
        bytes.extend_from_slice(&FORMAT_VERSION.to_le_bytes());
        bytes.extend_from_slice(&u64::MAX.to_le_bytes()); // behauptet eine unmögliche Länge
        fs::write(&path, bytes).unwrap();

        assert!(matches!(load_show(&path), Err(ContainerError::Truncated)));
    }

    #[test]
    fn failed_save_keeps_the_old_file() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        save_show(&path, &sample_show()).unwrap();

        // An der Stelle der Temp-Datei liegt ein Ordner, deshalb schlägt das Anlegen fehl
        fs::create_dir(temp_path(&path)).unwrap();

        let mut other = sample_show();
        other.name = "Andere Show".into();
        assert!(save_show(&path, &other).is_err());

        // Die alte Datei ist noch vollständig da
        assert_eq!(load_show(&path).unwrap().name, "Testshow");
    }

    // Eine Temp-Datei von einem früheren Absturz darf das Speichern nicht stören
    #[test]
    fn stale_temp_file_is_overwritten() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.archx");
        fs::write(temp_path(&path), b"Reste eines frueheren Absturzes").unwrap();

        save_show(&path, &sample_show()).unwrap();

        assert_eq!(load_show(&path).unwrap(), sample_show());
        assert!(!temp_path(&path).exists());
    }
}
