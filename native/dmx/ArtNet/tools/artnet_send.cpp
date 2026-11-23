// Command-line utility that sends one Art-Net packet for testing and diagnostics.

// Sends a single Art-Net packet for testing the monitor. Linux and WSL only.
//
// Usage: artnet_send <universe starting at 1> <channel 1-512> <value 0-255> [target-IP]
// Example: artnet_send 1 1 255
//
// All other channels in the universe remain zero. Universe n is sent as Art-Net universe n-1.

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <exception>
#include <iostream>
#include <vector>

#include "ArtNet/artnet_packet.h"

int main(int argc, char **argv) {
    if (argc < 4) {
        std::cerr << "Usage: artnet_send <universe starting at 1> <channel 1-512> <value 0-255> [target-IP]\n";
        return 1;
    }

    const int universe = std::atoi(argv[1]);
    const int channel = std::atoi(argv[2]);
    const int value = std::atoi(argv[3]);
    const char *target = argc > 4 ? argv[4] : "127.0.0.1";

    if (universe < 1 || universe > 32768 || channel < 1 || channel > 512 || value < 0 || value > 255) {
        std::cerr << "Invalid arguments: universe 1 to 32768, channel 1 to 512, value 0 to 255\n";
        return 1;
    }

    std::vector<uint8_t> data(512, 0);
    data[channel - 1] = static_cast<uint8_t>(value);

    std::vector<uint8_t> packet;
    try {
        packet = archx::BuildArtDmx(static_cast<uint16_t>(universe - 1), 0, data);
    } catch (const std::exception &e) {
        std::cerr << "Could not build packet: " << e.what() << "\n";
        return 1;
    }

    const int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        std::perror("socket");
        return 1;
    }

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(archx::kArtNetPort);
    if (inet_pton(AF_INET, target, &address.sin_addr) != 1) {
        std::cerr << "Invalid IP address: " << target << "\n";
        return 1;
    }

    const ssize_t sent = sendto(sock, packet.data(), packet.size(), 0,
                                reinterpret_cast<sockaddr *>(&address), sizeof(address));
    if (sent < 0) {
        std::perror("sendto");
        return 1;
    }

    std::cout << "Sent: universe " << universe << ", channel " << channel << " = " << value
            << " to " << target << ":" << archx::kArtNetPort << "\n";
    close(sock);
    return 0;
}
