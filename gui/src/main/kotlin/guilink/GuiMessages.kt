package archx.gui.guilink

// Hilfe (fertig): Datenklassen der GUI-Seite, unabhängig vom generierten Protobuf-Code.
// Der Rest der GUI (FaderPanelModel, UI) kennt nur diese Typen.

data class AttributeView(val name: String, val value: Float)

data class FixtureView(
    val patchId: Int,
    val name: String,
    val universe: Int,
    val address: Int,
    val attributes: List<AttributeView>,
)

/** Was der Master der GUI schickt. */
sealed interface MasterEvent {
    /** Kompletter Stand, direkt nach dem Verbinden. */
    data class ShowView(val fixtures: List<FixtureView>) : MasterEvent

    /** Ein einzelner Wert hat sich geändert (z. B. durch ein Pult). */
    data class ValueChanged(val patchId: Int, val attribute: String, val value: Float) : MasterEvent
}

/** Was die GUI dem Master schickt. */
sealed interface GuiCommand {
    data class SetValue(val patchId: Int, val attribute: String, val value: Float) : GuiCommand
}

/** Rückmeldungen des [GuiLinkClient]. Alle Aufrufe kommen aus dem Netzwerk-Thread, NICHT aus dem UI-Thread. */
interface GuiLinkListener {
    fun onConnected() {}
    fun onDisconnected() {}
    fun onEvent(event: MasterEvent) {}
}
