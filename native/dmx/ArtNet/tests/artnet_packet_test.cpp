// Tests für den Bau und das Lesen von Art-Net-Paketen.

#include <cstdint>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "ArtNet/artnet_packet.h"

using namespace archx;

// ---------- kleine Prüf-Hilfen ----------

static int failures = 0;

template <typename T>
static auto format_val(const T& v) {
    if constexpr (std::is_integral_v<T> && sizeof(T) == 1) {
        return +v; // sorgt dafür, dass uint8_t / char als Zahl gedruckt wird
    } else {
        return v;
    }
}

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
                      << "  (" << format_val(va) << " != " << format_val(vb)<< ")\n";                     \
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
            std::cerr << "FEHLER " << __FILE__ << ":" << __LINE__                    \
                      << ": erwartet std::invalid_argument bei " #expr "\n";         \
            ++failures;                                                              \
        }                                                                            \
    } while (0)

// Ein Rahmen mit 512 Werten: 0, 1, 2, ... (ab 256 wieder von vorn)
static std::vector<uint8_t> Ramp512() {
    std::vector<uint8_t> data(512);
    for (size_t i = 0; i < data.size(); ++i) data[i] = static_cast<uint8_t>(i);
    return data;
}

// ---------- Paket bauen ----------

static void test_header_of_a_full_frame() {
    const auto packet = BuildArtDmx(0, 0, Ramp512());

    CHECK_EQ(packet.size(), static_cast<size_t>(18 + 512));

    const std::string id(reinterpret_cast<const char*>(packet.data()), 7);
    CHECK_EQ(id, std::string("Art-Net"));
    CHECK_EQ(packet[7], 0);          // abschließendes Nullbyte der Kennung
    CHECK_EQ(packet[8], 0x00);       // Operationscode 0x5000, niedriges Byte zuerst
    CHECK_EQ(packet[9], 0x50);
    CHECK_EQ(packet[10], 0x00);      // Protokollversion 14, hohes Byte zuerst
    CHECK_EQ(packet[11], 0x0E);
    CHECK_EQ(packet[13], 0);         // physischer Port
    CHECK_EQ(packet[16], 0x02);      // Länge 512 = 0x0200, hohes Byte zuerst
    CHECK_EQ(packet[17], 0x00);
}

static void test_data_is_copied_in_order() {
    const auto packet = BuildArtDmx(0, 0, Ramp512());
    CHECK_EQ(packet[18], 0);
    CHECK_EQ(packet[19], 1);
    CHECK_EQ(packet[18 + 255], 255);
    CHECK_EQ(packet[18 + 256], 0);
    CHECK_EQ(packet[18 + 511], 255);
}

static void test_port_address_is_split_into_two_bytes() {
    auto p0 = BuildArtDmx(0, 0, Ramp512());
    CHECK_EQ(p0[14], 0x00);
    CHECK_EQ(p0[15], 0x00);

    auto p1 = BuildArtDmx(0x0102, 0, Ramp512());   // Net 1, Sub-Net 0, Universum 2
    CHECK_EQ(p1[14], 0x02);
    CHECK_EQ(p1[15], 0x01);

    auto p2 = BuildArtDmx(0x7FFF, 0, Ramp512());   // der größte erlaubte Wert
    CHECK_EQ(p2[14], 0xFF);
    CHECK_EQ(p2[15], 0x7F);
}

static void test_port_address_too_large_is_rejected() {
    CHECK_THROWS_INVALID(BuildArtDmx(0x8000, 0, Ramp512()));
}

static void test_sequence_is_passed_through() {
    const auto packet = BuildArtDmx(0, 42, Ramp512());
    CHECK_EQ(packet[12], 42);
}

static void test_odd_length_is_padded_with_zero() {
    const auto packet = BuildArtDmx(0, 0, {10, 20, 30});

    CHECK_EQ(packet.size(), static_cast<size_t>(18 + 4));
    CHECK_EQ(packet[16], 0x00);
    CHECK_EQ(packet[17], 0x04);      // Länge 4
    CHECK_EQ(packet[18], 10);
    CHECK_EQ(packet[19], 20);
    CHECK_EQ(packet[20], 30);
    CHECK_EQ(packet[21], 0);         // aufgefülltes Byte
}

static void test_invalid_data_sizes_are_rejected() {
    CHECK_THROWS_INVALID(BuildArtDmx(0, 0, {}));
    CHECK_THROWS_INVALID(BuildArtDmx(0, 0, std::vector<uint8_t>(513, 1)));
}

