# Arbeitsplan für zwei Tage

Stand: 2026-11-08

Dieser Plan führt dich durch die nächsten beiden Arbeitstage. Er enthält pro Tag die Ziele, die Dateien mit ihrem Ziel im Repo, die Schritte, Hinweise zu den Aufgaben und eine Liste, woran du erkennst, dass du fertig bist.

**Die Arbeitsweise bleibt wie bisher:** Die Tests und die Gerüste sind vorbereitet, du schreibst den Code, bis die Tests grün sind. Arbeite immer **einen Test nach dem anderen** und committe, sobald etwas grün ist. Die Zeitangaben sind grobe Richtwerte, wichtiger ist die Reihenfolge.

## Das Ziel der zwei Tage

- **Tag 1 (Rust und Kotlin):** Die automatischen Sicherungen sind fertig, der Interpreter bekommt ein kleines Kommandozeilenwerkzeug, und Kotlin und Rust beweisen mit einer gemeinsamen Datei, dass sie das `.archx`-Format gleich verstehen.
- **Tag 2 (C++):** Der erste Teil des Fixtures-Library-Submasters entsteht: ein Importer, der aus einer Open-Fixture-Library-Datei (JSON) einen `FixtureType` für unser Schema baut.

Damit ist **Phase 1 abgeschlossen** (Speichern, Laden, Sicherungen, Format-Vertrag) und der erste Baustein von **Phase 6** (Library) hat ein Fundament.

---

## Tag 1: Rust und Kotlin

### Ausgangslage

- `container.rs` ist fertig (Kopf, Laden, sicheres Speichern), alle Tests grün.
- `autosave.rs` ist im Gange: `list_autosaves` ist fertig, drei Funktionen (`write_autosave`, `prune_autosaves`, `load_with_fallback`) hast du selbst zu schreiben.

### Dateien für Tag 1

| Datei | Ziel im Repo | Was du tust |
|---|---|---|
| `interpreter/src/main.rs` | `interpreter/src/main.rs` | **Ersetzt** deine bisherige `main.rs`. Sie enthält das Kommandozeilenwerkzeug und die Tests. Der bisherige Test `reads_showfile_written_by_kotlin` ist darin enthalten. |
| `interpreter/src/autosave.rs` | `interpreter/src/autosave.rs` | Hast du schon. Nur dein Code gehört hinein, ersetze die Datei nicht. |
| `ArchxFileContractTest.kt` | `master/src/test/kotlin/` | Neue Datei, neben den anderen Testdateien. |

### Schritt 1: Autosave fertigstellen (autosave.rs)

Mache die letzten drei Funktionen fertig, bis alle Tests in `autosave.rs` grün sind (`cargo test autosave` führt nur diese aus). Die Hinweise stehen in der Nachricht zu `autosave.rs`. Kurzfassung:

- `write_autosave`: Ordner anlegen (`fs::create_dir_all`), nächste Nummer aus `numbered_autosaves` bestimmen (`first()`, `map`, `unwrap_or(1)`), Dateiname mit `format!("{show_name}-{next:06}.archx")`, dann `save_show`.
- `prune_autosaves`: Liste holen (neueste zuerst), mit `.into_iter().skip(keep)` die älteren auswählen und löschen.
- `load_with_fallback`: Hauptdatei probieren, bei einem Fehler die Sicherungen der Reihe nach, und `if let Ok(...)` benutzen, damit eine kaputte Sicherung nicht abbricht.

**Fertig, wenn:** `cargo test autosave` alles grün meldet. Committen.

### Schritt 2: Das Kommandozeilenwerkzeug (main.rs)

