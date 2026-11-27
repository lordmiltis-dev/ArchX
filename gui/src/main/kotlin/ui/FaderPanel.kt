package archx.gui.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Das eigentliche Fenster: Statuszeile + eine Zeile pro Fixture mit einem Regler je Attribut.
 *
 * TODO (Parameter passend zu deiner Lösung in Main.kt, z. B. model: FaderPanelModel, version: Int):
 *  - Oben: "Verbunden" / "Getrennt - verbinde neu..." je nach model.connected.
 *  - Leere Liste: Hinweis "Keine Show vom Master empfangen".
 *  - Pro Fixture: Name, "Universum/Adresse", für jedes Attribut ein Slider (0f..1f), Wert =
 *    model.valueOf(...) ?: 0f, onValueChange = { model.setValueFromUser(patchId, name, it) }.
 *  - Scrollbar bei vielen Fixtures (LazyColumn).
 *  - Tipp: Slider auf `enabled = model.connected` setzen; getrennt sendet GuiLinkClient ohnehin nichts.
 *  - Tipp: Beim Ziehen kommen sehr viele onValueChange-Aufrufe; GuiLinkClient fasst sie pro Regler zusammen.
 */
@Composable
fun FaderPanel() {
    Text("TODO: FaderPanel")
}
