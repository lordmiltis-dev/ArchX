# ArchX

> **🚧 WORK IN PROGRESS — ArchX is not finished.** Features, interfaces, and documentation may change during active development. The project is not yet ready for production use.
>
> **AI disclosure:** AI tools may be used to assist with development and documentation. Architecture and implementation direction are defined by the project owner, and third-party contributions are reviewed before incorporation.

<img src="docs/img/ArchX_Logo_ohne_Hintergrund.png" width="200" height="200">

A planned lighting software framework for modern and legacy digital lighting consoles. The goal is to connect different console ecosystems through expansion slots and hardware driver extensions.

ArchX is intended to use a central Master state model for show data and specialized Submasters for hardware, protocols, rendering, the interface, and file formats. The modular boundaries are development goals and still require implementation and validation.

## Planned Features

The following points describe the current development direction and planned goals. Not every feature is implemented yet:

- A modular architecture with a central Master and pluggable Submasters
- Support for modern and legacy digital lighting consoles through expansion slots and hardware driver extensions
- Planned USB console integration with automatic driver detection and bidirectional data transfer
- Planned DMX and Art-Net output with optional RDM support
- Planned pixel mapping and DyLOS-compatible value generation from a future Master 2D list
- Planned in-memory show state consistency with interface updates driven by the Master
- Planned saving and autosaving of showfiles in `.archx` format
- Import and export support through extensions
- A planned extension ecosystem for hardware, interface components, fixtures, outputs, importers, exporters, and pixel mapping
- Planned safe-save behavior using temporary files and atomic replacement
- Planned autosave rotation to reduce data loss during crashes or outages

## Architecture Overview

The planned project is divided into a Master for show state and multiple specialized Submasters for hardware, interfaces, and processing. This structure is intended to allow hardware and software components to remain modular and replaceable.

<img src="docs/img/ArchX-Architecture.png" alt="Planned architecture overview">

### Main Components

| Component | Language | Planned Responsibility |
| --- | --- | --- |
| Master | Kotlin | Manage show state in RAM and coordinate Submasters |
| GUI-Submaster | Kotlin | User interface and UI extensions |
| Divider / Assembler | C++ | Bundle incoming events and distribute commands to USB and DMX components |
| USB-Submaster | C++ | Detect consoles, load suitable driver extensions, and forward data |
| DMX / Art-Net-Submaster | C++ | Planned periodic DMX output and optional RDM support |
| Pixel-Mapping-Submaster | C++ | Planned value generation from the Master 2D list |
| Fixtures-Library-Submaster | C++ | Manage fixture profiles through extensions |
| Savefile-Interpreter | Rust | Planned loading, periodic saving, and exporting of showfiles |

## Planned Data Flow

The flows below describe architectural goals. They do not guarantee that every step is currently implemented.

### Input: Console to Interface

Console → USB-Submaster (driver extension) → Divider/Assembler → Master → GUI-Submaster

### Output: DMX and Art-Net

Master → Divider → DMX-Submaster → optional RDM extension → DMX output or Art-Net output

### Pixel Mapping

The Master is intended to provide a 2D value list to the Pixel-Mapping-Submaster. The submaster would calculate values and return them to the Master for use alongside other value sources.

### Feedback to the Console

The planned feedback path is Master → Divider → USB-Submaster → driver extension → console for LEDs, motor faders, and other feedback data.

### Saving and Loading

The Savefile-Interpreter is intended to read the Master state periodically and write `.archx` showfiles. Other showfile formats are planned to be supported through importer extensions.

## Core Design Decisions

### Multi-language Runtime

The planned implementation uses several languages for separate responsibilities:

- Kotlin for the Master and interface
- C++ for hardware-facing code and native Submasters
- Rust for savefile processing and persistence

This separation is intended to keep time-critical hardware paths small and maintainable. The language boundaries and communication model are still subject to development and validation.

### Shared Communication Schema

Protocol Buffers (`proto3`) are planned for messages, status frames, and the `.archx` structure. Schemas will live under `schema/`; Kotlin, C++, and Rust code are intended to be generated during builds.

