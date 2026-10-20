# ADR 0003: Schema-Format für Nachrichten und .archx

- **Status:** Vorgeschlagen, wird mit dem Phase-0-Test (`FaderMoved` von Kotlin nach C++) bestätigt
- **Datum:** 2026-10-19

## Kontext

Kotlin, C++ und Rust müssen Daten austauschen: Events von den Pulten, Status-Frames, Snapshots für den Interpreter und das Dateiformat `.archx`. Ohne gemeinsames Format müsste das Byte-Layout in jeder Sprache von Hand gepflegt werden, was fehleranfällig ist.

## Entscheidung

**Protocol Buffers (proto3)** beschreiben alle Nachrichten und das `.archx`-Schema.

- Die Schema-Dateien liegen in `schema/`.
- Der Code für Kotlin, C++ und Rust wird beim Build daraus **generiert** und nicht ins Repository committet.

## Begründung

- Einfach zu lernen, sehr gut dokumentiert und weit verbreitet.
- Gute Unterstützung für alle drei Sprachen (z.B. `protobuf-kotlin` oder Wire, `libprotobuf`, `prost`).
- Die Datenmengen sind klein (einige tausend kleine Events pro Sekunde, Frames mit 44 Hz). Das Parsing ist dafür schnell genug.

## Ausnahmen

DMX-Universen (512 Bytes) und andere reine Hot-Path-Daten werden als rohe Bytes oder feste Struktur übertragen, ohne Schema-Overhead.

## Verworfene Alternativen

- **FlatBuffers:** Liest ohne Parsing direkt aus dem Puffer und ist noch schneller, aber umständlicher in der Handhabung. Wird nur für einen einzelnen Pfad in Betracht gezogen, wenn eine Messung zeigt, dass Protobuf dort zu langsam ist.
- **Cap'n Proto:** Ähnliche Vorteile wie FlatBuffers, aber kleineres Ökosystem.

## Konsequenzen

- Schemaänderungen erfolgen nur versioniert. Feldnummern werden nie wiederverwendet.
- Die Wahl des Transports (Shared-Memory-Ringpuffer, FFM API, ZeroMQ) wird in einer eigenen ADR festgelegt.
- Der Phase-0-Test bestätigt oder widerlegt diese Entscheidung.
