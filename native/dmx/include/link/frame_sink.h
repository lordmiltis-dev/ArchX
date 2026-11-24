#pragma once
#include <array>
#include <cstddef>
#include <cstdint>

namespace archx::dmx::link {

inline constexpr std::size_t kUniverseSize = 512;
inline constexpr std::uint32_t kMaxUniverse = 32767;  // Art-Net: 15 Bit
using UniverseData = std::array<std::uint8_t, kUniverseSize>;

// Ziel der empfangenen Rahmen. Das ist die Nahtstelle zu Schritt 2.3:
// TODO: Eine kleine Adapterklasse schreiben, die FrameSink implementiert und
//       den "letzten Rahmen pro Universum" der 44-Hz-Sendeschleife setzt.
//
// ACHTUNG Threads: setFrame() wird vom Netzwerk-Thread des LinkServer
// aufgerufen, die Sendeschleife liest aus einem anderen Thread. Der Speicher
// hinter dem Adapter muss also thread-sicher sein (Mutex oder Doppelpuffer).
class FrameSink {
 public:
  virtual ~FrameSink() = default;
  virtual void setFrame(std::uint16_t universe, const UniverseData& data) = 0;
};

}  // namespace archx::dmx::link
