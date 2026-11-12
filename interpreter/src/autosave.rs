// Automatic backups with rotation, see docs/adr/0007-autosave.md.
//
// Backup file name: "<Show name>-<6-digit number>.archx", for example "Testshow-000042.archx".
// The number always increases. Note: the show name should later be sanitized for file names
// (slashes and other forbidden characters).

#![allow(dead_code)] // main() does not use this module yet

use crate::archx::Showfile;
use crate::container::{load_show, save_show, ContainerError, temp_path};
use std::fs;
use std::path::{Path, PathBuf};
use crate::main;

/// Read the numeric suffix from a backup file name like "Testshow-000042.archx".
/// Returns `None` when the file name does not belong to this show.
fn autosave_number(file_name: &str, show_name: &str) -> Option<u64> {
    let rest = file_name.strip_prefix(show_name)?.strip_prefix('-')?;
    let digits = rest.strip_suffix(".archx")?;
    digits.parse().ok()
}

/// All backups for this show with their numbers, newest first.
/// If the folder does not exist, the result is empty.
fn numbered_autosaves(dir: &Path, show_name: &str) -> Result<Vec<(u64, PathBuf)>, ContainerError> {
    if !dir.exists() {
        return Ok(Vec::new());
    }
    let mut found: Vec<(u64, PathBuf)> = Vec::new();
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        let name = entry.file_name().to_string_lossy().into_owned();
        if let Some(number) = autosave_number(&name, show_name) {
            found.push((number, entry.path()));
        }
    }
    found.sort_by(|a, b| b.0.cmp(&a.0)); // highest number first
    Ok(found)
}

/// Paths to all backups of this show, newest first.
pub fn list_autosaves(dir: &Path, show_name: &str) -> Result<Vec<PathBuf>, ContainerError> {
    let numbered = numbered_autosaves(dir, show_name)?;
    Ok(numbered.into_iter().map(|(_, path)| path).collect())
}

/// Write a new backup with the next higher number and create the directory if it is missing.
/// Returns the path to the new file.
pub fn write_autosave(dir: &Path, show_name: &str, show: &Showfile) -> Result<PathBuf, ContainerError> {
    fs::create_dir_all(dir)?;
    let next = numbered_autosaves(dir, show_name)?.first().map(|(n, _)| n + 1).unwrap_or(1);
    let show_name = format!("{show_name}-{next:06}.archx");
    let path = dir.join(show_name);
    save_show(&path, show)?;
    Ok(path)
}

/// Delete all backups for this show except the newest `keep` entries.
pub fn prune_autosaves(dir: &Path, show_name: &str, keep: usize) -> Result<(), ContainerError> {
    let autosaves = numbered_autosaves(dir, show_name)?;

    for backup_tuple in autosaves.iter().skip(keep) {
        let rmfile = &backup_tuple.1;

        std::fs::remove_file(rmfile).map_err(|e| {
            ContainerError::Io(e)
        })?;
    }

    Ok(())
}

