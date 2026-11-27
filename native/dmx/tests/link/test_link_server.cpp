// Integration tests for LinkServer using real sockets on 127.0.0.1.
#include <fstream>

#include "dmx/include/link/link_server.h"
#include "test_support.h"

using namespace archx::dmx::link;
using namespace archx::test;

namespace {
// Waits until the sink contains at least n frames.
bool sinkHas(const FakeSink& s, std::size_t n, int ms = 2000) {
  return waitFor([&] { return s.count() >= n; }, ms);
}
}  // namespace

TEST(start_picks_a_port_and_stop_is_idempotent) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK_EQ(server.port(), std::uint16_t{0});
  CHECK(server.start(0));
  CHECK(server.port() != 0);
  server.stop();
  server.stop();
}
TEST(stop_and_destructor_without_start_are_safe) {
  FakeSink sink;
  {
    LinkServer a(sink, testParse);
    a.stop();
  }
  { LinkServer b(sink, testParse); }
  CHECK(true);
}
TEST(second_start_fails) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  CHECK(!server.start(0));
}
TEST(start_on_busy_port_fails_and_leaks_nothing) {
  FakeSink sink;
  LinkServer a(sink, testParse), b(sink, testParse);
  CHECK(a.start(0));
  CHECK(!b.start(a.port()));
  CHECK_EQ(b.port(), std::uint16_t{0});
  b.stop();  // stop() remains harmless after a failed start().
}
TEST(listens_on_loopback_only) {
  // Linux-specific (/proc); skip this test on other systems.
  std::ifstream tcp("/proc/net/tcp");
  if (!tcp) return;
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  char port[8];
  std::snprintf(port, sizeof port, "%04X", server.port());
  std::string loop = std::string("0100007F:") + port, any = std::string("00000000:") + port;
  bool sawLoop = false, sawAny = false;
  std::string line;
  while (std::getline(tcp, line)) {
    if (line.find(" 0A ") == std::string::npos) continue;  // Keep LISTEN entries only.
    if (line.find(loop) != std::string::npos) sawLoop = true;
    if (line.find(any) != std::string::npos) sawAny = true;
  }
  CHECK(sawLoop);
  CHECK(!sawAny);
}
TEST(receives_a_frame) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  CHECK(c.sendAll(wireMessage(3, 0x33)));
  CHECK(sinkHas(sink, 1));
  CHECK(allBytesEqual(sink.last(3)->data, 0x33));
  CHECK_EQ(server.connectionsAccepted(), std::uint64_t{1});
}
TEST(receives_a_frame_sent_byte_by_byte) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  CHECK(c.sendBytewise(wireMessage(4, 0x44)));
  CHECK(sinkHas(sink, 1));
  CHECK(allBytesEqual(sink.last(4)->data, 0x44));
}
TEST(receives_a_burst_of_many_frames_in_order) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  Bytes burst;
  for (int i = 0; i < 500; ++i) {
    Bytes m = wireMessage(static_cast<std::uint32_t>(i % 10), static_cast<std::uint8_t>(i));
    burst.insert(burst.end(), m.begin(), m.end());
  }
  CHECK(c.sendAll(burst));
  CHECK(sinkHas(sink, 500, 5000));
  auto all = sink.all();
  for (int i = 0; i < 500; ++i) {
    if (all[i].universe != i % 10 || !allBytesEqual(all[i].data, static_cast<std::uint8_t>(i)))
      ARCHX_FAIL_AT("Rahmen " + std::to_string(i) + " falsch oder in falscher Reihenfolge");
  }
}
TEST(new_connection_replaces_the_old_one) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient a, b;
  CHECK(a.connect(server.port()));
  CHECK(a.sendAll(wireMessage(1, 1)));
  CHECK(sinkHas(sink, 1));
  CHECK(b.connect(server.port()));
  CHECK(waitFor([&] { return server.connectionsAccepted() == 2; }));
  CHECK(a.waitForClose());  // The old connection was closed.
  CHECK(b.sendAll(wireMessage(2, 2)));
  CHECK(sinkHas(sink, 2));
  CHECK(allBytesEqual(sink.last(2)->data, 2));
}
TEST(reconnect_after_client_closed_works_and_frames_stay) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  {
    TcpClient a;
    CHECK(a.connect(server.port()));
    CHECK(a.sendAll(wireMessage(1, 0xA1)));
    CHECK(sinkHas(sink, 1));
  }  // Client a disconnects.
  TcpClient b;
  CHECK(b.connect(server.port()));
  CHECK(b.sendAll(wireMessage(2, 0xB2)));
  CHECK(sinkHas(sink, 2));
  CHECK(allBytesEqual(sink.last(1)->data, 0xA1));  // The previous frame remains in the sink.
  CHECK_EQ(server.connectionsAccepted(), std::uint64_t{2});
}
TEST(half_received_frame_of_a_dead_connection_is_discarded) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  {
    TcpClient a;
    CHECK(a.connect(server.port()));
    Bytes half = wireMessage(1, 0xEE);
    half.resize(200);
    CHECK(a.sendAll(half));
    sleepMs(50);
  }
  TcpClient b;
  CHECK(b.connect(server.port()));
  CHECK(b.sendAll(wireMessage(2, 0x22)));
  CHECK(sinkHas(sink, 1));
  sleepMs(100);
  CHECK_EQ(sink.count(), std::size_t{1});  // Exactly one complete frame was accepted.
  CHECK(allBytesEqual(sink.last(2)->data, 0x22));
}
TEST(framing_error_closes_only_that_connection) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient bad;
  CHECK(bad.connect(server.port()));
  CHECK(bad.sendAll(Bytes{0, 0, 0, 0}));
  CHECK(bad.waitForClose());
  TcpClient good;
  CHECK(good.connect(server.port()));
  CHECK(good.sendAll(wireMessage(5, 0x55)));
  CHECK(sinkHas(sink, 1));
}
TEST(oversize_length_closes_the_connection) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  CHECK(c.sendAll(Bytes{0xFF, 0xFF, 0xFF, 0x7F}));
  CHECK(c.waitForClose());
}
TEST(frames_before_a_framing_error_are_applied_then_connection_closes) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  CHECK(c.sendAll(concat({wireMessage(1, 1), Bytes{0, 0, 0, 0}})));
  CHECK(sinkHas(sink, 1));
  CHECK(c.waitForClose());
}
TEST(invalid_content_does_not_close_the_connection) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  CHECK(c.sendAll(concat({wireMessage(1, 1, 100), framed(Bytes{0xFF}), wireMessage(2, 2)})));
  CHECK(sinkHas(sink, 1));
  CHECK_EQ(sink.count(), std::size_t{1});
  CHECK(!c.waitForClose(300));  // The connection remains open.
  CHECK(c.sendAll(wireMessage(3, 3)));
  CHECK(sinkHas(sink, 2));
}
TEST(stop_with_connected_client_returns_quickly_and_closes_it) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  TcpClient c;
  CHECK(c.connect(server.port()));
  CHECK(waitFor([&] { return server.connectionsAccepted() == 1; }));
  auto t0 = std::chrono::steady_clock::now();
  server.stop();
  auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - t0).count();
  CHECK(ms < 1000);
  CHECK(c.waitForClose(500));
}
TEST(port_is_closed_after_stop) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  std::uint16_t port = server.port();
  server.stop();
  TcpClient c;
  CHECK(!c.connect(port));
}
TEST(idle_server_does_not_burn_cpu) {
  FakeSink sink;
  LinkServer server(sink, testParse);
  CHECK(server.start(0));
  // A rough busy-wait check: 300 ms idle time should not consume about 300 ms of CPU.
  auto cpu = [] { return std::clock(); };
  auto c0 = cpu();
  sleepMs(300);
  double usedMs = 1000.0 * static_cast<double>(cpu() - c0) / CLOCKS_PER_SEC;
  CHECK(usedMs < 100.0);
}

ARCHX_TEST_MAIN()
