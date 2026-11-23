// Implements Art-Net 4 packet serialization and validation for ArtDmx messages.

#include "artnet_packet.h"

#include <array>
#include <cstring>
#include <stdexcept>

namespace archx {
    constexpr uint16_t readLE16(const uint8_t *ptr) {
        return static_cast<uint16_t>(
            static_cast<uint32_t>(*(ptr + 0)) | (static_cast<uint32_t>(*(ptr + 1)) << 8));
    }

    constexpr uint16_t readBE16(const uint8_t *ptr) {
        return static_cast<uint16_t>(
            (static_cast<uint32_t>(*(ptr + 0)) << 8) | static_cast<uint32_t>(*(ptr + 1)));
    }

    constexpr void writeLE16(uint8_t *ptr, uint16_t val) {
        *(ptr + 0) = static_cast<uint8_t>(val & 0xFF);
        *(ptr + 1) = static_cast<uint8_t>((val >> 8) & 0xFF);
    }

    constexpr void writeBE16(uint8_t *ptr, uint16_t val) {
        *(ptr + 0) = static_cast<uint8_t>((val >> 8) & 0xFF);
        *(ptr + 1) = static_cast<uint8_t>(val & 0xFF);
    }

    std::vector<uint8_t> BuildArtDmx(uint16_t port_address, uint8_t sequence, const std::vector<uint8_t> &data) {
        constexpr size_t Header_Size = 18;

        if (port_address > 0x7FFF) {
            throw std::invalid_argument("port address is too big");
        }

        if (data.size() > 512 || data.empty()) {
            throw std::invalid_argument("DMX data is too big or to small");
        }

        uint16_t length = static_cast<uint16_t>(data.size());

        if (length % 2 != 0) {
            length++;
        }

        std::vector<uint8_t> packet(static_cast<size_t>(length) + Header_Size, 0x00);

        constexpr std::array<uint8_t, 8> Artnet_id = {'A', 'r', 't', '-', 'N', 'e', 't', 0x00};
        std::memcpy(packet.data(), Artnet_id.data(), Artnet_id.size());

        writeLE16(packet.data() + 8, 0x5000);
        writeBE16(packet.data() + 10, 14);

        *(packet.data() + 12) = sequence;

        *(packet.data() + 14) = static_cast<uint8_t>(port_address & 0xFF);
        *(packet.data() + 15) = static_cast<uint8_t>((port_address >> 8) & 0x7F);

        writeBE16(packet.data() + 16, length);

        std::memcpy(packet.data() + Header_Size, data.data(), data.size());


        return packet;
    }

    std::optional<ArtDmx> ParseArtDmx(const uint8_t *bytes, size_t length) {
        constexpr size_t Header_Size = 18;

        if (bytes == nullptr || length < Header_Size) {
            return std::nullopt;
        }

        constexpr std::array<uint8_t, 8> artNetId = {
            'A', 'r', 't', '-', 'N', 'e', 't', 0x00
        };

        if (std::memcmp(bytes, artNetId.data(), artNetId.size()) != 0) {
            return std::nullopt;
        }

        if (readLE16(&bytes[8]) != 0x5000) {
            return std::nullopt;
        }

        const uint16_t dmxLength = readBE16(&bytes[16]);

        if (dmxLength > (length - Header_Size) || dmxLength > 512 || dmxLength == 0) {
            return std::nullopt;
        }

        const uint16_t portAddress = static_cast<uint16_t>(
            (static_cast<uint32_t>(bytes[15] & 0x07F) << 8) | static_cast<uint32_t>(bytes[14])
        );

        ArtDmx package{};
        package.port_address = portAddress;
        package.sequence = bytes[12];
        package.data.assign(bytes + Header_Size, bytes + Header_Size + dmxLength);

        return package;
    }
} // namespace archx
