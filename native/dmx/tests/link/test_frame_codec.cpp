// Tests für encodeFrame / FrameDecoder (Länge-Präfix-Framing).
#include <random>

#include "dmx/include/link/frame_codec.h"
#include "test_support.h"

using namespace archx::dmx::link;
using namespace archx::test;

namespace {
using Frames = std::vector<Bytes>;
Bytes patternPayload(std::size_t n, std::uint8_t seed = 0) {
  Bytes b(n);
  for (std::size_t i = 0; i < n; ++i) b[i] = static_cast<std::uint8_t>(seed + i * 7);
  return b;
}
}  // namespace

// ------------------------------------------------------------------ encodeFrame
TEST(encode_prepends_length_little_endian) {
  CHECK_EQ(encodeFrame(Bytes{1, 2, 3}), (Bytes{3, 0, 0, 0, 1, 2, 3}));
}
TEST(encode_length_uses_all_four_bytes_lowest_first) {
  Bytes e = encodeFrame(patternPayload(258));
  CHECK_EQ(e.size(), std::size_t{262});
  CHECK_EQ((Bytes(e.begin(), e.begin() + 4)), (Bytes{0x02, 0x01, 0x00, 0x00}));
}
TEST(encode_rejects_empty_payload) {
  CHECK_THROWS(encodeFrame(Bytes{}), std::invalid_argument);
}
TEST(encode_rejects_payload_above_max) {
  Bytes tooBig(kMaxPayloadSize + 1, 0);
  CHECK_THROWS(encodeFrame(tooBig), std::invalid_argument);
}
TEST(encode_accepts_exactly_max_payload) {
  Bytes max(kMaxPayloadSize, 0x5A);
  Bytes e = encodeFrame(max);
  CHECK_EQ(e.size(), std::size_t{kHeaderSize + kMaxPayloadSize});
  CHECK_EQ((Bytes(e.begin(), e.begin() + 4)), (Bytes{0x00, 0x00, 0x01, 0x00}));  // 65536
}
TEST(encode_matches_golden_vectors) {
  for (auto& v : vectors().frames) {
    Bytes got = encodeFrame(v.payload);
    if (!(got == v.framed)) ARCHX_FAIL_AT("Vektor " + v.name + ": " + toHex(got) + " != " + toHex(v.framed));
  }
}

