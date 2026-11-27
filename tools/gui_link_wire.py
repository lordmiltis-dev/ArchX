"""Handgeschriebene Protobuf-Kodierung für schema/gui_link.proto (nur für Tools und Referenzwerte).

Bewusst unabhängig von protobuf: Die Werte dienen als unabhängiges Orakel für die Tests.
proto3: Felder mit Standardwert (0, "", 0.0) werden nicht geschrieben; ein gesetztes oneof
mit leerer Nachricht wird trotzdem als Tag + Länge 0 geschrieben.
"""
import struct


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


def tag(field: int, wire_type: int) -> bytes:
    return varint((field << 3) | wire_type)


def ld(field: int, payload: bytes) -> bytes:        # length-delimited
    return tag(field, 2) + varint(len(payload)) + payload


def f32(field: int, v: float) -> bytes:
    return tag(field, 5) + struct.pack("<f", v)


def _patch_attr_value(patch: int, attr: str, value: float) -> bytes:
    out = b""
    if patch:
        out += tag(1, 0) + varint(patch)
    if attr:
        out += ld(2, attr.encode())
    if value != 0.0:
        out += f32(3, value)
    return out


def encode_set_value(patch: int, attr: str, value: float) -> bytes:        # GuiToMaster
    return ld(1, _patch_attr_value(patch, attr, value))


def encode_value_changed(patch: int, attr: str, value: float) -> bytes:    # MasterToGui
    return ld(2, _patch_attr_value(patch, attr, value))


def _attribute(name: str, value: float) -> bytes:
    out = ld(1, name.encode()) if name else b""
    if value != 0.0:
        out += f32(2, value)
    return out


def _fixture(patch: int, name: str, universe: int, address: int, attrs) -> bytes:
    out = b""
    if patch:
        out += tag(1, 0) + varint(patch)
    if name:
        out += ld(2, name.encode())
    if universe:
        out += tag(3, 0) + varint(universe)
    if address:
        out += tag(4, 0) + varint(address)
    for n, v in attrs:
        out += ld(5, _attribute(n, v))
    return out


def encode_show_view(fixtures) -> bytes:                                   # MasterToGui
    """fixtures: Liste von (patch_id, name, universe, address, [(attr, value), ...])"""
    inner = b"".join(ld(1, _fixture(*f)) for f in fixtures)
    return ld(1, inner)


def framed(payload: bytes) -> bytes:
    return struct.pack("<I", len(payload)) + payload


# ------------------------------------------------------------------ Lesen (für fake_master)
def read_varint(buf: bytes, i: int):
    v, shift = 0, 0
    while True:
        b = buf[i]
        i += 1
        v |= (b & 0x7F) << shift
        if not b & 0x80:
            return v, i
        shift += 7


def decode_gui_to_master(payload: bytes):
    """Gibt (patch_id, attribute, value) für SetValue zurück, sonst None."""
    try:
        t, i = read_varint(payload, 0)
        if t != ((1 << 3) | 2):
            return None
        n, i = read_varint(payload, i)
        inner = payload[i:i + n]
        patch, attr, value, j = 0, "", 0.0, 0
        while j < len(inner):
            t, j = read_varint(inner, j)
            field, wt = t >> 3, t & 7
            if field == 1 and wt == 0:
                patch, j = read_varint(inner, j)
            elif field == 2 and wt == 2:
                n, j = read_varint(inner, j)
                attr = inner[j:j + n].decode()
                j += n
            elif field == 3 and wt == 5:
                value = struct.unpack("<f", inner[j:j + 4])[0]
                j += 4
            else:
                return None
        return patch, attr, value
    except (IndexError, struct.error, UnicodeDecodeError):
        return None
