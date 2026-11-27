#!/usr/bin/env python3
"""Fake-Master zum Handtesten der GUI (ohne echten Master).

Lauscht auf 127.0.0.1:<port>, schickt nach dem Verbinden eine ShowView (drei Fixtures) und
druckt jedes SetValue der GUI. Mit --echo kommt jeder Wert als ValueChanged zurück (so,
als hätte ein Pult ihn gesetzt).

Beispiele:
  tools/fake_master.py 46100
  tools/fake_master.py 46100 --echo               # Werte zurückspiegeln
  tools/fake_master.py 46100 --drop-after 5       # nach 5 Befehlen Verbindung kappen (Reconnect testen)
  tools/fake_master.py 46100 --wiggle             # schickt jede Sekunde ValueChanged (Pult-Simulation)
  tools/fake_master.py 46100 --garbage            # nach der ShowView ein kaputter Rahmen (Länge 0)
"""
import argparse
import socket
import struct
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gui_link_wire as w  # noqa: E402

FIXTURES = [
    (1, "Par 1", 0, 1, [("Rot", 1.0), ("Gruen", 0.5), ("Blau", 0.0)]),
    (2, "Par 2", 0, 4, [("Rot", 0.0), ("Gruen", 0.0), ("Blau", 0.25)]),
    (3, "Dimmer", 0, 7, [("Dimmer", 0.0)]),
]


def recv_exact(conn: socket.socket, n: int):
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def serve(conn: socket.socket, a) -> None:
    conn.sendall(w.framed(w.encode_show_view(FIXTURES)))
    print("  ShowView gesendet")
    if a.garbage:
        conn.sendall(b"\x00\x00\x00\x00")
        print("  kaputten Rahmen (Länge 0) gesendet")
    stop = threading.Event()
    if a.wiggle:
        def wiggle():
            v = 0.0
            while not stop.wait(1.0):
                v = (v + 0.25) % 1.25
                try:
                    conn.sendall(w.framed(w.encode_value_changed(3, "Dimmer", min(v, 1.0))))
                except OSError:
                    return
        threading.Thread(target=wiggle, daemon=True).start()
    n = 0
    try:
        while True:
            header = recv_exact(conn, 4)
            if header is None:
                print("  GUI hat die Verbindung geschlossen")
                return
            (length,) = struct.unpack("<I", header)
            payload = recv_exact(conn, length)
            if payload is None:
                return
            cmd = w.decode_gui_to_master(payload)
            n += 1
            print(f"  SetValue #{n}: {cmd}" if cmd else f"  unlesbarer Befehl: {payload.hex()}")
            if cmd and a.echo:
                conn.sendall(w.framed(w.encode_value_changed(*cmd)))
            if a.drop_after and n >= a.drop_after:
                print(f"  kappe die Verbindung nach {n} Befehlen")
                return
    finally:
        stop.set()
        conn.close()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("port", type=int)
    ap.add_argument("--echo", action="store_true")
    ap.add_argument("--drop-after", type=int, default=0)
    ap.add_argument("--wiggle", action="store_true")
    ap.add_argument("--garbage", action="store_true")
    a = ap.parse_args()
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", a.port))
    srv.listen(4)
    print(f"Fake-Master wartet auf 127.0.0.1:{a.port} (Strg+C beendet)")
    try:
        while True:
            conn, addr = srv.accept()
            print(f"GUI verbunden von {addr}")
            serve(conn, a)
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main())