This should provide a shared contract for show state, events, and file contents without hard-coding binary layouts in every component.

### Planned `.archx` File Format

The planned showfile format is a binary file with a fixed header and embedded Protocol Buffers payload:

- Magic identifier `ARCHX`
- Format version
- Length of the Protocol Buffers payload
- Protobuf block containing show definitions

This format is intended to support compact files, version checks, and recovery after failures.

### Media Handling and Save Safety

Media such as images, videos, and pixel-mapping content is planned to be stored outside the showfile in a neighboring media folder. The `.archx` file would store references, hashes, and relative paths.

For safer writes, the implementation is intended to write to a temporary file first and replace the final file only after completion. This should reduce the risk of damaged files after crashes or power loss.

### Planned Backups and Recovery

A rotating backup strategy is planned for the first development phase:

- Full show backups may be written regularly to `autosave/`
- Files may use a running counter and remain in rotation
- If the main showfile is missing or unreadable, the newest valid backup may be used
- Backups may only be written when show state changes

This strategy is intended to reduce data loss during live operation without introducing a full change journal during the early development phase.

### Planned Local Data Layout

Project data is planned to be stored in OS-specific user directories:

- Linux: `~/.local/share/archx/`, `~/.config/archx/`, `~/.cache/archx/`
- Windows: `%APPDATA%\ArchX\` and `%LOCALAPPDATA%\ArchX\`

The layout is intended to separate libraries, showfiles, autosaves, and caches.

## Planned Extension Types

- Hardware drivers for consoles
- Output modules for DMX and RDM
- Importers and exporters for showfiles
- Fixture libraries
- Pixel-mapping libraries and media importers
- Interface extensions

## Project Structure

- `master/` – Kotlin-based Master application and planned show state
- `interpreter/` – Rust-based savefile interpreter and persistence layer
- `native/` – C++ Submasters and native runtime components
- `schema/` – Protocol and showfile schemas
- `docs/` – Project documentation, architecture, and ADRs

## Roadmap

ArchX is still under active development. The following are broad development goals and not promises of released functionality:

1. Validate cross-language schemas and communication paths between Kotlin, C++, and Rust.
2. Develop the core Master and showfile handling, including safe saving and autosave/recovery.
3. Develop extension interfaces for console hardware, DMX/Art-Net, fixture libraries, pixel mapping, and the interface.
4. Add and test import/export integrations and media handling.
5. Improve documentation, automated tests, and compatibility coverage as the framework matures.

## Contributing

Contributions are welcome, especially extension work. Before developing an extension, create your own branch instead of committing directly to a shared branch. Open a pull request when the work is ready for review.

The project owner or a designated maintainer reviews contributions before they are incorporated into `main` or another shared branch. AI tools may assist development, but they do not replace review of unverified or uncontrolled changes.

## Documentation

- [Architecture overview](docs/architecture.md)
- [ADR 0001: Programming languages](docs/adr/001-sprachen.md)
- [ADR 0002: License](docs/adr/002-lizenz.md)
- [ADR 0003: Schema format](docs/adr/003-ipc-format.md)
- [ADR 0005: `.archx` file format](docs/adr/005-archx-dateiformat.md)
- [ADR 0006: Storage locations](docs/adr/0006-speicherorte.md)
- [ADR 0007: Autosave strategy](docs/adr/0007-autosave.md)

## Technology Stack

- Kotlin – planned for the Master and interface
- Rust – planned for the savefile interpreter and file handling
- C++ – planned for Submasters, hardware interaction, DMX, and native processing
- CMake – native build integration
- Protocol Buffers – planned cross-language messages and file structures

## License

ArchX is licensed under the GNU General Public License v3.0. See [LICENSE.md](LICENSE.md). GPLv3 permits redistribution, including for a fee, subject to its terms.

## Status

ArchX is planned as a modular lighting framework intended to bridge older and newer console ecosystems through extensible hardware and software interfaces. The project is unfinished, incomplete, and not approved for production use.
