# ADR 0006: Speicherorte

- **Status:** Vorläufig
- **Datum:** 2026-10-28

## Kontext

ArchX arbeitet mit mehreren Arten von Daten: Fixture-Libraries, Showfiles mit zugehörigen Medien, automatische Sicherungen, Einstellungen und Zwischenspeicher (zum Beispiel ein Index über die Library). Sie sollen auf Linux und Windows an vorhersehbaren Orten liegen, vom Installations-Wizard angelegt werden und vom Nutzer änderbar sein. Fremde Inhalte wie Libraries gehören nicht ins Repository (siehe ADR 0004).

## Entscheidung

1. **Standardorte im Benutzerverzeichnis:**

   | | Linux | Windows |
   |---|---|---|
   | Daten (Libraries, Shows) | `~/.local/share/archx/` | `%APPDATA%\ArchX\` |
   | Einstellungen | `~/.config/archx/` | `%APPDATA%\ArchX\` |
   | Zwischenspeicher (Index) | `~/.cache/archx/` | `%LOCALAPPDATA%\ArchX\` |

2. **Aufbau unterhalb der Datenordner:**

   ```
   libraries/<quelle>/   (zum Beispiel libraries/ofl/, passend zu library_source im Schema)
   shows/                (Showfiles mit ihren Medienordnern, siehe ADR 0005)
   autosave/             (Journal und automatische Sicherungen)
   ```

3. **Der Installations-Wizard** legt die Ordner an, lässt den Nutzer bei Bedarf einen anderen Ort wählen und schreibt die Pfade in die Einstellungen.
4. **Beim ersten Start weist die Standard-UI darauf hin**, wo die Ordner liegen und dass noch keine Fixture-Library vorhanden ist, mit einem Weg zum Import (siehe ADR 0004).
5. **Pfade stehen nur in den Einstellungen, nie im Showfile.** Ein Showfile verweist auf Medien ausschließlich über relative Pfade und Kennungen (siehe ADR 0005).
6. **Ein fehlender oder nicht erreichbarer Ordner stoppt nichts.** Shows lassen sich weiter laden, weil sie die benutzten Fixture-Typen enthalten. Die Oberfläche zeigt einen Hinweis.
7. **Im Repository** liegen keine Libraries. Für lokale Tests dient `local-data/` (in der `.gitignore`), für automatische Tests ein kleiner Satz freigegebener Beispieldateien in `native/fixtures/testdata/`.

## Begründung

- Das Benutzerverzeichnis braucht keine Administratorrechte, und Updates der Software berühren die Daten nicht.
- Die XDG-Ordner unter Linux sind der übliche Standard, `%APPDATA%` und `%LOCALAPPDATA%` entsprechen ihm unter Windows.
- Der Index ist neu erzeugbar und gehört deshalb in den Zwischenspeicher. Er kann gefahrlos gelöscht werden.
- Weil Pfade nur in den Einstellungen stehen, lassen sich Showfiles zwischen Rechnern weitergeben.

## Offene Punkte

- **ArchXOs als Gerät:** Auf einer fest installierten Konsole kann ein systemweiter Ort sinnvoller sein (zum Beispiel `/var/lib/archx/`). Das wird bei der Image-Erstellung in Phase 11 entschieden.
- Format und Ort der Einstellungsdatei.
- Wechseldatenträger als Speicherort für Shows (Warnung bei Entfernen während der Sicherung).

## Konsequenzen

- Der Ordner `Savefiles` im Repository ist nur ein Entwicklungsordner. Echte Shows landen im Ordner `shows/` des Benutzerverzeichnisses.
- Der Fixtures-Library-Submaster und der Savefile-Interpreter lesen die Pfade aus den Einstellungen und nehmen keine festen Pfade an.
