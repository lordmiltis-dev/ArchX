mod container;
pub mod archx {
        include!(concat!(env!("OUT_DIR"), "/archx.rs"));
}


fn main() {
    println!("ArchX Savefile-Interpreter");
}

#[cfg(test)]
mod tests {
    use prost::Message;
use super::archx::{Attribute, Precedence, Showfile};
    use std::fs;

    // Liest die Datei, die das Kotlin-Programm geschrieben hat, und prüft die Werte
    #[test]
    fn reads_showfile_written_by_kotlin() {
        let bytes = fs::read("../schema/testdata/showfile_example.bin")
            .expect("Testdatei fehlt: zuerst in Kotlin exportToBinary ausführen");
        let show = Showfile::decode(bytes.as_slice()).expect("Die Bytes sind kein gültiges Showfile");

        assert_eq!(show.name, "Testshow");
        assert_eq!(show.next_patch_id, 3);

        // Ein Typ, zwei Patches
        assert_eq!(show.fixture_types.len(), 1);
        assert_eq!(show.patch.len(), 2);
        assert_eq!(show.patch[0].fixture_type_id, show.patch[1].fixture_type_id);
        assert_eq!(show.patch[0].address, 1);
        assert_eq!(show.patch[1].address, 7);

        // Der Typ und seine Kanäle
        let typ = &show.fixture_types[0];
        assert_eq!(typ.name, "Platinum Mini TRI-PAR");
        assert_eq!(typ.channel_count, 6);
        assert_eq!(typ.channels.len(), 6);

        let intensity = &typ.channels[0];
        assert_eq!(intensity.offset, 1);
        assert_eq!(intensity.attribute(), Attribute::Intensity);
        assert_eq!(intensity.highlight_value, 255);
        assert_eq!(intensity.precedence(), Precedence::Htp);
    }
}