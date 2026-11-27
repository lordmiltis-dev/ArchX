package archx.gui.ui

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Einstiegspunkt der GUI. Von Hand getestet (Fenster), die Logik steckt in FaderPanelModel/GuiLinkClient.
 *
 * Aufruf z. B.: `./gradlew run --args="46100"` (Port des Masters bzw. von tools/fake_master.py).
 */
fun main(args: Array<String>) = application {
    val port = args.firstOrNull()?.toIntOrNull() ?: 46100   // TODO: Standardport festlegen (ADR 0008)

    // TODO: 1. val model = FaderPanelModel { patch, attr, value -> client.sendCommand(SetValue(...)) }
    //       2. val client = GuiLinkClient(port, listener) mit einem GuiLinkListener, der
    //          onConnected/onDisconnected -> model.setConnected, onEvent -> applyShowView bzw.
    //          onRemoteValueChanged aufruft.
    //       3. client.start(); beim Schließen des Fensters client.close() (DisposableEffect / onCloseRequest).
    //       4. model.onChanged -> Compose-State hochzählen, damit die Oberfläche neu zeichnet.
    //          Achtung: onChanged kommt aus dem Netzwerk-Thread; ein mutableStateOf darf von dort
    //          beschrieben werden (Compose ist dafür gebaut), Fenster/Composables aber nicht anfassen.
    Window(onCloseRequest = ::exitApplication, title = "ArchX GUI") {
        FaderPanel()
    }
}
