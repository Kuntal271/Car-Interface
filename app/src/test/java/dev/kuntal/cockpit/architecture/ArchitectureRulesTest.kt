package dev.kuntal.cockpit.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dependency-direction rules for the code base, checked on the source text (no extra library, runs in the normal unit-test task).
 *
 * Target layering (docs/ARCHITECTURE.md):  ui  ->  data / platform adapters  ->  :vehicle   (never the other way round)
 *
 * Known violations are listed in [KNOWN] with the finding that tracks them. The test is a RATCHET: a NEW violation fails it, and so
 * does a known violation that was fixed but is still listed, so the list can only shrink. The refactor plan removes the entries.
 */
class ArchitectureRulesTest {

    private data class Src(val rel: String, val text: String) {
        val imports: List<String> by lazy { IMPORT.findAll(text).map { it.groupValues[1] }.toList() }
    }

    private fun repoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").exists()) d = d.parentFile
        return requireNotNull(d) { "settings.gradle.kts not found above ${System.getProperty("user.dir")}" }
    }

    private fun sources(rel: String): List<Src> {
        val root = File(repoRoot(), rel)
        assertTrue("missing source root $root", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { Src(it.relativeTo(root).path.replace(File.separatorChar, '/'), it.readText()) }.toList()
    }

    private val app by lazy { sources("app/src/main/java/dev/kuntal/cockpit") }
    private val vehicle by lazy { sources("vehicle/src/main/kotlin/dev/kuntal/cockpit/vehicle") }

    private fun Src.layer(): String = when {
        rel.startsWith("ui/") -> "ui"
        rel.startsWith("data/") -> "data"
        rel.startsWith("perf/") -> "perf"
        rel.startsWith("source/") -> "source"
        else -> "root"
    }

    /** "rule: file" strings for every violation of one rule. */
    private fun violations(rule: String, files: List<Src>, bad: (Src) -> Boolean) = files.filter(bad).map { "$rule: ${it.rel}" }.toSet()

    @Test fun layeringRulesHoldExceptForTheKnownViolations() {
        val found = buildSet {
            // :vehicle is pure Kotlin/JVM: nothing from Android may leak into the protocol layer.
            addAll(violations("vehicle-is-pure", vehicle) { s -> s.imports.any { it.startsWith("android.") || it.startsWith("androidx.") } })
            // data/ is pure logic that runs in plain JVM tests: no Android, no Compose, and it never reaches up into ui/source/perf/root.
            addAll(violations("data-is-pure", app.filter { it.layer() == "data" }) { s ->
                s.imports.any { it.startsWith("android.") || it.startsWith("androidx.") || it.startsWith("kotlinx.coroutines.Dispatchers") }
            })
            addAll(violations("data-does-not-reach-up", app.filter { it.layer() == "data" }) { s ->
                s.imports.any { i -> UP.any { i.startsWith("dev.kuntal.cockpit.$it") } || ROOT_CLASSES.any { i == "dev.kuntal.cockpit.$it" } }
            })
            // Frame statistics and the governor are pure; only FrameMeter touches the platform.
            addAll(violations("perf-logic-is-pure", app.filter { it.rel in setOf("perf/FrameStats.kt", "perf/PerfGovernor.kt") }) { s ->
                s.imports.any { it.startsWith("android.") }
            })
            // ui/ talks to data/ and to adapters through the composition root, never to platform sources or debug globals directly.
            addAll(violations("ui-not-source", app.filter { it.layer() == "ui" }) { s -> s.imports.any { it.startsWith("dev.kuntal.cockpit.source.") } })
            addAll(violations("ui-not-debug-hooks", app.filter { it.layer() == "ui" }) { s -> s.imports.contains("dev.kuntal.cockpit.DebugHooks") })
            // Time and dispatchers are injected where logic is tested: no wall-clock reads in domain code.
            addAll(violations("no-wall-clock-in-logic", app.filter { it.layer() == "data" } + vehicle) { s -> WALL_CLOCK.containsMatchIn(s.text) })
            // Colours come from the palette. FordMark carries the trademark colours of the logo itself.
            addAll(violations("no-literal-colours", app.filter { it.rel != "ui/theme/Theme.kt" }) { s -> LITERAL_COLOUR.containsMatchIn(s.text) })
            // No fire-and-forget scopes.
            addAll(violations("no-global-scope", app + vehicle) { s -> "GlobalScope" in s.text })
        }
        val unexpected = found - KNOWN.keys
        val fixed = KNOWN.keys - found
        assertTrue("NEW architecture violations (fix them, do not add to KNOWN): $unexpected", unexpected.isEmpty())
        assertTrue("These known violations are gone: remove them from KNOWN so the ratchet tightens: $fixed", fixed.isEmpty())
        assertEquals(KNOWN.keys, found)
    }

    @Test fun sourceRootsWereActuallyScanned() {
        // Guards the guard: a wrong path would make every rule above pass vacuously.
        assertTrue("only ${app.size} app files found", app.size >= 30)
        assertTrue("only ${vehicle.size} vehicle files found", vehicle.size >= 9)
    }

    private companion object {
        val IMPORT = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE)
        val UP = listOf("ui.", "source.", "perf.")
        val ROOT_CLASSES = listOf("DebugHooks", "SettingsStore", "CockpitViewModel", "CockpitApp", "MainActivity")
        val WALL_CLOCK = Regex("""System\.(currentTimeMillis|nanoTime)\(\)|LocalDateTime\.now\(|Instant\.now\(|LocalTime\.now\(""")
        val LITERAL_COLOUR = Regex("""Color\(0x""")

        /** Violation -> the finding that tracks it. */
        val KNOWN = mapOf(
            "ui-not-source: ui/screens/SettingsScreen.kt" to "ARCH-06  SettingsScreen calls source.pairedAdapters() directly",
            "ui-not-debug-hooks: ui/shell/Shell.kt" to "ARCH-02  StatusBar reads DebugHooks.snapshot/CLOCK/DATE/VOLUME in production code",
            "ui-not-debug-hooks: ui/shell/QuickControls.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/shell/SystemVolume.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/screens/StandbyScreen.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/screens/RadioScreen.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/screens/ParkingScreen.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/screens/PhoneScreens.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/screens/ProjectionScreen.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/screens/DriveVariantsScreen.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/components/Layers.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "ui-not-debug-hooks: ui/components/Extras.kt" to "ARCH-02  reads DebugHooks (snapshot fixtures / benchmark switches) in production UI; moves to src/debug in refactor step S2",
            "no-wall-clock-in-logic: data/Trips.kt" to "ARCH-03  reads the wall clock directly; inject a Clock in refactor step S4",
            "no-wall-clock-in-logic: data/CockpitData.kt" to "ARCH-03  reads the wall clock directly; inject a Clock in refactor step S4",
            "no-wall-clock-in-logic: VehicleSample.kt" to "ARCH-03  reads the wall clock directly; inject a Clock in refactor step S4",
            "no-global-scope: Blocking.kt" to "ARCH-05  cancellableBlocking detaches the blocking read on GlobalScope so cancel returns at once (the socket is closed on cancel, so the thread ends); replace with an owned executor in refactor step S9",
            "no-literal-colours: ui/components/FordMark.kt" to "INFO     the Blue Oval keeps its own trademark navy; allowed on purpose",
        )
    }
}
