// Tests LinkSession without networking. An injected parser keeps them
// independent of generated Protobuf code.
#include "dmx/include/link/link_session.h"
#include "test_support.h"

using namespace archx::dmx::link;
using namespace archx::test;

namespace {
bool feed(LinkSession& s, const Bytes& b) { return s.onBytes(b.data(), b.size()); }
Bytes garbageMessage() { return framed(Bytes{0xFF, 0xFF, 0xFF}); }  // Valid frame with malformed contents.
}  // namespace

TEST(applies_valid_frame) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(feed(s, wireMessage(5, 0x42)));
  CHECK_EQ(sink.count(), std::size_t{1});
  auto e = sink.last(5);
  CHECK(e.has_value());
  CHECK(allBytesEqual(e->data, 0x42));
  CHECK_EQ(s.stats().framesApplied, std::uint64_t{1});
}
TEST(copies_all_512_bytes_in_order) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  Bytes data(512);
  for (std::size_t i = 0; i < 512; ++i) data[i] = static_cast<std::uint8_t>(i * 13 + 1);
  CHECK(feed(s, framed(wireFrame(2, data))));
  auto e = sink.last(2);
  CHECK(e.has_value());
  CHECK(Bytes(e->data.begin(), e->data.end()) == data);
}
TEST(applies_frames_in_arrival_order) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  Bytes in = concat({wireMessage(1, 1), wireMessage(2, 2), wireMessage(1, 3)});
  CHECK(feed(s, in));
  auto all = sink.all();
  CHECK_EQ(all.size(), std::size_t{3});
  CHECK_EQ(all[0].universe, std::uint16_t{1});
  CHECK_EQ(all[1].universe, std::uint16_t{2});
  CHECK_EQ(all[2].universe, std::uint16_t{1});
  CHECK(allBytesEqual(all[2].data, 3));
}
TEST(accepts_universe_zero_and_max) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(feed(s, wireMessage(0, 1)));
  CHECK(feed(s, wireMessage(kMaxUniverse, 2)));
  CHECK_EQ(s.stats().framesApplied, std::uint64_t{2});
  CHECK(sink.last(32767).has_value());
}
TEST(rejects_universe_above_max_without_closing) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(feed(s, wireMessage(kMaxUniverse + 1, 1)));  // Still true: invalid content does not close the connection.
  CHECK_EQ(sink.count(), std::size_t{0});
  CHECK_EQ(s.stats().framesInvalid, std::uint64_t{1});
}
TEST(universe_above_16_bit_is_rejected_not_truncated) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(feed(s, wireMessage(65536 + 3, 1)));  // Must not truncate to universe 3 as a uint16_t.
  CHECK_EQ(sink.count(), std::size_t{0});
  CHECK_EQ(s.stats().framesInvalid, std::uint64_t{1});
}
TEST(rejects_wrong_data_sizes) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  for (std::size_t n : {std::size_t{0}, std::size_t{1}, std::size_t{511}, std::size_t{513}, std::size_t{1024}}) {
    CHECK(feed(s, wireMessage(1, 9, n)));
  }
  CHECK_EQ(sink.count(), std::size_t{0});
  CHECK_EQ(s.stats().framesInvalid, std::uint64_t{5});
}
TEST(malformed_payload_is_counted_and_skipped) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(feed(s, garbageMessage()));
  CHECK_EQ(sink.count(), std::size_t{0});
  CHECK_EQ(s.stats().framesMalformed, std::uint64_t{1});
}
TEST(stream_stays_in_sync_after_bad_content) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  Bytes in = concat({wireMessage(1, 1), garbageMessage(), wireMessage(kMaxUniverse + 1, 0),
                     wireMessage(1, 2, 100), wireMessage(7, 7)});
  CHECK(feed(s, in));
  CHECK_EQ(sink.count(), std::size_t{2});
  CHECK(allBytesEqual(sink.last(1)->data, 1));
  CHECK(allBytesEqual(sink.last(7)->data, 7));
  CHECK_EQ(s.stats().framesApplied, std::uint64_t{2});
  CHECK_EQ(s.stats().framesMalformed, std::uint64_t{1});
  CHECK_EQ(s.stats().framesInvalid, std::uint64_t{2});
}
TEST(works_when_delivered_byte_by_byte) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  Bytes in = concat({wireMessage(1, 0xA1), wireMessage(2, 0xA2)});
  for (auto b : in) CHECK(s.onBytes(&b, 1));
  CHECK_EQ(sink.count(), std::size_t{2});
  CHECK(allBytesEqual(sink.last(2)->data, 0xA2));
}
TEST(framing_error_returns_false_and_counts_once) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(!feed(s, Bytes{0, 0, 0, 0}));
  CHECK_EQ(s.stats().framingErrors, std::uint64_t{1});
  CHECK(!feed(s, Bytes{1, 2, 3}));  // Further bytes still return false and are not counted again.
  CHECK_EQ(s.stats().framingErrors, std::uint64_t{1});
}
TEST(frames_before_a_framing_error_are_still_applied) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  Bytes in = concat({wireMessage(1, 1), wireMessage(2, 2), Bytes{0xFF, 0xFF, 0xFF, 0xFF}, wireMessage(3, 3)});
  CHECK(!feed(s, in));
  CHECK_EQ(sink.count(), std::size_t{2});
  CHECK(!sink.last(3).has_value());
}
TEST(no_frames_applied_after_error_until_reset) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(!feed(s, Bytes{0, 0, 0, 0}));
  CHECK(!feed(s, wireMessage(1, 1)));
  CHECK_EQ(sink.count(), std::size_t{0});
  s.reset();
  CHECK(feed(s, wireMessage(1, 1)));
  CHECK_EQ(sink.count(), std::size_t{1});
}
TEST(reset_discards_a_half_received_frame) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  Bytes half = wireMessage(1, 0xEE);
  half.resize(100);
  CHECK(feed(s, half));
  s.reset();
  CHECK(feed(s, wireMessage(2, 0x11)));
  CHECK_EQ(sink.count(), std::size_t{1});  // No data from the previous partial frame is applied.
  CHECK(allBytesEqual(sink.last(2)->data, 0x11));
}
TEST(reset_keeps_stats_and_does_not_touch_the_sink) {
  FakeSink sink;
  LinkSession s(sink, testParse);
  CHECK(feed(s, wireMessage(1, 1)));
  s.reset();
  CHECK_EQ(s.stats().framesApplied, std::uint64_t{1});
  CHECK_EQ(sink.count(), std::size_t{1});
}
TEST(uses_the_injected_parser_once_per_frame_with_exact_payload) {
  FakeSink sink;
  std::vector<Bytes> seen;
  LinkSession s(sink, [&](const std::uint8_t* p, std::size_t n) -> std::optional<DmxFrame> {
    seen.emplace_back(p, p + n);
    return std::nullopt;
  });
  Bytes p1{1, 2, 3}, p2{4, 5};
  CHECK(feed(s, concat({framed(p1), framed(p2)})));
  CHECK_EQ(seen.size(), std::size_t{2});
  CHECK_EQ(seen[0], p1);
  CHECK_EQ(seen[1], p2);
}

ARCHX_TEST_MAIN()
