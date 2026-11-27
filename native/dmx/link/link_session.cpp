// Implements per-connection frame decoding, payload validation, and sink updates.

#include "dmx/include/link/link_session.h"

#include <algorithm>
#include <utility>

#include "dmx/include/link/todo.h"

namespace archx::dmx::link {

LinkSession::LinkSession(FrameSink& sink, PayloadParser parser)
    : sink_(sink), parser_(std::move(parser)) {}

bool LinkSession::onBytes(const std::uint8_t* data, std::size_t size) {
  if (decoder_.failed()) {
    return false;
  }

  std::vector<std::vector<std::uint8_t>> payloads;
  const bool ok = decoder_.feed(data, size, payloads);

  for (const auto& payload : payloads) {
    handlePayload(payload);
  }

  if (!ok) {
    ++stats_.framingErrors;
    return false;
  }
  return true;
}

void LinkSession::handlePayload(const std::vector<std::uint8_t>& payload) {
  const std::optional<DmxFrame> frame = parser_(payload.data(), payload.size());

  if (!frame.has_value()) {
    ++stats_.framesMalformed;
    return;
  }

  if (frame->universe > kMaxUniverse || frame->data.size() != kUniverseSize) {
    ++stats_.framesInvalid;
    return;
  }

  UniverseData data;
  std::copy(frame->data.begin(), frame->data.end(), data.begin());

  sink_.setFrame(static_cast<std::uint16_t>(frame->universe), data);
  ++stats_.framesApplied;
}

void LinkSession::reset() {
  decoder_.reset();
}

}  // namespace archx::dmx::link
