// Defines the thread-safe boundary for applying received DMX frames to output.

#pragma once
#include <array>
#include <cstddef>
#include <cstdint>

namespace archx::dmx::link {

inline constexpr std::size_t kUniverseSize = 512;
inline constexpr std::uint32_t kMaxUniverse = 32767;  // Art-Net: 15 Bit
using UniverseData = std::array<std::uint8_t, kUniverseSize>;

// Destination for received frames. An adapter can implement this interface
// to update the latest frame per universe in the output loop.
//
// Threading: LinkServer calls setFrame() on its network thread while the output
// loop reads from another thread. Implementations must therefore synchronize
// access to stored frame data.
class FrameSink {
 public:
  virtual ~FrameSink() = default;
  virtual void setFrame(std::uint16_t universe, const UniverseData& data) = 0;
};

}  // namespace archx::dmx::link
