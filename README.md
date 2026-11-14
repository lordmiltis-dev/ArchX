# ArchX

> **🚧 WORK IN PROGRESS — ArchX is not finished.** This project is under active development; features, interfaces, and documentation may change, and it is not yet ready for production use.

<img src="docs/img/ArchX_Logo_ohne_Hintergrund.png" width="200" height="200">

An lighting software framework designed to work with modern and legacy digital lighting consoles through expansion slots.

ArchX is built around a central Master state model and specialized Submasters that handle hardware, protocols, rendering, UI, and file import/export. This architecture keeps the system modular while ensuring every component works from the same show state.

## Key Features

- Modular architecture with a central Master and pluggable Submasters
- Support for modern and legacy digital lighting consoles via expansion slots and hardware driver extensions
- USB console integration with automatic driver detection and bidirectional data tunneling
- DMX and Art-Net output with optional RDM support
- Pixel mapping and DyLOS-compatible value generation from the Master 2D list
- Consistent show state in memory with GUI updates mirrored from the Master
- Save and autosave of showfiles in the `.archx` format
- Import and export support through extension-based showfile integrations
- Extension ecosystem for hardware, UI, fixtures, outputs, importers, exporters, and pixel mapping
- Safe save semantics with atomic replace operations and recovery from backup copies
- Automatic rotation of autosave files to avoid data loss during crashes or outages

## Architecture Overview

The project is split into a Master, which keeps the full showfile state in RAM, and multiple specialized Submasters for hardware, interfaces, and processing. The system is designed so that hardware-specific logic, file formats, and fixture libraries stay isolated behind extensions.

<img src="docs/img/ArchX-Architecture.png" alt="ArchX architecture overview">

### Main Components

| Component | Language | Responsibility |
| --- | --- | --- |
| Master | Kotlin | Holds the showfile state in RAM and manages all submasters |
| GUI-Submaster | Kotlin | User interface, configurable via UI extensions |
| Divider / Assembler | C++ | Bundles incoming events into status frames and distributes commands to USB and DMX submasters |
| USB-Submaster | C++ | Detects attached consoles, loads the correct driver extension, and tunnels data bidirectionally |
| DMX / Art-Net-Submaster | C++ | Sends DMX frames in a 44 Hz loop and optionally supports RDM |
| Pixel-Mapping-Submaster | C++ | Generates pixel values from the Master 2D list in a DyLOS-compatible way |
| Fixtures-Library-Submaster | C++ | Manages fixture profiles, primarily the Onyx library, via extensions |
| Savefile-Interpreter | Rust | Imports `.archx` showfiles, periodically saves the Master state, and exports files via extensions |

## Signal Flow

### Input: Console to interface
Pult → USB-Submaster (driver extension) → Divider/Assembler → Master → GUI-Submaster.

### Output: DMX and Art-Net
Master → Divider → DMX-Submaster → optional RDM extension → DMX Out or Art-Net Out.

### Pixel mapping
The Master provides a 2D list to the Pixel-Mapping-Submaster, which calculates the values and returns them to the Master where they are treated like any other value source.

### Feedback to the console
The Master → Divider → USB-Submaster → driver extension → console returns LED, motor fader, and other feedback data.

### Save and load
The Savefile-Interpreter reads the Master state periodically and writes it as a `.archx` showfile. Foreign showfiles can be imported through importer extensions.

## Core Design Decisions

### Multi-language runtime
ArchX deliberately uses different languages for different responsibilities:

- Kotlin for the Master and UI layer
- C++ for low-latency hardware and native submasters
- Rust for the savefile interpreter and import/export logic

This separation keeps the runtime responsive while limiting complexity in the time-critical hardware paths.

### Shared schema for communication
The project uses Protocol Buffers (`proto3`) for messages, status frames, and the `.archx` structure. The schema lives under `schema/`, and code for Kotlin, C++ and Rust is generated during build time.

This gives ArchX a stable cross-language contract for show state, events, and file contents without hard-coding binary layouts in each component.

### `.archx` file format
The core showfile format is a binary file with a fixed header and embedded Protobuf payload:

- Magic identifier `ARCHX`
- Format version
- Length of the protobuf payload
- Protobuf block with show definitions

This design keeps files compact and fast to read and write while supporting version checks and safe recovery.

### Media handling and save safety
Media such as images, videos, and pixel mapping content are stored outside the showfile in a media folder beside it. The `.archx` file stores references, hashes, and relative paths rather than embedding large assets directly.

Save operations are implemented safely by writing to a temporary file and renaming it only after completion. This reduces the chance of corrupt files during crashes or power loss.

### Automatic backups and recovery
ArchX uses automated backup rotation instead of a journal for the first phase:

- Full show backups are written regularly into `autosave/`
- Files are named with a running counter and kept in rotation
- If the main showfile is missing or unreadable, the newest valid backup is used
- Backups are only written when the show state has changed

This is intentionally designed to minimize data loss during live operation without adding the complexity of a full change journal too early.

### Local data layout
Project data is stored in OS-specific user directories rather than the repository itself:

- Linux: `~/.local/share/archx/`, `~/.config/archx/`, `~/.cache/archx/`
- Windows: `%APPDATA%\ArchX\` and `%LOCALAPPDATA%\ArchX\`

The data layout separates libraries, showfiles, autosaves, and caches while keeping settings isolated from the showfiles themselves.

## Extension Types

- Hardware drivers for consoles
- Output modules (DMX, RDM)
- Importers and exporters for showfiles
- Fixture library sources
- Pixel mapping libraries, video, and image importers
- UI extensions

## Project Structure

- `master/` – Kotlin-based master application and core show state logic
- `interpreter/` – Rust-based savefile interpreter and persistence layer
- `native/` – C++ submasters and native runtime components
- `schema/` – Protocol and showfile schema definitions
- `docs/` – project documentation, architecture, and ADRs

## Documentation

- [Architecture overview](docs/architecture.md)
- [ADR 0001: Programming languages](docs/adr/001-sprachen.md)
- [ADR 0002: License](docs/adr/002-lizenz.md)
- [ADR 0003: Schema format](docs/adr/003-ipc-format.md)
- [ADR 0005: `.archx` file format](docs/adr/005-archx-dateiformat.md)
- [ADR 0006: Storage locations](docs/adr/0006-speicherorte.md)
- [ADR 0007: Autosave strategy](docs/adr/0007-autosave.md)

## Technology Stack

- Kotlin – Master and UI layer
- Rust – Savefile interpreter and file handling
- C++ – Submasters, hardware interaction, DMX, and native processing
- CMake – Native build integration
- Protobuf – Cross-language message and file schema

## Status

ArchX is designed as a modular lighting framework that aims to bridge older and newer console ecosystems through extensible hardware and software interfaces.
