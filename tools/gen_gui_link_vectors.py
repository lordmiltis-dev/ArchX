#!/usr/bin/env python3
"""Erzeugt schema/testdata/gui_link_vectors.txt (Referenzwerte für die GUI-Tests).

Zeilenformat ('#' = Kommentar):
  cmd|name|patch|attr|value|wire_hex        GuiToMaster.set_value
  changed|name|patch|attr|value|wire_hex    MasterToGui.value_changed
  view|name|spec|wire_hex                   MasterToGui.show_view
spec: Fixtures durch ';' getrennt, Fixture = patch:name:universe:address:attr=wert,attr=wert
      ('-' = keine Fixtures). Namen enthalten keines der Zeichen : ; , =
Alle Werte sind in float32 exakt darstellbar (0, 0.25, 0.5, 1).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gui_link_wire as w  # noqa: E402

CMDS = [
    ("set_basic", 7, "Rot", 0.5),
    ("set_patch_0_is_omitted", 0, "Rot", 0.5),
    ("set_value_0_is_omitted", 3, "Gruen", 0.0),
    ("set_patch_300_two_byte_varint", 300, "Blau", 1.0),
]
CHANGED = [
    ("changed_basic", 1, "Rot", 0.25),
    ("changed_value_0_is_omitted", 2, "Dimmer", 0.0),
]
VIEWS = [
    ("view_empty", "-"),
    ("view_one_rgb", "1:Par1:0:1:Rot=1,Gruen=0.5,Blau=0"),
    ("view_two_fixtures", "1:Par1:0:1:Rot=0;2:Par2:1:512:Dimmer=0.25"),
    ("view_fixture_without_attributes", "5:Nebel:0:100:"),
    ("view_all_defaults_omitted", "0::0:0:"),
]


def parse_spec(spec: str):
    if spec == "-":
        return []
    out = []
    for f in spec.split(";"):
        patch, name, universe, address, attrs = f.split(":")
        pairs = [(a.split("=")[0], float(a.split("=")[1])) for a in attrs.split(",") if a]
        out.append((int(patch), name, int(universe), int(address), pairs))
    return out


def main() -> None:
    root = pathlib.Path(__file__).resolve().parent.parent
    lines = [
        "# GENERIERT von tools/gen_gui_link_vectors.py - nicht von Hand ändern.",
        "# cmd|name|patch|attr|value|wire_hex   changed|name|patch|attr|value|wire_hex   view|name|spec|wire_hex",
    ]
    for n, p, a, v in CMDS:
        lines.append(f"cmd|{n}|{p}|{a}|{v}|{w.encode_set_value(p, a, v).hex()}")
    for n, p, a, v in CHANGED:
        lines.append(f"changed|{n}|{p}|{a}|{v}|{w.encode_value_changed(p, a, v).hex()}")
    for n, spec in VIEWS:
        lines.append(f"view|{n}|{spec}|{w.encode_show_view(parse_spec(spec)).hex()}")
    out = root / "schema" / "testdata" / "gui_link_vectors.txt"
    out.write_text("\n".join(lines) + "\n")
    print(f"geschrieben: {out} ({len(CMDS)} cmd, {len(CHANGED)} changed, {len(VIEWS)} view)")


if __name__ == "__main__":
    main()
