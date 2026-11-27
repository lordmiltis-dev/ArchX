// Declares the length-prefixed TCP frame encoder and incremental decoder.

#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace archx::dmx::link {

// Wire format: [4-byte unsigned little-endian length][payload].
// Valid lengths are 1..kMaxPayloadSize; other lengths are protocol errors.
inline constexpr std::size_t kHeaderSize = 4;
inline constexpr std::uint32_t kMaxPayloadSize = 65536;

// Builds a header and payload. Throws std::invalid_argument if size is zero or
// exceeds kMaxPayloadSize.
std::vector<std::uint8_t> encodeFrame(const std::uint8_t* payload, std::size_t size);
inline std::vector<std::uint8_t> encodeFrame(const std::vector<std::uint8_t>& payload) {
  return encodeFrame(payload.data(), payload.size());
}

// Reassembles frames from a TCP byte stream. A frame may span multiple feed()
// calls, and one call may contain multiple frames.
class FrameDecoder {
 public:
  // Appends complete payloads to `out`, preserving its existing contents.
  // Returns false for a protocol error (zero or oversized length). Frames
  // completed before the error remain in `out`. Invalid lengths are rejected
  // as soon as the header is complete, before allocating the payload buffer.
  // After an error, failed() remains true and later calls do not consume input
  // or modify `out`; only reset() clears the error state.
  bool feed(const std::uint8_t* data, std::size_t size,
            std::vector<std::vector<std::uint8_t>>& out);

  bool failed() const { return failed_; }

  // Buffered bytes of an incomplete frame, including any header bytes. Returns
  // zero when positioned at a frame boundary.
  std::size_t bufferedBytes() const;

  // Discards partial data and clears the error state for a new connection.
  void reset();

 private:
  bool failed_ = false;
  std::uint8_t header_[kHeaderSize] = {};
  std::size_t headerBytes_ = 0;
  std::size_t expected_ = 0;
  bool inPayload_ = false;
  std::vector<std::uint8_t> payload_;
};

}  // namespace archx::dmx::link
