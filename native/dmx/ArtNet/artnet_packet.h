// Declares the ArtDmx data structure and packet building and parsing functions.

#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <vector>

namespace archx {
    // UDP port used to send and receive Art-Net.
    constexpr uint16_t kArtNetPort = 6454;

    // Contents of an ArtDmx packet.
    struct ArtDmx {
        uint16_t port_address = 0; // 15 bits (Net 7, Sub-Net 4, Universe 4), starting at 0.
        uint8_t sequence = 0; // 0 means sequence numbering is disabled.
        std::vector<uint8_t> data; // 2 to 512 DMX values.
    };

    // Builds an ArtDmx packet (18-byte header plus data).
    // An odd number of values is padded with zero because Art-Net requires an even length.
    // Throws std::invalid_argument if port_address exceeds 0x7FFF or data is empty or longer than 512.
    std::vector<uint8_t> BuildArtDmx(uint16_t port_address, uint8_t sequence, const std::vector<uint8_t> &data);

    // Parses a received packet.
    // Returns std::nullopt if the packet is not valid ArtDmx.
    std::optional<ArtDmx> ParseArtDmx(const uint8_t *bytes, size_t length);
} // namespace archx
