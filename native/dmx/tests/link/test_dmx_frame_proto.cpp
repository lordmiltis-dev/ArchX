// Tests Protobuf frame parsing and serialization against generated Protobuf code.
// The golden vectors are generated independently by tools/gen_dmx_link_vectors.py.
#include "dmx/include/link/dmx_frame_proto.h"
#include "test_support.h"

using namespace archx::dmx::link;
using namespace archx::test;

namespace {
std::optional<DmxFrame> parse(const Bytes& b) { return parseDmxFrame(b.data(), b.size()); }
}  // namespace

TEST(serialize_matches_golden_vectors) {
  for (auto& v : vectors().protos) {
    DmxFrame f{v.universe, Bytes(512, v.fill)};
    Bytes got = serializeDmxFrame(f);
    if (!(got == v.wire)) ARCHX_FAIL_AT("Vektor " + v.name + ": " + toHex(got) + " != " + toHex(v.wire));
  }
}
TEST(parse_matches_golden_vectors) {
  for (auto& v : vectors().protos) {
    auto f = parse(v.wire);
    if (!f) ARCHX_FAIL_AT("Vektor " + v.name + " nicht lesbar");
    CHECK_EQ(f->universe, v.universe);
    CHECK_EQ(f->data, Bytes(512, v.fill));
  }
}
TEST(parse_missing_universe_means_zero) {
  auto f = parse(wireFrame(0, Bytes(512, 1)));  // proto3 omits the default value 0.
  CHECK(f.has_value());
  CHECK_EQ(f->universe, std::uint32_t{0});
}
TEST(parse_empty_message_is_valid_default_message) {
  auto f = parse(Bytes{});
  CHECK(f.has_value());
  CHECK_EQ(f->universe, std::uint32_t{0});
  CHECK_EQ(f->data.size(), std::size_t{0});
}
TEST(parse_does_not_range_check_universe) {
  auto f = parse(wireFrame(70000, Bytes(512, 2)));  // Range validation belongs to LinkSession.
  CHECK(f.has_value());
  CHECK_EQ(f->universe, std::uint32_t{70000});
}
TEST(parse_accepts_any_data_length) {
  auto f = parse(wireFrame(3, Bytes{1, 2, 3}));
  CHECK(f.has_value());
  CHECK_EQ(f->data, (Bytes{1, 2, 3}));
}
TEST(parse_ignores_unknown_fields) {
  Bytes wire = wireFrame(4, Bytes(512, 7));
  wire.push_back(0x18);  // Feld 3, Varint
  wire.push_back(0x05);
  auto f = parse(wire);
  CHECK(f.has_value());
  CHECK_EQ(f->universe, std::uint32_t{4});
  CHECK_EQ(f->data, Bytes(512, 7));
}
TEST(parse_last_data_field_wins) {
  Bytes wire{0x12, 0x01, 0xAA, 0x12, 0x01, 0xBB};
  auto f = parse(wire);
  CHECK(f.has_value());
  CHECK_EQ(f->data, (Bytes{0xBB}));
}
TEST(parse_truncated_data_returns_nullopt) {
  Bytes wire = wireFrame(1, Bytes(512, 9));
  wire.resize(wire.size() - 1);  // The declared length is 512, but only 511 bytes remain.
  CHECK(!parse(wire).has_value());
}
TEST(parse_truncated_varint_returns_nullopt) {
  CHECK(!parse(Bytes{0x08, 0xFF}).has_value());  // Continuation bit is set at end of input.
}
TEST(parse_dangling_tag_returns_nullopt) {
  CHECK(!parse(Bytes{0x08}).has_value());
  CHECK(!parse(Bytes{0x12}).has_value());
}
TEST(parse_random_garbage_never_crashes) {
  Bytes g{0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01};
  (void)parse(g);  // Either result is acceptable; the test only requires termination without a crash or infinite loop.
  (void)parse(Bytes{0x0A, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F});  // Huge declared length with little data.
  CHECK(true);
}
TEST(serialize_parse_roundtrip) {
  DmxFrame in{1234, Bytes(512, 0)};
  for (std::size_t i = 0; i < in.data.size(); ++i) in.data[i] = static_cast<std::uint8_t>(i * 31);
  auto out = parse(serializeDmxFrame(in));
  CHECK(out.has_value());
  CHECK_EQ(out->universe, in.universe);
  CHECK_EQ(out->data, in.data);
}

ARCHX_TEST_MAIN()