// ---------- Paket lesen ----------

static void test_parse_returns_what_was_built() {
    const auto packet = BuildArtDmx(5, 7, {1, 2, 3, 4});
    const auto parsed = ParseArtDmx(packet.data(), packet.size());

    CHECK(parsed.has_value());
    if (!parsed) return;
    CHECK_EQ(parsed->port_address, 5);
    CHECK_EQ(parsed->sequence, 7);
    CHECK(parsed->data == std::vector<uint8_t>({1, 2, 3, 4}));
}

static void test_parse_of_a_full_frame() {
    const auto packet = BuildArtDmx(0x0102, 1, Ramp512());
    const auto parsed = ParseArtDmx(packet.data(), packet.size());

    CHECK(parsed.has_value());
    if (!parsed) return;
    CHECK_EQ(parsed->port_address, 0x0102);
    CHECK_EQ(parsed->data.size(), static_cast<size_t>(512));
    CHECK(parsed->data == Ramp512());
}

static void test_parse_of_a_padded_packet_contains_the_padding() {
    const auto packet = BuildArtDmx(0, 0, {1, 2, 3});
    const auto parsed = ParseArtDmx(packet.data(), packet.size());

    CHECK(parsed.has_value());
    if (!parsed) return;
    CHECK(parsed->data == std::vector<uint8_t>({1, 2, 3, 0}));
}

static void test_parse_ignores_bytes_after_the_declared_length() {
    auto packet = BuildArtDmx(0, 0, {1, 2});
    packet.push_back(99);
    packet.push_back(99);

    const auto parsed = ParseArtDmx(packet.data(), packet.size());

    CHECK(parsed.has_value());
    if (!parsed) return;
    CHECK(parsed->data == std::vector<uint8_t>({1, 2}));
}

static void test_parse_rejects_garbage() {
    const std::string text = "Hallo Welt, das ist kein Art-Net-Paket";
    CHECK(!ParseArtDmx(reinterpret_cast<const uint8_t*>(text.data()), text.size()).has_value());

    CHECK(!ParseArtDmx(nullptr, 0).has_value());

    const std::vector<uint8_t> tooShort(10, 0);
    CHECK(!ParseArtDmx(tooShort.data(), tooShort.size()).has_value());
}

static void test_parse_rejects_wrong_opcode() {
    auto packet = BuildArtDmx(0, 0, {1, 2});
    packet[9] = 0x21;   // ein anderer Operationscode, zum Beispiel ArtPoll-Antwort
    CHECK(!ParseArtDmx(packet.data(), packet.size()).has_value());
}

static void test_parse_rejects_a_truncated_packet() {
    const auto packet = BuildArtDmx(0, 0, Ramp512());
    // Der Kopf verspricht 512 Werte, es kommen aber nur 100 an
    CHECK(!ParseArtDmx(packet.data(), 18 + 100).has_value());
}

static void test_parse_rejects_an_impossible_length() {
    auto packet = BuildArtDmx(0, 0, {1, 2});
    packet[16] = 0x02;   // Länge 0x0201 = 513
    packet[17] = 0x01;
    CHECK(!ParseArtDmx(packet.data(), packet.size()).has_value());

    packet[16] = 0x00;   // Länge 0
    packet[17] = 0x00;
    CHECK(!ParseArtDmx(packet.data(), packet.size()).has_value());
}

int main() {
    try {
        test_header_of_a_full_frame();
        test_data_is_copied_in_order();
        test_port_address_is_split_into_two_bytes();
        test_port_address_too_large_is_rejected();
        test_sequence_is_passed_through();
        test_odd_length_is_padded_with_zero();
        test_invalid_data_sizes_are_rejected();

        test_parse_returns_what_was_built();
        test_parse_of_a_full_frame();
        test_parse_of_a_padded_packet_contains_the_padding();
        test_parse_ignores_bytes_after_the_declared_length();
        test_parse_rejects_garbage();
        test_parse_rejects_wrong_opcode();
        test_parse_rejects_a_truncated_packet();
        test_parse_rejects_an_impossible_length();
    } catch (const std::exception& e) {
        std::cerr << "Unerwartete Ausnahme: " << e.what() << "\n";
        return 1;
    }

    if (failures == 0) {
        std::cout << "Alle Tests bestanden\n";
        return 0;
    }
    std::cerr << failures << " Prüfung(en) fehlgeschlagen\n";
    return 1;
}
