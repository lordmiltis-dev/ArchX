// Tests the DmxOutput send loop and UDP packet behavior with a local receiver.

// Tests for DmxOutput. A local UDP receiver captures the packets. Linux and WSL only.
//
// Some tests depend on timing, for example the 44 Hz rate. The limits are generous,
// but a heavily loaded machine may occasionally make a test fail. Run it again if that happens.

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#include <chrono>
#include <cstdint>
#include <iostream>
#include <map>
#include <optional>
#include <stdexcept>
#include <thread>
#include <vector>

#include "ArtNet/artnet_packet.h"
#include "../dmx_output.h"

using namespace archx;

// Small test helpers

static int failures = 0;

#define CHECK(cond)                                                                  \
    do {                                                                             \
        if (!(cond)) {                                                               \
            std::cerr << "ERROR " << __FILE__ << ":" << __LINE__ << ": " #cond "\n"; \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

#define CHECK_EQ(a, b)                                                               \
    do {                                                                             \
        auto va = (a);                                                               \
        auto vb = (b);                                                               \
        if (!(va == vb)) {                                                           \
            std::cerr << "ERROR " << __FILE__ << ":" << __LINE__ << ": " #a " == " #b \
                      << "  (" << +va << " != " << +vb << ")\n";                     \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

#define CHECK_THROWS_INVALID(expr)                                                   \
    do {                                                                             \
        bool thrown = false;                                                         \
        try {                                                                        \
            (void)(expr);                                                            \
        } catch (const std::invalid_argument&) {                                     \
            thrown = true;                                                           \
        }                                                                            \
        if (!thrown) {                                                               \
            std::cerr << "ERROR " << __FILE__ << ":" << __LINE__                    \
                      << ": expected std::invalid_argument at " #expr "\n";         \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

// Runs a test. An unexpected exception counts as a failure, but the remaining tests continue.
#define RUN(test)                                                                    \
    do {                                                                             \
        try {                                                                        \
            test();                                                                  \
        } catch (const std::exception& e) {                                          \
            std::cerr << "ERROR in " #test ": exception: " << e.what() << "\n";      \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

// Test helpers

// A local UDP receiver on a free port, with a 300 ms wait for each packet.
class Receiver {
public:
    Receiver() {
        sock_ = socket(AF_INET, SOCK_DGRAM, 0);
        if (sock_ < 0) throw std::runtime_error("socket failed");

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        addr.sin_port = 0; // The system chooses a free port.
        if (bind(sock_, reinterpret_cast<sockaddr *>(&addr), sizeof(addr)) < 0) {
            throw std::runtime_error("bind failed");
        }
        socklen_t length = sizeof(addr);
        getsockname(sock_, reinterpret_cast<sockaddr *>(&addr), &length);
        port_ = ntohs(addr.sin_port);

        timeval timeout{};
        timeout.tv_sec = 0;
        timeout.tv_usec = 300000;
        setsockopt(sock_, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    }

    ~Receiver() { close(sock_); }

    uint16_t port() const { return port_; }

    // Waits for the next ArtDmx packet. Returns std::nullopt on timeout.
    std::optional<ArtDmx> Receive() {
        uint8_t buffer[2048];
        const ssize_t received = recv(sock_, buffer, sizeof(buffer), 0);
        if (received < 0) return std::nullopt;
        return ParseArtDmx(buffer, static_cast<size_t>(received));
    }

private:
    int sock_ = -1;
    uint16_t port_ = 0;
};

static DmxOutputConfig ConfigFor(const Receiver &receiver) {
    DmxOutputConfig config;
    config.target_ip = "127.0.0.1";
    config.port = receiver.port();
    return config;
}

static std::vector<uint8_t> Ramp512() {
    std::vector<uint8_t> data(512);
    for (size_t i = 0; i < data.size(); ++i) data[i] = static_cast<uint8_t>(i);
    return data;
}

// Tests

// The frame keeps being sent even when it does not change.
static void test_frame_is_sent_repeatedly() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.SetFrame(1, Ramp512());
    out.Start();

    for (int i = 0; i < 5; ++i) {
        const auto packet = rx.Receive();
        CHECK(packet.has_value());
        if (!packet) break;
        CHECK_EQ(packet->port_address, 0); // Universe 1 is sent as Art-Net universe 0.
        CHECK(packet->data == Ramp512());
    }
    out.Stop();
}

// The sequence number increases from 1 to 255 and then starts again at 1. Zero is never used.
static void test_sequence_counts_up_and_skips_zero() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.SetFrame(1, Ramp512());
    out.Start();

    std::vector<int> sequences;
    for (int i = 0; i < 6; ++i) {
        const auto packet = rx.Receive();
        CHECK(packet.has_value());
        if (!packet) break;
        sequences.push_back(packet->sequence);
    }
    out.Stop();

    for (size_t i = 0; i < sequences.size(); ++i) {
        CHECK(sequences[i] != 0);
        if (i > 0) {
            const int expected = sequences[i - 1] == 255 ? 1 : sequences[i - 1] + 1;
            CHECK_EQ(sequences[i], expected);
        }
    }
}

// A new frame replaces the old one.
static void test_new_frame_replaces_the_old_one() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    const std::vector<uint8_t> a(512, 10);
    const std::vector<uint8_t> b(512, 20);
    out.SetFrame(1, a);
    out.Start();

    const auto first = rx.Receive();
    CHECK(first.has_value() && first->data == a);

    out.SetFrame(1, b);
    bool seenB = false;
    for (int i = 0; i < 30 && !seenB; ++i) {
        const auto packet = rx.Receive();
        if (packet && packet->data == b) seenB = true;
    }
    CHECK(seenB);

    const auto next = rx.Receive();
    CHECK(next.has_value() && next->data == b);
    out.Stop();
}

// Every universe is sent at every tick.
static void test_all_universes_are_sent() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.SetFrame(1, std::vector<uint8_t>(512, 1));
    out.SetFrame(3, std::vector<uint8_t>(512, 3));
    out.Start();

    std::map<uint16_t, int> counts;
    for (int i = 0; i < 10; ++i) {
        const auto packet = rx.Receive();
        CHECK(packet.has_value());
        if (!packet) break;
        counts[packet->port_address]++;
    }
    out.Stop();

    CHECK_EQ(counts.size(), static_cast<size_t>(2));
    CHECK(counts[0] >= 4); // Universe 1 as Art-Net 0.
    CHECK(counts[2] >= 4); // Universe 3 as Art-Net 2.
}

// Approximately 44 packets per second, with a generous tolerance.
static void test_rate_is_about_44_hz() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.SetFrame(1, Ramp512());
    out.Start();

    std::this_thread::sleep_for(std::chrono::seconds(1));
    const uint64_t sent = out.PacketsSent();
    out.Stop();

    CHECK(sent >= 35);
    CHECK(sent <= 55);
}

// No packets are sent when no frame is set.
static void test_nothing_is_sent_without_a_frame() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.Start();

    CHECK(!rx.Receive().has_value());
    CHECK_EQ(out.PacketsSent(), uint64_t{0});
    out.Stop();
}

// No packets are sent after Stop.
static void test_stop_ends_sending() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.SetFrame(1, Ramp512());
    out.Start();
    CHECK(rx.Receive().has_value());

    out.Stop();
    const uint64_t after = out.PacketsSent();
    std::this_thread::sleep_for(std::chrono::milliseconds(300));
    CHECK_EQ(out.PacketsSent(), after);
}

// Destroying an output that was never started and calling Stop multiple times are harmless.
static void test_stop_and_destructor_without_start_are_harmless() {
    DmxOutput out;
    out.Stop();
    out.Stop();
}

static void test_invalid_frames_are_rejected() {
    DmxOutput out;
    CHECK_THROWS_INVALID(out.SetFrame(0, std::vector<uint8_t>(512, 0))); // Universe 0 is invalid.
    CHECK_THROWS_INVALID(out.SetFrame(32769, std::vector<uint8_t>(512, 0))); // Universe is too large.
    CHECK_THROWS_INVALID(out.SetFrame(1, std::vector<uint8_t>())); // Frame is empty.
    CHECK_THROWS_INVALID(out.SetFrame(1, std::vector<uint8_t>(513, 0))); // Frame is too large.
}

int main() {
    RUN(test_invalid_frames_are_rejected);
    RUN(test_stop_and_destructor_without_start_are_harmless);
    RUN(test_nothing_is_sent_without_a_frame);
    RUN(test_frame_is_sent_repeatedly);
    RUN(test_sequence_counts_up_and_skips_zero);
    RUN(test_new_frame_replaces_the_old_one);
    RUN(test_all_universes_are_sent);
    RUN(test_stop_ends_sending);
    RUN(test_rate_is_about_44_hz);

    if (failures == 0) {
        std::cout << "All tests passed\n";
        return 0;
    }
    std::cerr << failures << " check(s) failed\n";
    return 1;
}