/// Load the main file. If that fails, use the newest readable autosave.
/// If no readable files are available, return the original main-file error.
pub fn load_with_fallback(main_path: &Path, autosave_dir: &Path, show_name: &str) -> Result<Showfile, ContainerError> {
    let main_show = main_path;
    let fallback_list = numbered_autosaves(autosave_dir, show_name)?;

    match load_show(main_show) {
        Ok(main_show) => Ok(main_show),
        Err(original_error) => {
            for backup_tuple in &fallback_list {
                let backup_path = &backup_tuple.1;

                if let Ok(backup_show) = load_show(backup_path) {
                    return Ok(backup_show);
                }
            }

            if fallback_list.is_empty() {
                Err(original_error)
            } else {
                Err(ContainerError::Corrupt("All Showfiles and Autosaves are Corrupted".parse().unwrap()))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn show_named(name: &str) -> Showfile {
        Showfile {
            name: name.into(),
            next_patch_id: 1,
            ..Default::default()
        }
    }

    // ----- Write and list -----

    #[test]
    fn first_autosave_is_created_and_loadable() {
        let dir = tempdir().unwrap();
        let path = write_autosave(dir.path(), "Testshow", &show_named("Testshow")).unwrap();

        assert!(path.exists());
        assert_eq!(load_show(&path).unwrap().name, "Testshow");
    }

    #[test]
    fn missing_folder_is_created() {
        let dir = tempdir().unwrap();
        let sub = dir.path().join("autosave"); // does not exist yet

        write_autosave(&sub, "Testshow", &show_named("Testshow")).unwrap();

        assert!(sub.exists());
    }

    #[test]
    fn missing_folder_means_empty_list() {
        let dir = tempdir().unwrap();
        let sub = dir.path().join("does-not-exist");

        assert!(list_autosaves(&sub, "Testshow").unwrap().is_empty());
    }

    #[test]
    fn autosaves_are_listed_newest_first() {
        let dir = tempdir().unwrap();
        for name in ["eins", "zwei", "drei"] {
            write_autosave(dir.path(), "Testshow", &show_named(name)).unwrap();
        }

        let list = list_autosaves(dir.path(), "Testshow").unwrap();

        assert_eq!(list.len(), 3);
        assert_eq!(load_show(&list[0]).unwrap().name, "drei");
        assert_eq!(load_show(&list[2]).unwrap().name, "eins");
    }

    #[test]
    fn autosaves_of_other_shows_are_ignored() {
        let dir = tempdir().unwrap();
        write_autosave(dir.path(), "Testshow", &show_named("a")).unwrap();
        write_autosave(dir.path(), "Andere", &show_named("b")).unwrap();
        write_autosave(dir.path(), "Test", &show_named("c")).unwrap();

        assert_eq!(list_autosaves(dir.path(), "Testshow").unwrap().len(), 1);
        assert_eq!(list_autosaves(dir.path(), "Test").unwrap().len(), 1);
    }

    // ----- Rotation -----

    #[test]
    fn prune_keeps_only_the_newest() {
        let dir = tempdir().unwrap();
        for name in ["eins", "zwei", "drei", "vier", "fuenf"] {
            write_autosave(dir.path(), "Testshow", &show_named(name)).unwrap();
        }

        prune_autosaves(dir.path(), "Testshow", 2).unwrap();

        let list = list_autosaves(dir.path(), "Testshow").unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(load_show(&list[0]).unwrap().name, "fuenf");
        assert_eq!(load_show(&list[1]).unwrap().name, "vier");
    }

    #[test]
    fn prune_with_fewer_files_than_limit_changes_nothing() {
        let dir = tempdir().unwrap();
        write_autosave(dir.path(), "Testshow", &show_named("eins")).unwrap();
        write_autosave(dir.path(), "Testshow", &show_named("zwei")).unwrap();

        prune_autosaves(dir.path(), "Testshow", 5).unwrap();

        assert_eq!(list_autosaves(dir.path(), "Testshow").unwrap().len(), 2);
    }

    // After deleting old files, numbering must not restart from the beginning.
    #[test]
    fn numbers_keep_growing_after_pruning() {
        let dir = tempdir().unwrap();
        for name in ["eins", "zwei", "drei"] {
            write_autosave(dir.path(), "Testshow", &show_named(name)).unwrap();
        }
        prune_autosaves(dir.path(), "Testshow", 1).unwrap(); // only "drei" remains

        write_autosave(dir.path(), "Testshow", &show_named("vier")).unwrap();

        let list = list_autosaves(dir.path(), "Testshow").unwrap();
        assert_eq!(load_show(&list[0]).unwrap().name, "vier");
        assert_eq!(load_show(&list[1]).unwrap().name, "drei");
    }

    // ----- Recovery -----

    #[test]
    fn healthy_main_file_is_preferred() {
        let dir = tempdir().unwrap();
        let main = dir.path().join("Testshow.archx");
        let autosaves = dir.path().join("autosave");
        save_show(&main, &show_named("Haupt")).unwrap();
        write_autosave(&autosaves, "Testshow", &show_named("Sicherung")).unwrap();

        let show = load_with_fallback(&main, &autosaves, "Testshow").unwrap();

        assert_eq!(show.name, "Haupt");
    }

    #[test]
    fn corrupt_main_file_falls_back_to_newest_autosave() {
        let dir = tempdir().unwrap();
        let main = dir.path().join("Testshow.archx");
        let autosaves = dir.path().join("autosave");
        fs::write(&main, b"kaputt").unwrap();
        write_autosave(&autosaves, "Testshow", &show_named("alt")).unwrap();
        write_autosave(&autosaves, "Testshow", &show_named("neu")).unwrap();

        let show = load_with_fallback(&main, &autosaves, "Testshow").unwrap();

        assert_eq!(show.name, "neu");
    }

    #[test]
    fn missing_main_file_falls_back_to_autosave() {
        let dir = tempdir().unwrap();
        let main = dir.path().join("gibt-es-nicht.archx");
        let autosaves = dir.path().join("autosave");
        write_autosave(&autosaves, "Testshow", &show_named("Sicherung")).unwrap();

        let show = load_with_fallback(&main, &autosaves, "Testshow").unwrap();

        assert_eq!(show.name, "Sicherung");
    }

    #[test]
    fn corrupt_newest_autosave_falls_back_to_the_one_before() {
        let dir = tempdir().unwrap();
        let main = dir.path().join("Testshow.archx");
        let autosaves = dir.path().join("autosave");
        fs::write(&main, b"kaputt").unwrap();
        write_autosave(&autosaves, "Testshow", &show_named("alt")).unwrap();
        write_autosave(&autosaves, "Testshow", &show_named("neu")).unwrap();

        // Corrupt the newest backup.
        let newest = list_autosaves(&autosaves, "Testshow").unwrap().remove(0);
        fs::write(&newest, b"kaputt").unwrap();

        let show = load_with_fallback(&main, &autosaves, "Testshow").unwrap();

        assert_eq!(show.name, "alt");
    }

    #[test]
    fn nothing_usable_returns_the_error_of_the_main_file() {
        let dir = tempdir().unwrap();
        let main = dir.path().join("Testshow.archx");
        let autosaves = dir.path().join("autosave");
        fs::write(&main, b"kaputt").unwrap(); // fewer bytes than header, therefore "Truncated"

        let result = load_with_fallback(&main, &autosaves, "Testshow");

        assert!(matches!(result, Err(ContainerError::Truncated)));
    }
}
