#pragma once
#include <cstddef>
#include <cstdint>
#include <functional>
#include <optional>
#include <vector>

namespace archx::dmx::link {

// Inhalt der Protobuf-Nachricht archx.dmxlink.DmxFrame (schema/dmx_link.proto),
// unabhängig vom generierten Code. Es wird hier NICHT geprüft, ob universe im
// Bereich liegt oder data genau 512 Bytes hat: das macht LinkSession.
struct DmxFrame {
  std::uint32_t universe = 0;
  std::vector<std::uint8_t> data;
};

using PayloadParser =
    std::function<std::optional<DmxFrame>(const std::uint8_t*, std::size_t)>;

// Verwendet den aus schema/dmx_link.proto GENERIERTEN Code (ADR 0003: nichts
// von Hand nachbauen).
//  - nullopt bei kaputten Bytes (abgeschnitten, ungültiger Tag, ...).
//  - Unbekannte Felder werden ignoriert, fehlende Felder sind Standardwerte.
std::optional<DmxFrame> parseDmxFrame(const std::uint8_t* payload, std::size_t size);

// Kanonische proto3-Serialisierung (Standardwerte werden nicht geschrieben).
std::vector<std::uint8_t> serializeDmxFrame(const DmxFrame& frame);

}  // namespace archx::dmx::link
