#include <fstream>
#include <iostream>
#include <string>

#include "fader.pb.h"  // wird von protoc aus schema/fader.proto erzeugt

int main() {
    // Pfad zur Datei, die das Kotlin-Programm geschrieben hat
    const std::string path = std::string(TESTDATA_DIR) + "/fader_moved.bin";

    std::ifstream in(path, std::ios::binary);
    if (!in) {
        std::cerr << "Datei nicht gefunden: " << path << "\n";
        return 1;
    }

    // Bytes in ein FaderMoved-Objekt umwandeln
    archx::FaderMoved msg;
    if (!msg.ParseFromIstream(&in)) {
        std::cerr << "Die Bytes konnten nicht gelesen werden\n";
        return 1;
    }

    std::cout << "fader_id = " << msg.fader_id() << "\n"
              << "value    = " << msg.value() << "\n"
              << "touched  = " << (msg.touched() ? "true" : "false") << "\n";

    // Prüfen, ob dieselben Werte herauskommen wie in Kotlin
    const bool ok = msg.fader_id() == 3 && msg.value() == 0.75f && msg.touched();
    if (!ok) {
        std::cerr << "FEHLER: Die Werte stimmen nicht\n";
        return 1;
    }

    std::cout << "OK: C++ liest dieselben Werte, die Kotlin geschrieben hat\n";
    return 0;
}
