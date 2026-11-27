package archx.gui.panel

import archx.gui.guilink.FixtureView

/**
 * Zustand der Regler-Oberfläche, ohne Compose (dadurch ohne Fenster testbar).
 *
 * Regeln (jede wird von FaderPanelModelTest geprüft):
 *  - Werte liegen immer in 0.0..1.0 (außerhalb wird begrenzt; NaN/Unendlich vom Nutzer wird ignoriert).
 *  - [setValueFromUser] ruft [sendValue] genau einmal auf, aber nur wenn sich der Wert wirklich ändert,
 *    und nur für bekannte Fixtures/Attribute.
 *  - [onRemoteValueChanged] (Master -> GUI) ändert den Wert, ruft aber NIE [sendValue] auf
 *    (sonst Endlosschleife Master <-> GUI). Unbekannte Fixtures/Attribute werden ignoriert.
 *  - [applyShowView] ersetzt den kompletten Stand. Doppelte patchId: der erste Eintrag gilt;
 *    doppelte Attributnamen in einem Fixture: der erste gilt. Reihenfolge bleibt erhalten.
 *  - [onChanged] wird nach jeder echten Änderung aufgerufen (Show, Wert, Verbindungsstatus), nicht
 *    bei ignorierten Aufrufen und nicht bei unverändertem Wert.
 *  - Thread-sicher: Aufrufe kommen aus dem Netzwerk-Thread (Master-Events) und dem UI-Thread.
 *    [onChanged] wird NICHT unter einem Lock aufgerufen.
 *
 * @param sendValue geht später an GuiLinkClient.sendCommand.
 */
class FaderPanelModel(
    private val sendValue: (patchId: Int, attribute: String, value: Float) -> Unit,
) {
    /** Die UI hängt hier an: z. B. `version++` auf einem Compose-State. Darf aus jedem Thread kommen. */
    @Volatile var onChanged: () -> Unit = {}

    // TODO: Zustand (Vorschlag: lock, Liste der Fixtures in Reihenfolge, Map (patchId, attribute) -> Float).

    /** Aktuelle Fixtures in Anzeigereihenfolge (Kopie). Werte über [valueOf] lesen. */
    val fixtures: List<FixtureView> get() = TODO("FaderPanelModel.fixtures")

    /** true, solange die Verbindung zum Master steht. */
    val connected: Boolean get() = TODO("FaderPanelModel.connected")

    fun valueOf(patchId: Int, attribute: String): Float? {
        TODO("FaderPanelModel.valueOf")
    }

    fun applyShowView(view: List<FixtureView>) {
        TODO("FaderPanelModel.applyShowView")
    }

    fun setValueFromUser(patchId: Int, attribute: String, value: Float) {
        TODO("FaderPanelModel.setValueFromUser")
    }

    fun onRemoteValueChanged(patchId: Int, attribute: String, value: Float) {
        TODO("FaderPanelModel.onRemoteValueChanged")
    }

    fun setConnected(connected: Boolean) {
        TODO("FaderPanelModel.setConnected")
    }
}
