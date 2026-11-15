package dev.archx.master

import dev.archx.schema.Attribute
import dev.archx.schema.Showfile

/**
 * Rechnet aus der Show und den eingestellten Werten die DMX-Rahmen aus.
 *
 * Werte sind Zahlen von 0.0 bis 1.0. Mehrere Quellen (source) dürfen Werte für denselben Parameter
 * liefern. Der Parameter wird bestimmt durch: Patch, Zelle und Attribut.
 *
 * Zelle 0 bedeutet: die gemeinsamen Kanäle des Fixtures. Zelle 1, 2, ... sind die Zellen des Typs
 * (SubFixtureDef.index).
 */
class ValueEngine {

    /** Übernimmt eine Show. Alle bisher eingestellten Werte werden gelöscht. */
    fun load(show: Showfile) {
        TODO("hier kommt dein Code hin")
    }

    /**
     * Stellt einen Wert ein. Der Wert wird auf 0.0 bis 1.0 begrenzt.
     * Wirft IllegalArgumentException, wenn es den Patch oder die Zelle nicht gibt oder der Patch
     * in diesem Bereich keinen Kanal mit diesem Attribut hat.
     * Ausnahme: ATTRIBUTE_INTENSITY ist bei einem Patch mit virtualDimmer auch ohne Dimmerkanal erlaubt.
     */
    fun setValue(source: Int, patchId: Int, attribute: Attribute, value: Float, cell: Int = 0) {
        TODO("hier kommt dein Code hin")
    }

    /** Entfernt den Wert einer Quelle. Wurde nie ein Wert gesetzt, passiert nichts. */
    fun clearValue(source: Int, patchId: Int, attribute: Attribute, cell: Int = 0) {
        TODO("hier kommt dein Code hin")
    }

    /**
     * Der Rahmen eines Universums: immer genau 512 Bytes. Index 0 ist die DMX-Adresse 1.
     * Gibt es im Universum keinen Patch, sind alle Bytes 0.
     */
    fun frame(universe: Int): ByteArray {
        TODO("hier kommt dein Code hin")
    }
}
