package archx.gui

import archx.gui.guilink.AttributeView
import archx.gui.guilink.FixtureView
import archx.gui.panel.FaderPanelModel
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class Sent(val patch: Int, val attr: String, val value: Float)

private fun fixture(patch: Int, name: String, vararg attrs: Pair<String, Float>) =
    FixtureView(patch, name, 0, patch, attrs.map { AttributeView(it.first, it.second) })

class FaderPanelModelTest {
    private val sent = CopyOnWriteArrayList<Sent>()
    private val changes = AtomicInteger()
    private val model = FaderPanelModel { p, a, v -> sent += Sent(p, a, v) }.also { m ->
        m.onChanged = { changes.incrementAndGet() }
    }

    private fun rgbShow() = listOf(
        fixture(1, "Par 1", "Rot" to 1f, "Gruen" to 0.5f),
        fixture(2, "Par 2", "Dimmer" to 0.25f),
    )

    // ------------------------------------------------------------ Show und Werte

    @Test
    fun applyShowViewStoresFixturesInOrderWithTheirValues() {
        model.applyShowView(rgbShow())
        assertEquals(listOf(1, 2), model.fixtures.map { it.patchId })
        assertEquals(1f, model.valueOf(1, "Rot"))
        assertEquals(0.5f, model.valueOf(1, "Gruen"))
        assertEquals(0.25f, model.valueOf(2, "Dimmer"))
    }

    @Test
    fun valueOfUnknownReturnsNull() {
        model.applyShowView(rgbShow())
        assertNull(model.valueOf(99, "Rot"))
        assertNull(model.valueOf(1, "Blau"))
    }

    @Test
    fun applyShowViewReplacesEverything() {
        model.applyShowView(rgbShow())
        model.applyShowView(listOf(fixture(5, "Neu", "Dimmer" to 1f)))
        assertEquals(listOf(5), model.fixtures.map { it.patchId })
        assertNull(model.valueOf(1, "Rot"))
        assertEquals(1f, model.valueOf(5, "Dimmer"))
    }

    @Test
    fun applyShowViewWithDuplicatesKeepsTheFirst() {
        model.applyShowView(
            listOf(
                fixture(1, "Erster", "Rot" to 0.1f, "Rot" to 0.9f),
                fixture(1, "Zweiter", "Rot" to 0.7f),
            ),
        )
        assertEquals(1, model.fixtures.size)
        assertEquals("Erster", model.fixtures[0].name)
        assertEquals(0.1f, model.valueOf(1, "Rot"))
    }

    @Test
    fun applyShowViewClampsValuesFromTheMaster() {
        model.applyShowView(listOf(fixture(1, "P", "A" to 1.5f, "B" to -0.5f)))
        assertEquals(1f, model.valueOf(1, "A"))
        assertEquals(0f, model.valueOf(1, "B"))
    }

    @Test
    fun fixturesIsASnapshotCopy() {
        model.applyShowView(rgbShow())
        val before = model.fixtures
        model.applyShowView(emptyList())
        assertEquals(2, before.size)
        assertEquals(0, model.fixtures.size)
    }

    // ------------------------------------------------------------ Nutzer bewegt Regler

    @Test
    fun userChangeSendsExactlyOnceAndUpdatesTheValue() {
        model.applyShowView(rgbShow())
        model.setValueFromUser(1, "Rot", 0.25f)
        assertEquals(listOf(Sent(1, "Rot", 0.25f)), sent.toList())
        assertEquals(0.25f, model.valueOf(1, "Rot"))
    }

    @Test
    fun userValuesAreClampedBeforeSending() {
        model.applyShowView(rgbShow())
        model.setValueFromUser(1, "Rot", 1.5f)
        model.setValueFromUser(1, "Gruen", -3f)
        assertEquals(listOf(Sent(1, "Gruen", 0f)), sent.toList())   // Rot stand schon auf 1.0: keine Änderung
        assertEquals(1f, model.valueOf(1, "Rot"))
        assertEquals(0f, model.valueOf(1, "Gruen"))
    }

    @Test
    fun userNanAndInfinityAreIgnored() {
        model.applyShowView(rgbShow())
        model.setValueFromUser(1, "Rot", Float.NaN)
        model.setValueFromUser(1, "Rot", Float.POSITIVE_INFINITY)
        model.setValueFromUser(1, "Rot", Float.NEGATIVE_INFINITY)
        assertTrue(sent.isEmpty())
        assertEquals(1f, model.valueOf(1, "Rot"))
    }

