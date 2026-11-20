// Zeigt eingehende Art-Net-Pakete an. Nur für Linux und WSL (benutzt POSIX-Sockets).
//
// Aufruf:  artnet_monitor [port]      (Standard: 6454)
//
// Es erscheint eine Zeile, wenn sich ein Universum ändert, und mindestens einmal pro Sekunde.
// Angezeigt werden die Kanäle, die nicht 0 sind (höchstens 24).

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <map>
#include <vector>

#include "../artnet_packet.h"

int main(int argc, char** argv) {
    const int port = argc > 1 ? std::atoi(argv[1]) : archx::kArtNetPort;

    const int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        std::perror("socket");
        return 1;
    }

    // Erlaubt, dass mehrere Programme denselben Port lesen (zum Beispiel Monitor und ein Visualizer)
    int yes = 1;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_ANY);
    address.sin_port = htons(static_cast<uint16_t>(port));
    if (bind(sock, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0) {
        std::perror("bind");
        return 1;
    }

    std::cout << "Warte auf Art-Net auf UDP-Port " << port << " ...\n";

    std::vector<uint8_t> buffer(2048);
    std::map<uint16_t, std::vector<uint8_t>> last;   // letzter Stand pro Universum
    std::map<uint16_t, unsigned long> counts;        // Anzahl der Pakete pro Universum
    auto lastPrint = std::chrono::steady_clock::now();

    while (true) {
        const ssize_t received = recvfrom(sock, buffer.data(), buffer.size(), 0, nullptr, nullptr);
        if (received < 0) {
            std::perror("recvfrom");
            return 1;
        }

        const auto packet = archx::ParseArtDmx(buffer.data(), static_cast<size_t>(received));
        if (!packet) continue;   // kein ArtDmx-Paket

        counts[packet->port_address]++;
        const bool changed = last[packet->port_address] != packet->data;
        const auto now = std::chrono::steady_clock::now();

        if (changed || now - lastPrint > std::chrono::seconds(1)) {
            lastPrint = now;
            last[packet->port_address] = packet->data;

            std::cout << "Universum " << packet->port_address + 1 << " (Art-Net " << packet->port_address
                      << "), Paket " << counts[packet->port_address]
                      << ", Folge " << static_cast<int>(packet->sequence) << ":";

            int shown = 0;
            for (size_t i = 0; i < packet->data.size() && shown < 24; ++i) {
                if (packet->data[i] != 0) {
                    std::cout << ' ' << (i + 1) << '=' << static_cast<int>(packet->data[i]);
                    ++shown;
                }
            }
            if (shown == 0) std::cout << " alle Kanäle 0";
            std::cout << '\n';
        }
    }
}
