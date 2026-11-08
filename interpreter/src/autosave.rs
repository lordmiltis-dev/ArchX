// Automatische Sicherungen mit Rotation, siehe docs/adr/0007-autosave.md
//
// Dateiname einer Sicherung: "<Showname>-<Nummer mit 6 Stellen>.archx", zum Beispiel "Testshow-000042.archx".
// Die Nummer steigt immer weiter. Hinweis: Der Showname sollte später für Dateinamen bereinigt werden
// (Schrägstriche und andere unerlaubte Zeichen).

#![allow(dead_code)] // solange main() das Modul noch nicht benutzt

use crate::archx::Showfile;
use crate::container::{load_show, save_show, ContainerError, temp_path};
use std::fs;
use std::path::{Path, PathBuf};

/// Liest die Nummer aus einem Dateinamen wie "Testshow-000042.archx".
/// Gibt `None` zurück, wenn der Name nicht zu dieser Show gehört.
fn autosave_number(file_name: &str, show_name: &str) -> Option<u64> {
    let rest = file_name.strip_prefix(show_name)?.strip_prefix('-')?;
    let digits = rest.strip_suffix(".archx")?;
    digits.parse().ok()
}

/// Alle Sicherungen dieser Show mit ihrer Nummer, die neueste zuerst.
/// Gibt es den Ordner nicht, ist die Liste leer.
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
    found.sort_by(|a, b| b.0.cmp(&a.0)); // höchste Nummer zuerst
    Ok(found)
}

/// Pfade aller Sicherungen dieser Show, die neueste zuerst.
pub fn list_autosaves(dir: &Path, show_name: &str) -> Result<Vec<PathBuf>, ContainerError> {
    let numbered = numbered_autosaves(dir, show_name)?;
    Ok(numbered.into_iter().map(|(_, path)| path).collect())
}

/// Schreibt eine neue Sicherung mit der nächsthöheren Nummer und legt den Ordner an, falls er fehlt.
/// Liefert den Pfad der neuen Datei.
pub fn write_autosave(dir: &Path, show_name: &str, show: &Showfile) -> Result<PathBuf, ContainerError> {
    fs::create_dir_all(dir)?;
    let next = numbered_autosaves(dir, show_name)?.first().map(|(n,_)| n + 1).unwrap_or(1);
    let show_name = format!("{show_name}-{next:06}.archx");
    let path = dir.join(show_name);
    save_show(&path, show)?;
    Ok(path)
}

/// Löscht alle Sicherungen dieser Show bis auf die neuesten `keep`.
pub fn prune_autosaves(dir: &Path, show_name: &str, keep: usize) -> Result<(), ContainerError> {
    todo!("hier kommt dein Code hin")
}

/// Lädt die Hauptdatei. Gelingt das nicht, wird die neueste lesbare Sicherung genommen.
/// Findet sich auch dort nichts Lesbares, kommt der Fehler der Hauptdatei zurück.
pub fn load_with_fallback(
    main_path: &Path,
    autosave_dir: &Path,
    show_name: &str,
) -> Result<Showfile, ContainerError> {
    todo!("hier kommt dein Code hin")
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

    // ----- Schreiben und Auflisten -----

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
        let sub = dir.path().join("autosave"); // existiert noch nicht

        write_autosave(&sub, "Testshow", &show_named("Testshow")).unwrap();

        assert!(sub.exists());
    }

    #[test]
    fn missing_folder_means_empty_list() {
        let dir = tempdir().unwrap();
        let sub = dir.path().join("gibt-es-nicht");

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

   /* #[test]
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

    // Nach dem Löschen alter Dateien darf die Nummerierung nicht von vorn beginnen
    #[test]
    fn numbers_keep_growing_after_pruning() {
        let dir = tempdir().unwrap();
        for name in ["eins", "zwei", "drei"] {
            write_autosave(dir.path(), "Testshow", &show_named(name)).unwrap();
        }
        prune_autosaves(dir.path(), "Testshow", 1).unwrap(); // nur "drei" bleibt

        write_autosave(dir.path(), "Testshow", &show_named("vier")).unwrap();

        let list = list_autosaves(dir.path(), "Testshow").unwrap();
        assert_eq!(load_show(&list[0]).unwrap().name, "vier");
        assert_eq!(load_show(&list[1]).unwrap().name, "drei");
    }

    // ----- Wiederherstellung -----

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

        // Die neueste Sicherung beschädigen
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
        fs::write(&main, b"kaputt").unwrap(); // weniger Bytes als der Kopf, also "Truncated"

        let result = load_with_fallback(&main, &autosaves, "Testshow");

        assert!(matches!(result, Err(ContainerError::Truncated)));
    }*/
}
