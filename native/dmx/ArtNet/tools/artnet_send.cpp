// Sendet ein einzelnes Art-Net-Paket, zum Ausprobieren des Monitors. Nur für Linux und WSL.
//
// Aufruf:  artnet_send <Universum ab 1> <Kanal 1-512> <Wert 0-255> [Ziel-IP]
// Beispiel:  artnet_send 1 1 255
//
// Alle anderen Kanäle des Universums bleiben 0. Das Universum n wird als Art-Net-Universum n-1 gesendet.

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

int main(int argc, char** argv) {
    if (argc < 4) {
        std::cerr << "Aufruf: artnet_send <Universum ab 1> <Kanal 1-512> <Wert 0-255> [Ziel-IP]\n";
        return 1;
    }

    const int universe = std::atoi(argv[1]);
    const int channel = std::atoi(argv[2]);
    const int value = std::atoi(argv[3]);
    const char* target = argc > 4 ? argv[4] : "127.0.0.1";

    if (universe < 1 || universe > 32768 || channel < 1 || channel > 512 || value < 0 || value > 255) {
        std::cerr << "Ungültige Angabe: Universum 1 bis 32768, Kanal 1 bis 512, Wert 0 bis 255\n";
        return 1;
    }

    std::vector<uint8_t> data(512, 0);
    data[channel - 1] = static_cast<uint8_t>(value);

    std::vector<uint8_t> packet;
    try {
        packet = archx::BuildArtDmx(static_cast<uint16_t>(universe - 1), 0, data);
    } catch (const std::exception& e) {
        std::cerr << "Paket konnte nicht gebaut werden: " << e.what() << "\n";
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
        std::cerr << "Ungültige IP-Adresse: " << target << "\n";
        return 1;
    }

    const ssize_t sent = sendto(sock, packet.data(), packet.size(), 0,
                                reinterpret_cast<sockaddr*>(&address), sizeof(address));
    if (sent < 0) {
        std::perror("sendto");
        return 1;
    }

    std::cout << "Gesendet: Universum " << universe << ", Kanal " << channel << " = " << value
              << " an " << target << ":" << archx::kArtNetPort << "\n";
    close(sock);
    return 0;
}