1. Ersetze `interpreter/src/main.rs` durch die neue Datei.
2. `cargo test`. Die Tests `reads_showfile_written_by_kotlin`, `pack_then_unpack_keeps_the_show` und `pack_rejects_a_file_that_is_not_a_show` sollten grün sein (`pack` und `unpack` sind fertig geschrieben). **Rot** ist nur `describe_lists_name_counts_and_patches`, denn `describe` enthält noch `todo!()`.
3. Schreibe `describe`: Sie gibt eine Beschreibung der Show als Text zurück. Das Format steht im Kommentar über der Funktion.

Hinweise zu `describe`:

- Fange mit `let mut text = String::new();` an. `mut`, weil du Text anhängst.
- Anhängen: `text.push_str(&format!("Show: {}\n", show.name));`. `format!` baut einen Text, `{}` ist die Stelle für den Wert, `\n` ist der Zeilenumbruch.
- Anzahl der Einträge einer Liste: `show.fixture_types.len()`.
- Alle Patches durchgehen: `for patch in &show.patch { ... }`. Das `&` leiht die Liste nur aus, du verbrauchst sie nicht.
- Am Ende steht `text` **ohne Semikolon** als Rückgabewert.

**Fertig, wenn:** `cargo test` alles grün meldet.

### Schritt 3: Die goldene .archx-Datei erzeugen

Das Werkzeug aus Schritt 2 macht aus der rohen Datei von Kotlin eine echte `.archx`-Datei. Im Ordner `interpreter`:

```
cargo run -- pack ../schema/testdata/showfile_example.bin ../schema/testdata/showfile_example.archx
```

- `cargo run --` startet das Programm, alles hinter `--` sind die Argumente des Programms.
- Es muss eine Meldung wie "... Bytes aus ... gepackt" erscheinen und die Datei `schema/testdata/showfile_example.archx` entstehen.
- Zur Kontrolle: `cargo run -- info ../schema/testdata/showfile_example.archx` zeigt dir die Show mit deiner Funktion `describe`.

**Achtung `.gitignore`:** Hast du die Regel `*.archx` eingetragen, ignoriert Git die neue Datei. Dann braucht die Datei eine Ausnahme: Die Zeile `!schema/testdata/*.archx` **nach** der Regel `*.archx`. Prüfe mit `git status`, ob die Datei als neue Datei erscheint.

### Schritt 4: Der Kotlin-Vertragstest

1. Lege `ArchxFileContractTest.kt` in `master/src/test/kotlin/` ab.
2. `.\gradlew test` im Ordner `master`.
3. Drei Tests: `headerIsAsSpecified` liest den Kopf der Datei **ohne** den Rust-Code (nur anhand der Beschreibung im ADR 0005). `payloadIsTheExampleShow` prüft, dass der Block hinter dem Kopf die Beispiel-Show ist. `showFromRustFileCanBeLoadedIntoTheBuilder` lädt sie in den `ShowBuilder`.

Das ist der wichtigste Test des Tages: Zwei Sprachen, die den Code des anderen nie gesehen haben, sind sich einig. Fällt er durch, ist die Beschreibung im ADR oder eine der beiden Umsetzungen falsch.

Mögliche Anpassungen: Heißt dein Beispiel-Code anders (`ExampleShow().exampleShow()`), passe die Aufrufe an. Hat `ShowBuilder` einen Pflichtparameter, setze ihn ein.

### Tag 1 ist fertig, wenn

- `cargo test` im Ordner `interpreter` grün ist,
- `.\gradlew test` im Ordner `master` grün ist,
- `schema/testdata/showfile_example.archx` im Repo liegt,
- alles committet und gepusht ist und die **drei Jobs auf GitHub grün** sind.

---

## Tag 2: C++ (Fixtures-Library)

C++ kennst du besser als Rust, deshalb sind die Hinweise knapper. Gearbeitet wird in der WSL-Toolchain von CLion (Windows) oder direkt auf dem Linux-Laptop.

### Vorbereitung

Der Importer braucht eine JSON-Bibliothek (`nlohmann_json`). Installiere sie in Ubuntu/WSL:

```
sudo apt update
sudo apt install -y nlohmann-json3-dev
```

