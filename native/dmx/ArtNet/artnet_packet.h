#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <vector>

namespace archx {

// UDP-Port, auf dem Art-Net gesendet und empfangen wird
constexpr uint16_t kArtNetPort = 6454;

// Der Inhalt eines ArtDmx-Pakets
struct ArtDmx {
    uint16_t port_address = 0;   // 15 Bit (Net 7 Bit, Sub-Net 4 Bit, Universum 4 Bit), zählt ab 0
    uint8_t sequence = 0;        // 0 bedeutet: Sequenznummer wird nicht benutzt
    std::vector<uint8_t> data;   // 2 bis 512 DMX-Werte
};

// Baut ein ArtDmx-Paket (18 Bytes Kopf plus Daten).
// Eine ungerade Anzahl Werte wird mit einer 0 aufgefüllt, denn Art-Net verlangt eine gerade Länge.
// Wirft std::invalid_argument, wenn port_address größer als 0x7FFF ist oder data leer oder länger als 512 ist.
std::vector<uint8_t> BuildArtDmx(uint16_t port_address, uint8_t sequence, const std::vector<uint8_t>& data);

// Liest ein empfangenes Paket.
// Gibt std::nullopt zurück, wenn es kein gültiges ArtDmx-Paket ist.
std::optional<ArtDmx> ParseArtDmx(const uint8_t* bytes, size_t length);

}  // namespace archx
