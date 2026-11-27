// Declares the per-connection decoder, payload validation, and frame statistics.

#pragma once
#include <cstddef>
#include <cstdint>

#include "dmx/include/link/dmx_frame_proto.h"
#include "dmx/include/link/frame_codec.h"
#include "dmx/include/link/frame_sink.h"

namespace archx::dmx::link {

struct LinkStats {
  std::uint64_t framesApplied = 0;    // Forwarded to the sink.
  std::uint64_t framesMalformed = 0;  // Payload could not be parsed as DmxFrame.
  std::uint64_t framesInvalid = 0;    // Parsed, but universe or data size is invalid.
  std::uint64_t framingErrors = 0;    // Stream protocol errors, counted once each.
};

// Per-connection logic without sockets, allowing network-independent tests:
// bytes -> frame -> DmxFrame -> validation -> sink.
//
// Malformed or invalid payloads are counted and skipped without closing the
// connection. A framing error makes onBytes() return false; complete frames
// before the error are still applied, and later bytes are ignored until reset().
// Frame order is preserved.
class LinkSession {
 public:
  explicit LinkSession(FrameSink& sink, PayloadParser parser = parseDmxFrame);

  bool onBytes(const std::uint8_t* data, std::size_t size);

  // Resets partial data and framing errors for a new connection. Statistics
  // are preserved and the sink is left untouched.
  void reset();

  const LinkStats& stats() const { return stats_; }

 private:
  void handlePayload(const std::vector<std::uint8_t>& payload);

  FrameSink& sink_;
  PayloadParser parser_;
  FrameDecoder decoder_;
  LinkStats stats_;
};

}  // namespace archx::dmx::link
