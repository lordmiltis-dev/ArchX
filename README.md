# ArchX

> **🚧 Work in progress.** ArchX is an experimental, modular lighting-software framework. It is not production-ready, and APIs and file formats may change.

<img src="docs/img/ArchX_Logo_ohne_Hintergrund.png" width="200" height="200" alt="ArchX logo">

ArchX is being developed as a cross-platform foundation for lighting-control software. The long-term goal is to connect modern and legacy lighting consoles through extensible hardware, protocol, fixture, import/export, and UI components.

The repository currently contains working building blocks for the shared show-file schema, Kotlin/Protobuf integration, a Rust `.archx` container tool, native C++ DMX/Art-Net code, and an OFL fixture importer. The complete Master/Submaster runtime and GUI described by the architecture are not implemented yet.

## Current status

### Implemented

- Protocol Buffer schemas for fader events and show files in [`schema/`](schema/).
- Kotlin/JVM build using Gradle, Kotlin 2.3, Java 21, and generated Kotlin Protobuf bindings in [`master/`](master/).
- A Kotlin example that creates and serializes a `FaderMoved` message.
- Rust save-file tooling in [`interpreter/`](interpreter/) with:
  - `pack` — wrap raw Protobuf show bytes in an `.archx` container;
  - `unpack` — extract Protobuf bytes from an `.archx` file;
  - `info` — display basic show-file information.
- Rust tests covering Protobuf compatibility, pack/unpack round trips, validation, and show inspection.
- Native C++20 components in [`native/`](native/) for:
  - shared generated Protobuf code;
  - DMX output;
  - Art-Net packet handling;
  - OFL fixture importing;
  - Art-Net and DMX command-line tools.
- Native tests for the schema, fader data, OFL importer, Art-Net packets, and DMX output.
- Continuous integration for Kotlin, C++, and Rust through [GitHub Actions](.github/workflows/ci.yml).

### Not implemented yet

The following are architectural goals rather than currently available features:

- The central Master show-state runtime and its Submaster communication layer.
- A GUI or Compose Multiplatform interface.
- USB console discovery, console drivers, and bidirectional console feedback.
- A complete DMX/Art-Net output pipeline connected to the Master.
- RDM support, pixel mapping, and DyLOS-compatible value generation.
- Extension loading and the planned importer/exporter, fixture-library, hardware, and UI extension ecosystem.
- Automatic autosave, recovery, media management, and production-ready show-file workflows.

See [`docs/architecture.md`](docs/architecture.md) and the [architecture decision records](docs/adr/) for the intended design. Those documents describe the target architecture and may include features that are still planned.

## Repository structure

| Path | Contents |
| --- | --- |
| [`master/`](master/) | Kotlin/JVM example and shared-schema integration |
| [`interpreter/`](interpreter/) | Rust `.archx` pack, unpack, and inspection tool |
| [`native/`](native/) | C++20 schema, DMX, Art-Net, fixture, tool, and test code |
| [`schema/`](schema/) | Protocol Buffer definitions and test data |
| [`extensions/`](extensions/) | Reserved extension directories; most are placeholders currently |
| [`gui/`](gui/) | Reserved GUI directory; implementation has not started |
| [`docs/`](docs/) | Architecture documentation, ADRs, and project images |

## Building and testing

### Kotlin

Requirements: JDK 21.

```bash
cd master
./gradlew build
```

The Gradle build generates Kotlin bindings from the `.proto` files in `schema/`.

### Rust interpreter

Requirements: Rust 1.85 or newer. `protoc` is supplied through the vendored build dependency.

```bash
cd interpreter
cargo build
cargo test
```

To use the interpreter after building:

```bash
cargo run -- pack ../schema/testdata/showfile_example.bin show.archx
cargo run -- info show.archx
cargo run -- unpack show.archx show.bin
```

The input to `pack` must contain a serialized `Showfile` Protobuf message. The resulting `.archx` file is the current binary show-file container; it should be considered experimental.

### Native C++

Requirements: CMake 3.20 or newer, a C++20 compiler, Protobuf development tools, `nlohmann-json`, and pthreads.

```bash
cmake -S native -B build -G Ninja
cmake --build build
ctest --test-dir build --output-on-failure
```

The Art-Net monitor and sender are development tools and are currently intended for Linux and WSL environments.

### CI

The CI workflow runs the Kotlin, C++, and Rust builds and tests on pushes and pull requests. See [`.github/workflows/ci.yml`](.github/workflows/ci.yml) for the exact commands and dependencies.

## Architecture direction

The intended architecture is a Kotlin Master that owns the in-memory show state and specialized native or language-specific Submasters that handle hardware, protocols, rendering, interfaces, and file formats:

```text
Console / input → hardware driver → native processing → Master → GUI / output
Master → DMX / Art-Net output
Master ↔ save-file interpreter → .archx show file
```

This diagram represents the target design, not a claim that the full runtime currently exists.

## Documentation

- [Architecture overview](docs/architecture.md)
- [Programming languages ADR](docs/adr/001-sprachen.md)
- [License ADR](docs/adr/002-lizenz.md)
- [IPC and schema ADR](docs/adr/003-ipc-format.md)
- [`.archx` file-format ADR](docs/adr/005-archx-dateiformat.md)
- [Storage locations ADR](docs/adr/0006-speicherorte.md)
- [Autosave ADR](docs/adr/0007-autosave.md)
- [Contributing guide](CONTRIBUTING.md)

## Contributing

Contributions are welcome, especially improvements to the native protocol components, schema compatibility, fixture importers, and tests. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

## License

ArchX is licensed under the [GNU General Public License v3.0](LICENSE.md).
