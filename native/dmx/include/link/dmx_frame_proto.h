// Declares the DMX frame model and Protobuf parsing and serialization interface.

#pragma once
#include <cstddef>
#include <cstdint>
#include <functional>
#include <optional>
#include <vector>

namespace archx::dmx::link {

// In-memory representation of archx.dmxlink.DmxFrame (schema/dmx_link.proto),
// independent of generated code. Range and 512-byte data validation is handled
// by LinkSession.
struct DmxFrame {
  std::uint32_t universe = 0;
  std::vector<std::uint8_t> data;
};

using PayloadParser =
    std::function<std::optional<DmxFrame>(const std::uint8_t*, std::size_t)>;

// Uses code generated from schema/dmx_link.proto (ADR 0003; do not reimplement
// the wire format by hand). Returns nullopt for malformed payloads. Unknown
// fields are ignored, and missing fields use their default values.
std::optional<DmxFrame> parseDmxFrame(const std::uint8_t* payload, std::size_t size);

// Canonical proto3 serialization; default-valued fields are omitted.
std::vector<std::uint8_t> serializeDmxFrame(const DmxFrame& frame);

}  // namespace archx::dmx::link