// ------------------------------------------------------------------ FrameDecoder
TEST(decoder_single_frame_in_one_chunk) {
  FrameDecoder d;
  Frames out;
  Bytes in = encodeFrame(Bytes{9, 8, 7});
  CHECK(d.feed(in.data(), in.size(), out));
  CHECK_EQ(out.size(), std::size_t{1});
  CHECK_EQ(out[0], (Bytes{9, 8, 7}));
  CHECK_EQ(d.bufferedBytes(), std::size_t{0});
  CHECK(!d.failed());
}
TEST(decoder_two_frames_in_one_chunk_keep_order) {
  FrameDecoder d;
  Frames out;
  Bytes in = concat({encodeFrame(Bytes{1}), encodeFrame(Bytes{2, 2})});
  CHECK(d.feed(in.data(), in.size(), out));
  CHECK_EQ(out.size(), std::size_t{2});
  CHECK_EQ(out[0], (Bytes{1}));
  CHECK_EQ(out[1], (Bytes{2, 2}));
}
TEST(decoder_appends_to_existing_output) {
  FrameDecoder d;
  Frames out{Bytes{0xEE}};
  Bytes in = encodeFrame(Bytes{1});
  CHECK(d.feed(in.data(), in.size(), out));
  CHECK_EQ(out.size(), std::size_t{2});
  CHECK_EQ(out[0], (Bytes{0xEE}));
}
TEST(decoder_header_split_across_calls) {
  FrameDecoder d;
  Frames out;
  Bytes in = encodeFrame(Bytes{5, 6, 7, 8});
  CHECK(d.feed(in.data(), 2, out));
  CHECK_EQ(out.size(), std::size_t{0});
  CHECK_EQ(d.bufferedBytes(), std::size_t{2});
  CHECK(d.feed(in.data() + 2, in.size() - 2, out));
  CHECK_EQ(out.size(), std::size_t{1});
  CHECK_EQ(out[0], (Bytes{5, 6, 7, 8}));
}
TEST(decoder_payload_split_across_calls) {
  FrameDecoder d;
  Frames out;
  Bytes in = encodeFrame(patternPayload(10));
  CHECK(d.feed(in.data(), 6, out));  // Header + 2 Payload-Bytes
  CHECK_EQ(out.size(), std::size_t{0});
  CHECK_EQ(d.bufferedBytes(), std::size_t{6});
  CHECK(d.feed(in.data() + 6, in.size() - 6, out));
  CHECK_EQ(out.size(), std::size_t{1});
  CHECK_EQ(out[0], patternPayload(10));
  CHECK_EQ(d.bufferedBytes(), std::size_t{0});
}
TEST(decoder_empty_feed_is_a_noop) {
  FrameDecoder d;
  Frames out;
  CHECK(d.feed(nullptr, 0, out));
  CHECK_EQ(out.size(), std::size_t{0});
  CHECK_EQ(d.bufferedBytes(), std::size_t{0});
  CHECK(!d.failed());
}
TEST(decoder_frame_end_and_next_header_in_same_chunk) {
  FrameDecoder d;
  Frames out;
  Bytes a = encodeFrame(Bytes{1, 2});
  Bytes b = encodeFrame(Bytes{3});
  Bytes in = concat({a, Bytes(b.begin(), b.begin() + 2)});  // zweiter Header angefangen
  CHECK(d.feed(in.data(), in.size(), out));
  CHECK_EQ(out.size(), std::size_t{1});
  CHECK_EQ(d.bufferedBytes(), std::size_t{2});
  CHECK(d.feed(b.data() + 2, b.size() - 2, out));
  CHECK_EQ(out.size(), std::size_t{2});
  CHECK_EQ(out[1], (Bytes{3}));
}
TEST(decoder_golden_vectors_for_every_chunk_size) {
  for (auto& v : vectors().frames) {
    for (std::size_t chunk : {std::size_t{1}, std::size_t{2}, std::size_t{3}, std::size_t{7}, v.framed.size()}) {
      FrameDecoder d;
      Frames out;
      for (std::size_t off = 0; off < v.framed.size(); off += chunk) {
        std::size_t n = std::min(chunk, v.framed.size() - off);
        CHECK(d.feed(v.framed.data() + off, n, out));
      }
      if (out.size() != 1 || !(out[0] == v.payload))
        ARCHX_FAIL_AT("Vektor " + v.name + " mit Stückgröße " + std::to_string(chunk));
      CHECK_EQ(d.bufferedBytes(), std::size_t{0});
    }
  }
}
TEST(decoder_zero_length_is_a_protocol_error) {
  FrameDecoder d;
  Frames out;
  Bytes in{0, 0, 0, 0};
  CHECK(!d.feed(in.data(), in.size(), out));
  CHECK(d.failed());
  CHECK_EQ(out.size(), std::size_t{0});
}
TEST(decoder_oversize_length_fails_as_soon_as_header_is_complete) {
  FrameDecoder d;
  Frames out;
  Bytes header{0xFF, 0xFF, 0xFF, 0xFF};  // 4 GiB: darf nie angelegt/abgewartet werden
  CHECK(!d.feed(header.data(), header.size(), out));
  CHECK(d.failed());
}
TEST(decoder_length_max_plus_one_fails) {
  FrameDecoder d;
  Frames out;
  Bytes header{0x01, 0x00, 0x01, 0x00};  // 65537
  CHECK(!d.feed(header.data(), header.size(), out));
  CHECK(d.failed());
}
TEST(decoder_length_exactly_max_is_accepted) {
  FrameDecoder d;
  Frames out;
  Bytes e = encodeFrame(Bytes(kMaxPayloadSize, 0x11));
  CHECK(d.feed(e.data(), kHeaderSize, out));
  CHECK(!d.failed());
  CHECK(d.feed(e.data() + kHeaderSize, e.size() - kHeaderSize, out));
  CHECK_EQ(out.size(), std::size_t{1});
  CHECK_EQ(out[0].size(), std::size_t{kMaxPayloadSize});
}
TEST(decoder_error_in_split_header_is_detected_on_last_header_byte) {
  FrameDecoder d;
  Frames out;
  Bytes zero{0, 0, 0, 0};
  CHECK(d.feed(zero.data(), 3, out));  // noch kein vollständiger Header
  CHECK(!d.failed());
  CHECK(!d.feed(zero.data() + 3, 1, out));
  CHECK(d.failed());
}
TEST(decoder_failed_state_is_sticky) {
  FrameDecoder d;
  Frames out;
  Bytes bad{0, 0, 0, 0};
  CHECK(!d.feed(bad.data(), bad.size(), out));
  Bytes good = encodeFrame(Bytes{1});
  CHECK(!d.feed(good.data(), good.size(), out));
  CHECK_EQ(out.size(), std::size_t{0});
  CHECK(d.failed());
}
TEST(decoder_delivers_complete_frames_before_the_error) {
  FrameDecoder d;
  Frames out;
  Bytes in = concat({encodeFrame(Bytes{1}), encodeFrame(Bytes{2}), Bytes{0, 0, 0, 0}, encodeFrame(Bytes{3})});
  CHECK(!d.feed(in.data(), in.size(), out));
  CHECK_EQ(out.size(), std::size_t{2});  // der Rahmen NACH dem Fehler zählt nicht
  CHECK_EQ(out[0], (Bytes{1}));
  CHECK_EQ(out[1], (Bytes{2}));
}
TEST(decoder_reset_clears_partial_frame_and_failure) {
  FrameDecoder d;
  Frames out;
  Bytes half = encodeFrame(Bytes{1, 2, 3, 4});
  CHECK(d.feed(half.data(), 5, out));
  CHECK_EQ(d.bufferedBytes(), std::size_t{5});
  d.reset();
  CHECK_EQ(d.bufferedBytes(), std::size_t{0});
  Bytes bad{0, 0, 0, 0};
  CHECK(!d.feed(bad.data(), bad.size(), out));
  d.reset();
  CHECK(!d.failed());
  Bytes good = encodeFrame(Bytes{9});
  CHECK(d.feed(good.data(), good.size(), out));
  CHECK_EQ(out.size(), std::size_t{1});
  CHECK_EQ(out[0], (Bytes{9}));  // kein Rest vom alten halben Rahmen
}
TEST(decoder_survives_random_chunking_of_many_frames) {
  std::mt19937 rng(12345);  // feste Startzahl: Fehler sind reproduzierbar
  Frames expected;
  Bytes stream;
  for (int i = 0; i < 500; ++i) {
    std::size_t len = 1 + rng() % 700;
    Bytes p = patternPayload(len, static_cast<std::uint8_t>(i));
    expected.push_back(p);
    Bytes e = encodeFrame(p);
    stream.insert(stream.end(), e.begin(), e.end());
  }
  FrameDecoder d;
  Frames out;
  for (std::size_t off = 0; off < stream.size();) {
    std::size_t n = std::min<std::size_t>(1 + rng() % 40, stream.size() - off);
    CHECK(d.feed(stream.data() + off, n, out));
    off += n;
  }
  CHECK_EQ(out.size(), expected.size());
  for (std::size_t i = 0; i < expected.size(); ++i)
    if (!(out[i] == expected[i])) ARCHX_FAIL_AT("Rahmen " + std::to_string(i) + " weicht ab");
  CHECK_EQ(d.bufferedBytes(), std::size_t{0});
}

ARCHX_TEST_MAIN()