Auf Arch Linux: `sudo pacman -S nlohmann-json`.

**Wichtig vor dem ersten Push:** Die CI braucht dieselbe Bibliothek. Ergänze in der `ci.yml` beim Job `cpp` in der Zeile mit `apt-get install` das Paket `nlohmann-json3-dev`. Sonst wird der Job rot, sobald du die neue `CMakeLists.txt` pushst.

### Dateien für Tag 2

| Datei | Ziel im Repo | Was du tust |
|---|---|---|
| `native/CMakeLists.txt` | `native/CMakeLists.txt` | **Ersetzt** die bisherige Datei. Neu: eine gemeinsame Bibliothek `archx_schema` für den Schema-Code, die Suche nach `nlohmann_json` und das Modul `archx_fixtures` mit seinem Test. |
| `native/fixtures/ofl_importer.h` | `native/fixtures/ofl_importer.h` | Neue Datei. Beschreibt die Funktion `ImportOflMode`. Nicht ändern. |
| `native/fixtures/ofl_importer.cpp` | `native/fixtures/ofl_importer.cpp` | Neue Datei, ein **Gerüst**. Hier schreibst du den Importer. |
| `native/fixtures/tests/ofl_importer_test.cpp` | `native/fixtures/tests/ofl_importer_test.cpp` | Neue Datei mit den Tests. Nicht ändern, außer dem Schalter `kRunStretchTests` (siehe unten). |

Danach lädst du CMake in CLion neu. Das Programm `fader_test` benutzt jetzt die gemeinsame Bibliothek, es ändert sich für dich nichts, es soll weiter grün sein.

### Die Aufgabe

`ImportOflMode(json_text, manufacturer_name, mode_name)` liest den Text einer OFL-Datei und gibt für **einen Modus** einen `FixtureType` zurück. Die Schritte stehen als Kommentar im Gerüst. In Kurzform:

1. JSON mit `nlohmann::json::parse` einlesen.
2. Den Modus mit passendem Namen suchen, sonst `std::runtime_error` werfen.
3. Durch die Kanäle des Modus gehen. Die Position zählt ab 1.
4. Für jeden Kanal in `availableChannels` nachschlagen, was er kann (`capability` oder `capabilities`), und mit `AttributeFor` in unser Attribut umwandeln.
5. Den `FixtureType` mit allen Feldern füllen.

**Fertig, wenn** die ersten drei Tests grün sind: `test_mini_tri_par_basics`, `test_mini_tri_par_channels` und `test_unknown_mode_throws`.

### Die Zuordnungstabelle (OFL-Typ zu Attribut)

Soweit ich das Format kenne, gelten diese Zuordnungen. Prüfe sie an echten Dateien, falls ein Name abweicht.

