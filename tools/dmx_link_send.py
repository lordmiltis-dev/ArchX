#!/usr/bin/env python3
"""Handtest-Werkzeug für die Verbindung Master -> DMX-Submaster (Phase 2.4).

Spielt den Master nach: verbindet sich mit 127.0.0.1:<port> und sendet DmxFrame-Nachrichten
(4 Bytes Länge little-endian + Protobuf). Die Protobuf-Kodierung ist hier von Hand
geschrieben, damit das Werkzeug unabhängig vom Produktcode bleibt (Orakel!).

Beispiele:
  tools/dmx_link_send.py 6000 --universe 1 --fill 255
  tools/dmx_link_send.py 6000 --universe 1 --ramp --hz 44 --seconds 5
  tools/dmx_link_send.py 6000 --mode bytewise          # jedes Byte einzeln (TCP-Stückelung)
  tools/dmx_link_send.py 6000 --mode zero-length       # Protokollfehler: Verbindung muss zugehen
  tools/dmx_link_send.py 6000 --mode oversize          # Protokollfehler: Länge 4 GiB
  tools/dmx_link_send.py 6000 --mode wrong-size        # Inhalt ungültig: Verbindung bleibt offen
  tools/dmx_link_send.py 6000 --mode bad-universe      # Universum 40000: abgelehnt, Verbindung offen
  tools/dmx_link_send.py 6000 --mode bad-proto         # Rahmen ok, Inhalt Müll: übersprungen
  tools/dmx_link_send.py 6000 --mode half-frame        # halber Rahmen, dann trennen
"""
import argparse
import socket
import struct
import sys
import time


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def dmx_frame(universe: int, data: bytes) -> bytes:
    out = b""
    if universe != 0:
        out += b"\x08" + varint(universe)
    if data:
        out += b"\x12" + varint(len(data)) + data
    return out


def framed(payload: bytes) -> bytes:
    return struct.pack("<I", len(payload)) + payload


def closed_by_peer(sock: socket.socket, wait: float) -> bool:
    sock.settimeout(wait)
    try:
        return sock.recv(64) == b""
    except socket.timeout:
        return False
    except OSError:
        return True


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("port", type=int)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--universe", type=int, default=1)
    ap.add_argument("--fill", type=int, default=0, help="alle 512 Bytes auf diesen Wert (0..255)")
    ap.add_argument("--ramp", action="store_true", help="Wert läuft 0..255 hoch und wieder runter")
    ap.add_argument("--hz", type=float, default=44.0)
    ap.add_argument("--seconds", type=float, default=0.0, help="0 = ein einzelner Rahmen")
    ap.add_argument("--mode", default="normal",
                    choices=["normal", "bytewise", "zero-length", "oversize", "wrong-size",
                             "bad-universe", "bad-proto", "half-frame"])
    a = ap.parse_args()

    s = socket.create_connection((a.host, a.port), timeout=3)
    good = framed(dmx_frame(a.universe, bytes([a.fill & 0xFF]) * 512))
    print(f"verbunden mit {a.host}:{a.port}, Modus {a.mode}")

    if a.mode == "bytewise":
        for b in good:
            s.sendall(bytes([b]))
            time.sleep(0.001)
        print(f"{len(good)} Bytes einzeln gesendet")
    elif a.mode == "zero-length":
        s.sendall(b"\x00\x00\x00\x00")
        print("Verbindung vom Submaster geschlossen:", closed_by_peer(s, 2))
    elif a.mode == "oversize":
        s.sendall(b"\xff\xff\xff\xff")
        print("Verbindung vom Submaster geschlossen:", closed_by_peer(s, 2))
    elif a.mode == "wrong-size":
        s.sendall(framed(dmx_frame(a.universe, bytes(100))) + good)
        print("Verbindung noch offen (erwartet: ja):", not closed_by_peer(s, 0.5))
    elif a.mode == "bad-universe":
        s.sendall(framed(dmx_frame(40000, bytes(512))) + good)
        print("Verbindung noch offen (erwartet: ja):", not closed_by_peer(s, 0.5))
    elif a.mode == "bad-proto":
        s.sendall(framed(b"\xff\xff\xff") + good)
        print("Verbindung noch offen (erwartet: ja):", not closed_by_peer(s, 0.5))
    elif a.mode == "half-frame":
        s.sendall(good[: len(good) // 2])
        time.sleep(0.2)
        print("halber Rahmen gesendet, trenne jetzt")
    else:
        if a.seconds <= 0:
            s.sendall(good)
            print("1 Rahmen gesendet")
        else:
            n, period, t0, i = 0, 1.0 / a.hz, time.monotonic(), 0
            while time.monotonic() - t0 < a.seconds:
                v = a.fill & 0xFF
                if a.ramp:
                    phase = i % 510
                    v = phase if phase < 256 else 510 - phase
                s.sendall(framed(dmx_frame(a.universe, bytes([v]) * 512)))
                n += 1
                i += 1
                time.sleep(max(0.0, t0 + i * period - time.monotonic()))
            print(f"{n} Rahmen in {a.seconds:.1f} s gesendet")
    s.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
