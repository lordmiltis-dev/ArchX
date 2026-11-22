// Tests für DmxOutput. Ein lokaler UDP-Empfänger fängt die Pakete ab. Nur für Linux und WSL.
//
// Hinweis: Einige Tests hängen an der Zeit (zum Beispiel die Rate von 44 Hz). Die Grenzen sind großzügig,
// auf einem sehr ausgelasteten Rechner kann trotzdem ein Test einmal danebenliegen. Starte ihn dann noch einmal.

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
#include <set>
#include <stdexcept>
#include <thread>
#include <vector>

#include "ArtNet/artnet_packet.h"
#include "nativeDMX/dmx_output.h"

using namespace archx;

// ---------- kleine Prüf-Hilfen ----------

static int failures = 0;

#define CHECK(cond)                                                                  \
    do {                                                                             \
        if (!(cond)) {                                                               \
            std::cerr << "FEHLER " << __FILE__ << ":" << __LINE__ << ": " #cond "\n"; \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

#define CHECK_EQ(a, b)                                                               \
    do {                                                                             \
        auto va = (a);                                                               \
        auto vb = (b);                                                               \
        if (!(va == vb)) {                                                           \
            std::cerr << "FEHLER " << __FILE__ << ":" << __LINE__ << ": " #a " == " #b \
                      << "  (" << +va << " != " << +vb << ")\n";                     \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

#define CHECK_THROWS_INVALID(expr)                                                   \
    do {                                                                             \
        bool thrown = false;                                                         \
        try {                                                                        \
            expr;                                                                    \
        } catch (const std::invalid_argument&) {                                     \
            thrown = true;                                                           \
        }                                                                            \
        if (!thrown) {                                                               \
            std::cerr << "FEHLER " << __FILE__ << ":" << __LINE__                    \
                      << ": erwartet std::invalid_argument bei " #expr "\n";         \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

// Führt einen Test aus. Eine unerwartete Ausnahme zählt als Fehler, die anderen Tests laufen weiter.
#define RUN(test)                                                                    \
    do {                                                                             \
        try {                                                                        \
            test();                                                                  \
        } catch (const std::exception& e) {                                          \
            std::cerr << "FEHLER in " #test ": Ausnahme: " << e.what() << "\n";      \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

// ---------- Hilfen für die Tests ----------

// Ein lokaler UDP-Empfänger auf einem freien Port, mit Wartezeit von 300 ms pro Paket
class Receiver {
public:
    Receiver() {
        sock_ = socket(AF_INET, SOCK_DGRAM, 0);
        if (sock_ < 0) throw std::runtime_error("socket fehlgeschlagen");

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        addr.sin_port = 0;   // das System wählt einen freien Port
        if (bind(sock_, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
            throw std::runtime_error("bind fehlgeschlagen");
        }
        socklen_t length = sizeof(addr);
        getsockname(sock_, reinterpret_cast<sockaddr*>(&addr), &length);
        port_ = ntohs(addr.sin_port);

        timeval timeout{};
        timeout.tv_sec = 0;
        timeout.tv_usec = 300000;
        setsockopt(sock_, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    }

    ~Receiver() { close(sock_); }

    uint16_t port() const { return port_; }

    // Wartet auf das nächste ArtDmx-Paket. std::nullopt bei einer Zeitüberschreitung.
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

static DmxOutputConfig ConfigFor(const Receiver& receiver) {
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

// ---------- Tests ----------

// Auch ohne Änderung kommt der Rahmen immer wieder
static void test_frame_is_sent_repeatedly() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.SetFrame(1, Ramp512());
    out.Start();

    for (int i = 0; i < 5; ++i) {
        const auto packet = rx.Receive();
        CHECK(packet.has_value());
        if (!packet) break;
        CHECK_EQ(packet->port_address, 0);   // Universum 1 wird als Art-Net-Universum 0 gesendet
        CHECK(packet->data == Ramp512());
    }
    out.Stop();
}

// Die Folgenummer zählt von 1 bis 255 hoch und beginnt dann wieder bei 1. Die 0 kommt nie vor.
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

// Ein neuer Rahmen ersetzt den alten
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

// Jedes Universum wird in jedem Takt gesendet
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
    CHECK(counts[0] >= 4);   // Universum 1 als Art-Net 0
    CHECK(counts[2] >= 4);   // Universum 3 als Art-Net 2
}

// Rund 44 Pakete pro Sekunde (mit großem Spielraum)
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

// Ohne gesetzten Rahmen wird nichts gesendet
static void test_nothing_is_sent_without_a_frame() {
    Receiver rx;
    DmxOutput out(ConfigFor(rx));
    out.Start();

    CHECK(!rx.Receive().has_value());
    CHECK_EQ(out.PacketsSent(), uint64_t{0});
    out.Stop();
}

// Nach Stop kommen keine Pakete mehr
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

// Zerstören ohne Start und mehrfaches Stop sind harmlos
static void test_stop_and_destructor_without_start_are_harmless() {
    DmxOutput out;
    out.Stop();
    out.Stop();
}

static void test_invalid_frames_are_rejected() {
    DmxOutput out;
    CHECK_THROWS_INVALID(out.SetFrame(0, std::vector<uint8_t>(512, 0)));        // Universum ab 1
    CHECK_THROWS_INVALID(out.SetFrame(32769, std::vector<uint8_t>(512, 0)));    // zu groß
    CHECK_THROWS_INVALID(out.SetFrame(1, std::vector<uint8_t>()));              // leer
    CHECK_THROWS_INVALID(out.SetFrame(1, std::vector<uint8_t>(513, 0)));        // zu lang
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
        std::cout << "Alle Tests bestanden\n";
        return 0;
    }
    std::cerr << failures << " Prüfung(en) fehlgeschlagen\n";
    return 1;
}