| OFL `type` der Fähigkeit | unser Attribut |
|---|---|
| `Intensity` | `ATTRIBUTE_INTENSITY` |
| `ColorIntensity` mit `color` Red, Green, Blue, White, Amber, UV, Cyan, Magenta, Yellow | `ATTRIBUTE_RED`, `_GREEN`, `_BLUE`, `_WHITE`, `_AMBER`, `_UV`, `_CYAN`, `_MAGENTA`, `_YELLOW` |
| `ColorIntensity` mit einer anderen Farbe (zum Beispiel Warm White, Lime, Indigo) | `ATTRIBUTE_OTHER` |
| `ColorTemperature` | `ATTRIBUTE_COLOR_TEMPERATURE` |
| `Pan`, `PanContinuous` | `ATTRIBUTE_PAN` |
| `Tilt`, `TiltContinuous` | `ATTRIBUTE_TILT` |
| `PanTiltSpeed` | `ATTRIBUTE_PAN_TILT_SPEED` |
| `ShutterStrobe`, `StrobeSpeed`, `StrobeDuration` | `ATTRIBUTE_STROBE` |
| `Zoom` / `Focus` / `Iris` / `Frost` | `ATTRIBUTE_ZOOM` / `_FOCUS` / `_IRIS` / `_FROST` |
| `Prism` / `PrismRotation` | `ATTRIBUTE_PRISM` / `_PRISM_ROTATION` |
| `WheelSlotRotation`, `WheelRotation` | `ATTRIBUTE_GOBO_ROTATION` |
| `BladeInsertion` / `BladeRotation`, `BladeSystemRotation` | `ATTRIBUTE_FRAMING_BLADE` / `_FRAMING_ROTATION` |
| `Effect`, `ColorPreset` | `ATTRIBUTE_EFFECT` |
| `EffectSpeed`, `EffectDuration`, `Speed` | `ATTRIBUTE_SPEED` |
| `SoundSensitivity` | `ATTRIBUTE_SOUND_SENSITIVITY` |
| `Maintenance` | `ATTRIBUTE_CONTROL` |
| `WheelSlot` | je nach Rad: Name enthält "gobo" → `ATTRIBUTE_GOBO`, "color" → `ATTRIBUTE_COLOR_WHEEL`, sonst `ATTRIBUTE_OTHER` |
| alles andere, auch ein Kanal, der nur aus `NoFunction` besteht | `ATTRIBUTE_OTHER` |

Hat ein Kanal mehrere Fähigkeiten (`capabilities`), gilt die **erste, die nicht `NoFunction` ist**. Beim Makro des Mini TRI-PAR ist das `ColorPreset`, also `ATTRIBUTE_EFFECT`.

### Hinweise zu nlohmann::json

```cpp
auto fixture = nlohmann::json::parse(json_text);   // wirft eine Ausnahme bei ungültigem JSON
std::string name = fixture.at("name").get<std::string>();   // Pflichtfeld, wirft bei Fehlen
if (channel.contains("highlightValue")) { ... }              // optionales Feld prüfen
if (channel["highlightValue"].is_number()) { ... }           // Typ prüfen
for (const auto& mode : fixture.at("modes")) { ... }          // Liste durchgehen
for (const auto& [key, value] : fixture.at("availableChannels").items()) { ... }   // Objekt durchgehen
entry.is_null(), entry.is_string(), entry.is_object()        // Art eines Elements
```

- `.at("x")` wirft bei einem fehlenden Feld, `["x"]` legt es stillschweigend an. Nimm für Pflichtfelder `.at`.
- JSON-Zahlen sind für nlohmann `double` oder `int`: `value.get<unsigned>()` wandelt um.
- Ein Fehler beim Lesen (`nlohmann::json::exception`) ist keine `std::runtime_error`. Fang ihn bei Bedarf ab und wirf einen eigenen Fehler mit einer verständlichen Meldung.

Zugriff auf die erzeugte Protobuf-Klasse (C++), nur das Wichtigste:

```cpp
FixtureType type;
type.set_name("...");                  // Textfeld setzen
ChannelDef* channel = type.add_channels();   // neues Element einer Liste anlegen, Zeiger darauf
channel->set_offset(1);
channel->set_attribute(ATTRIBUTE_RED);       // Enum-Wert
channel->set_precedence(PRECEDENCE_HTP);
```

Die Namen der Funktionen entstehen aus den Feldnamen im Schema: `library_key` wird `set_library_key`, `fine_offset` wird `set_fine_offset`.

### Zusatzaufgaben (wenn die ersten drei Tests grün sind)

Setze in `ofl_importer_test.cpp` den Schalter `kRunStretchTests` auf `true`:

1. **Feinkanäle (16 Bit):** Hat ein Kanal `fineChannelAliases`, ist der erste Alias ein Kanal mit höherer Genauigkeit. Er bekommt **keinen eigenen Eintrag**. Seine Position wird `fine_offset` des Hauptkanals. Die Feinkanäle müssen nicht direkt neben ihrem Hauptkanal stehen. Deshalb zwei Durchläufe: Erst die Positionen aller Namen merken (eine `std::map<std::string, unsigned>`), dann die Einträge bauen.
2. **Unbenutzte Kanäle:** Ein `null` in der Kanalliste ist ein Kanal ohne Funktion. Er zählt zur Kanalzahl und zu den Positionen, bekommt aber keinen Eintrag.