    @Test
    fun unchangedValueIsNotSentAgain() {
        model.applyShowView(rgbShow())
        model.setValueFromUser(1, "Gruen", 0.5f)   // ist schon 0.5
        assertTrue(sent.isEmpty())
        model.setValueFromUser(1, "Gruen", 0.75f)
        model.setValueFromUser(1, "Gruen", 0.75f)
        assertEquals(1, sent.size)
    }

    @Test
    fun unknownFixtureOrAttributeFromUserIsIgnored() {
        model.applyShowView(rgbShow())
        model.setValueFromUser(99, "Rot", 0.5f)
        model.setValueFromUser(1, "Blau", 0.5f)
        assertTrue(sent.isEmpty())
    }

    // ------------------------------------------------------------ Master ändert Wert

    @Test
    fun remoteChangeUpdatesTheValueButNeverSends() {
        model.applyShowView(rgbShow())
        model.onRemoteValueChanged(2, "Dimmer", 0.9f)
        assertEquals(0.9f, model.valueOf(2, "Dimmer"))
        assertTrue(sent.isEmpty())   // sonst Endlosschleife Master <-> GUI
    }

    @Test
    fun remoteChangeIsClampedAndUnknownTargetsAreIgnored() {
        model.applyShowView(rgbShow())
        model.onRemoteValueChanged(2, "Dimmer", 7f)
        assertEquals(1f, model.valueOf(2, "Dimmer"))
        model.onRemoteValueChanged(42, "Rot", 0.5f)      // kein Absturz
        model.onRemoteValueChanged(1, "Blau", 0.5f)
        assertNull(model.valueOf(42, "Rot"))
        assertNull(model.valueOf(1, "Blau"))
    }

    // ------------------------------------------------------------ onChanged / Verbindung

    @Test
    fun onChangedFiresOnRealChangesOnly() {
        model.applyShowView(rgbShow())
        val afterShow = changes.get()
        assertTrue(afterShow >= 1)
        model.setValueFromUser(1, "Rot", 1f)        // unverändert
        model.setValueFromUser(99, "Rot", 0.5f)     // unbekannt
        model.setValueFromUser(1, "Rot", Float.NaN) // ungültig
        model.onRemoteValueChanged(42, "X", 0.5f)   // unbekannt
        assertEquals(afterShow, changes.get())
        model.setValueFromUser(1, "Rot", 0.5f)
        assertEquals(afterShow + 1, changes.get())
        model.onRemoteValueChanged(1, "Rot", 0.75f)
        assertEquals(afterShow + 2, changes.get())
    }

    @Test
    fun connectedFlagTogglesAndNotifiesOnlyOnChange() {
        assertFalse(model.connected)
        model.setConnected(true)
        assertTrue(model.connected)
        val n = changes.get()
        model.setConnected(true)    // nichts Neues
        assertEquals(n, changes.get())
        model.setConnected(false)
        assertFalse(model.connected)
        assertEquals(n + 1, changes.get())
    }

    @Test
    fun onChangedIsNotCalledWhileHoldingAModelLock() {
        // Ein Listener, der selbst ins Modell greift, darf nicht blockieren (Deadlock-Falle).
        model.applyShowView(rgbShow())
        val seen = CopyOnWriteArrayList<Float?>()
        model.onChanged = { seen += model.valueOf(1, "Rot"); model.fixtures }
        val t = Thread { model.setValueFromUser(1, "Rot", 0.1f) }
        t.start()
        t.join(2000)
        assertFalse(t.isAlive, "setValueFromUser hängt - wird onChanged unter einem Lock aufgerufen?")
        assertEquals(listOf<Float?>(0.1f), seen.toList())
    }

    // ------------------------------------------------------------ Threads

    @Test
    fun survivesConcurrentUserAndMasterUpdates() {
        model.applyShowView((1..4).map { fixture(it, "P$it", "A" to 0f) })
        val errors = CopyOnWriteArrayList<Throwable>()
        val threads = (1..4).map { id ->
            Thread {
                try {
                    repeat(5_000) { i ->
                        val v = (i % 100) / 100f
                        if (i % 2 == 0) model.setValueFromUser(id, "A", v) else model.onRemoteValueChanged(id, "A", v)
                    }
                    model.setValueFromUser(id, "A", 0.99f)
                } catch (t: Throwable) {
                    errors += t
                }
            }
        }
        val reader = Thread {
            try {
                repeat(5_000) { model.fixtures; model.valueOf(1, "A") }
            } catch (t: Throwable) {
                errors += t
            }
        }
        (threads + reader).forEach { it.start() }
        (threads + reader).forEach { it.join(10_000) }
        assertTrue(errors.isEmpty(), "Fehler: $errors")
        for (id in 1..4) assertEquals(0.99f, model.valueOf(id, "A"))
    }
}
