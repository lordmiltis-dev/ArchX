#include "dmx/include/link/dmx_frame_proto.h"
#include "cmake-build-debug/dmx_link.pb.h"
#include "dmx/include/link/todo.h"

// TODO: schema/dmx_link.proto in den C++-Build einbinden (protoc-Aufruf in
//       CMake, Ausgabe in den Build-Ordner, nicht ins Repo) und hier
//       #include "dmx_link.pb.h" aktivieren.

namespace archx::dmx::link {

std::optional<DmxFrame> parseDmxFrame(const std::uint8_t* payload, std::size_t size) {


  if (payload == nullptr && size != 0)
  {
    return std::nullopt;
  }

  if (size > static_cast<std::size_t>(std::numeric_limits<int>::max()))
  {
    return std::nullopt;
  }

  archx::dmxlink::DmxFrame msg;
  if (!msg.ParseFromArray(payload, static_cast<int>(size)))
  {
    return std::nullopt;
  }

  DmxFrame frame;
  frame.universe = msg.universe();

  const std::string& bytes = msg.data();
  frame.data.assign(bytes.begin(), bytes.end());

  return frame;
}

std::vector<std::uint8_t> serializeDmxFrame(const DmxFrame& frame) {
  archx::dmxlink::DmxFrame msg;

  msg.set_universe(frame.universe);
  msg.set_data(std::string(frame.data.begin(), frame.data.end()));

  const std::size_t size = msg.ByteSizeLong();
  if (size > static_cast<std::size_t>(std::numeric_limits<int>::max()))
  {
    throw std::length_error("DmxFrame::serializeDmxFrame: size too large");
  }

  std::vector<std::uint8_t> out(size);

  if (!msg.SerializeToArray(out.data(), static_cast<int>(size)))
  {
    throw std::runtime_error("serializeDmxFrame: failed to serialize DmxFrame");
  }

  return out;
}

}  // namespace archx::dmx::link