Nicht Teil der Aufgabe, aber der nächste Schritt für andere Tage: Matrix-Kanäle (`templateChannels`, der KLS-801), die aus Zellen bestehen. Dort wirft der Importer vorerst einen Fehler.

### Tag 2 ist fertig, wenn

- die ersten drei Tests grün sind (die Zusatzaufgaben sind freiwillig),
- `ctest` die vier Tests `smoke`, `fader_roundtrip` und `ofl_importer` (und der Kotlin-/Rust-Teil weiter) grün meldet,
- die `ci.yml` das neue Paket installiert und die **CI grün** ist.

---

## Befehle im Überblick

| Wo | Befehl | Wofür |
|---|---|---|
| Kotlin (Ordner `master`) | `.\gradlew test` (Windows), `./gradlew test` (Linux) | alle Tests |
| | `.\gradlew clean test` | sauber neu bauen und testen |
| Rust (Ordner `interpreter`) | `cargo test` | alle Tests |
| | `cargo test autosave` | nur Tests, deren Name "autosave" enthält |
| | `cargo test -- --nocapture` | auch `println!`-Ausgaben der Tests zeigen |
| | `cargo run -- info <datei>` | das Werkzeug mit Argumenten starten |
| | `cargo fmt` | Code einheitlich formatieren |
| C++ (im WSL- oder Linux-Terminal, Repo-Wurzel) | `cmake -S native -B build -G Ninja` | Projekt einrichten |
| | `cmake --build build` | bauen |
| | `ctest --test-dir build --output-on-failure` | alle Tests, bei einem Fehler mit Ausgabe |
| Git | `git status`, `git add`, `git commit -m "..."`, `git push` | Stand sichern |

In CLion erledigen der grüne Pfeil und die Run-Konfiguration das meiste davon. Das Terminal zeigt dir aber oft genauere Meldungen.

## Fehlermeldungen lesen

**Rust:** Die **erste Zeile** (`error[E0308]: mismatched types`) sagt, was los ist, die Zeile mit `-->` sagt, wo. Darunter zeigt Rust oft mit `help:` einen Vorschlag. Ignoriere zuerst alle `warning:`-Meldungen, sie sind keine Fehler. Behebe den **ersten** Fehler zuerst, die folgenden sind häufig Folgefehler.

**C++ und CMake:** Auch hier zählt der **erste** Fehler, danach folgen oft viele Folgefehler. Bei CMake steht die Ursache meist unter `CMake Error at ...` ganz oben ("Could not find ...": ein Paket fehlt).

**Gradle und Kotlin:** Suche in der langen Meldung nach `Caused by:` und der ersten Zeile mit deinem Dateinamen.

**Ein fehlgeschlagener Test:** Lies zuerst, **was erwartet** und **was erhalten** wurde. Meist steht die Ursache schon in der Differenz.

## Typische Fehler und was du tust

| Meldung | Ursache | Lösung |
|---|---|---|
| Rust: `cannot find function ... in this scope` | fehlendes `use` oder Schreibfehler | `use`-Zeile oben ergänzen |
| Rust: `mismatched types` | zwei verschiedene Zahlentypen | `as u64` oder `as usize` (Rust wandelt nie von selbst um) |
| Rust: `borrow of moved value` | ein Wert wurde schon abgegeben | stattdessen ausleihen (`&wert`) oder `.clone()` |
| Rust: `the ? operator can only be used in a function that returns Result` | `?` in einer Funktion ohne `Result` | den Rückgabetyp auf `Result<..., ...>` ändern |
| Rust: `not yet implemented` | ein `todo!()` wird noch ausgeführt | die Funktion schreiben |
| Rust: `Testdatei fehlt` | `showfile_example.bin` oder `.archx` nicht im Repo | Export in Kotlin bzw. Schritt 3 ausführen |
| C++: `fatal error: nlohmann/json.hpp: No such file` | Bibliothek nicht installiert | `sudo apt install nlohmann-json3-dev` und CMake neu laden |
| C++: `Could NOT find Protobuf` | Pakete fehlen | `sudo apt install libprotobuf-dev protobuf-compiler` |
| C++: `undefined reference to ...` | Bibliothek nicht gelinkt | in der `CMakeLists.txt` bei `target_link_libraries` ergänzen |
| Kotlin: Test rot, obwohl der Code stimmt | alter Übersetzungsstand | `.\gradlew clean test` |
| CLion: Änderungen an der `CMakeLists.txt` wirken nicht | CMake nicht neu geladen | Rechtsklick auf die Datei, *Reload CMake Project* |

## Debuggen ohne Debugger

- **Rust:** `println!("{:?}", wert);` gibt fast jeden Wert aus. `dbg!(wert)` zeigt zusätzlich Datei und Zeile. Bei Tests mit `cargo test -- --nocapture`.
- **Kotlin:** `println(wert)`.
- **C++:** `std::cerr << wert << "\n";`.

Wenn etwas nicht tut, was du erwartest, gib den Zwischenwert **vor** der Stelle aus, an der es schiefgeht. So findest du in Minuten, ob die Eingabe oder die Berechnung falsch ist.

## Git und CI

- **Committe nach jedem grünen Teil**, nicht erst am Tagesende. Kleine Schritte machen Fehler leichter auffindbar.
- **Pushe nur, wenn alles grün ist,** sonst wird die CI rot. Willst du Zwischenstände sichern, lege einen Zweig an (`git switch -c tag-1`).
- Prüfe nach dem Push im Reiter **Actions** auf GitHub, ob alle drei Jobs (`kotlin`, `cpp`, `rust`) grün sind.
- Bei einem roten Job: Öffne ihn, klappe den roten Schritt auf, lies die letzten Zeilen.

## Wenn du hängst

- **Ein Problem länger als eine Stunde:** Schreib es mir, statt weiter zu raten. Das Meiste ist schnell geklärt, wenn man die Meldung sieht.
- **Schick mir immer:** (1) die **erste Zeile** der Fehlermeldung mit den zwei, drei Zeilen darunter, (2) deine Funktion, in der es hakt, (3) was du schon probiert hast.
- **Zusatzaufgaben** darfst du auslassen. Wichtiger ist, dass die Pflichttests grün sind und alles committet ist.
- **Verstehst du eine Zeile Code nicht:** Frag nach. Ich erkläre sie lieber einmal zu oft.

## Kleines Wörterbuch

| Begriff | Bedeutung |
|---|---|
| **Test grün/rot** | bestanden / fehlgeschlagen |
| **Golden file** | eine feste Beispieldatei im Repo, gegen die mehrere Programme prüfen |
| **Vertragstest** | ein Test, der prüft, dass zwei Programme dasselbe Format meinen |
| **`Result` (Rust)** | Rückgabewert, der Erfolg (`Ok`) oder Fehler (`Err`) enthält |
| **`Option` (Rust)** | Wert, der da sein kann (`Some`) oder fehlt (`None`) |
| **`?` (Rust)** | bei einem Fehler sofort aus der Funktion zurückgeben |
| **Ausleihen (`&`)** | Zugriff auf einen Wert, ohne ihn abzugeben |
| **Modus (OFL)** | eine Belegungsvariante eines Scheinwerfers, bei uns ein eigener `FixtureType` |
| **Feinkanal** | zweiter Kanal für 16 Bit Genauigkeit, `fine_offset` im Schema |
