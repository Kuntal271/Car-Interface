package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

/**
 * Tries to get anything but "adapter setup" and "mode 01 read" through [CommandGuard] (docs/SECURITY.md, tools/security).
 * The pinned policy tables below are intentionally duplicated from the implementation: widening the whitelist must break a test,
 * because every new command class needs the owner's decision.
 */
class CommandGuardFuzzTest {
    /** The complete policy. Changing this list is a product decision (docs/SECURITY.md section "Command guard"). */
    private val policyAt = setOf(
        "ATZ", "ATI", "AT@1", "ATRV", "ATDP", "ATDPN", "ATE0", "ATE1", "ATL0", "ATL1", "ATS0", "ATS1", "ATH0", "ATH1",
        "ATAT0", "ATAT1", "ATAT2", "ATSP6", "ATCAF1",
    )

    /** What a real ELM327 does with a line: ignores spaces, is case-insensitive, treats CR as the end of a command. */
    private fun adapterWouldRun(wire: String): List<String> =
        wire.split('\r').map { it.filterNot { c -> c == ' ' }.uppercase() }.filter { it.isNotEmpty() }

    private fun isMode01Read(s: String) = s.length == 4 && s.startsWith("01") && s.drop(2).all { it in '0'..'9' || it in 'A'..'F' }

    // Every `AT` command of the ELM327 datasheets (v1.4b - v2.3, incl. the ones only some clones implement), with sample parameters.
    private val atCatalogue: List<String> = listOf(
        "AL", "AMC", "AMT00", "AMTFF", "AR", "AT0", "AT1", "AT2", "BD", "BI", "BRD23", "BRDFF", "BRT0A", "CAF0", "CAF1", "CEA", "CEA7E0",
        "CF7E8", "CFC0", "CFC1", "CM7F0", "CP18", "CRA", "CRA7E8", "CRA7E", "CS", "CSM0", "CSM1", "CT", "CV1234", "D", "D0", "D1", "DM1",
        "DP", "DPN", "E0", "E1", "F", "FCSD01000000", "FCSH7E0", "FCSM1", "FE", "FI", "H0", "H1", "I", "IB10", "IB96", "IFR0", "IFR1",
        "IGN", "IIA33", "JE", "JHF0", "JHF1", "JS", "JTM1", "JTM5", "KW", "KW0", "KW1", "L0", "L1", "LP", "M0", "M1", "MA", "MP0100",
        "MR7E8", "MT7E8", "NL", "PB0102", "PC", "PP", "PPS", "PP0CSV19", "PPFFOFF", "PPFFON", "PP0COFF", "R0", "R1", "RA7E8", "RD", "RS",
        "RTR", "RV", "S0", "S1", "SD00", "SH7E0", "SH7DF", "SH", "SI", "SP0", "SP1", "SP6", "SP7", "SPA", "SPB", "SPC", "SR7E8", "SS",
        "ST", "ST32", "STFF", "SW00", "SWFF", "TA13", "TP6", "TPA6", "V0", "V1", "WM", "WM1122", "WS", "@1", "@2", "@3", "BRDFF", "LP0",
    ).map { "AT$it" }.distinct()

    // ---- exact policy ----

    @Test fun allowedSetIsExactlyThePinnedPolicy() {
        assertEquals(policyAt, CommandGuard.AT_ALLOWED)
        policyAt.forEach { assertTrue(it, CommandGuard.isAllowed(it)) }
    }

    @Test fun everyDatasheetAtCommandOutsideThePolicyIsRefused() {
        val refused = atCatalogue.filter { it !in policyAt }
        assertTrue(refused.size > 100)
        refused.forEach { assertFalse("AT command must be refused: $it", CommandGuard.isAllowed(it)) }
    }

    @Test fun theDangerousClassesAreRefusedInEverySpelling() {
        // persistent adapter changes (ATPP*, baud), things that make the adapter transmit or choose its own frames (headers, addresses,
        // wake-up message, inits, protocol search, raw CAN framing), and modes that never return a prompt (monitor).
        val dangerous = listOf(
            "ATPP FF ON", "ATPP FF OFF", "ATPP 0C SV 19", "ATPPS", "ATPB0102", "ATBRD23", "ATBRT0A", "ATSH7E0", "ATSH7DF", "ATCP18",
            "ATTA13", "ATSR7E8", "ATWM1122", "ATSW00", "ATSI", "ATFI", "ATIB10", "ATIIA33", "ATKW0", "ATBI", "ATMA", "ATMR7E8", "ATMT7E8",
            "ATSP0", "ATSPA", "ATTP6", "ATTPA6", "ATCAF0", "ATV1", "ATJE", "ATJS", "ATCEA7E0", "ATCF7E8", "ATCM7F0", "ATCRA7E8", "ATFCSH7E0",
            "ATFCSD01000000", "ATFCSM1", "ATD", "ATWS", "ATLP", "ATST32", "ATPC", "ATCV1234", "ATRD", "ATSS", "ATFE", "ATAL", "ATNL", "ATM1",
        )
        for (base in dangerous) for (v in spellings(base)) {
            if (v in policyAt) continue
            assertFalse("must be refused: ${v.escaped()}", CommandGuard.isAllowed(v))
        }
    }

    @Test fun theSpaceBetweenAtAndPpDoesNotWalkAroundTheGuard() {
        // The adapter ignores spaces, so "AT PP FF OFF" is ATPPFFOFF (programmable parameters = persistent EEPROM change).
        assertFalse(CommandGuard.isAllowed("AT PP FF OFF"))
        assertFalse(CommandGuard.isAllowed("AT  PP FF ON"))
        assertFalse(CommandGuard.isAllowed("A T P P FFON"))
        assertEquals(listOf("ATPPFFOFF"), adapterWouldRun("AT PP FF OFF"))
    }

    // ---- other services, every spelling ----

    @Test fun onlyServiceOneIsEverAllowedOutOfAllTwoByteRequests() {
        for (mode in 0..255) for (pid in 0..255) {
            val s = "%02X%02X".format(mode, pid)
            assertEquals(s, mode == 1, CommandGuard.isAllowed(s))
        }
    }

    @Test fun mode01ForcesExactlyFourUpperCaseHexDigits() {
        assertTrue(CommandGuard.isAllowed("010C"))
        for (bad in listOf("01", "010", "010C1", "010c", "01 0C", "0 10C", " 010C", "010C ", "010C\r", "\r010C", "010C\n", "010G", "01-1", "01+C", "01 0",
            "0x010C", "010C0100", "0100\r0100", "01\u0000C", "010\u0661", "０１０Ｃ", "01０C")) {
            assertFalse("must be refused: ${bad.escaped()}", CommandGuard.isAllowed(bad))
        }
    }

    @Test fun multiCommandAndControlCharacterStringsAreRefused() {
        val sep = listOf("\r", "\n", "\r\n", "\u0000", "\t", "\u000B", "\u000C", "\u0085", "\u2028", "\u2029", ">", ";", "|", "&", "`", "$", " ")
        val parts = listOf("ATZ", "ATE0", "010C", "04", "ATPPFFOFF", "0100", "03", "2201F4")
        for (a in parts) for (b in parts) for (s in sep) {
            val joined = a + s + b
            assertFalse("must be refused: ${joined.escaped()}", CommandGuard.isAllowed(joined))
            assertFalse(CommandGuard.isAllowed(joined + s))
            assertFalse(CommandGuard.isAllowed(s + joined))
        }
        assertFalse(CommandGuard.isAllowed("AT\r010C\r04"))
        assertFalse(CommandGuard.isAllowed("ATZ\r"))      // trailing CR is added by the transport, never part of the command
        assertFalse(CommandGuard.isAllowed(""))
        assertFalse(CommandGuard.isAllowed("\r"))         // a bare CR makes the adapter repeat its LAST command
    }

    @Test fun lookAlikesAndFoldingTricksAreRefused() {
        val tricks = listOf(
            "atz", "Atz", "ate0", "at e0", "ＡＴＺ", "ΑΤΖ", "АТЗ", "ATΖ", "AТZ", "ATZ\u200B", "\u200BATZ", "AT\u00ADZ", "A\u0307TZ", "ATE０", "ATE\u0660",
            "ＡＴＥ０", "ATSP６", "ATSP6\u200B", "ĀTZ", "AT\uFF20" + "1", "ａｔｚ", "\uFEFFATZ", "ATZ\uFEFF", "0１00", "ATH1ATH1",
        )
        tricks.forEach { assertFalse("must be refused: ${it.escaped()}", CommandGuard.isAllowed(it)) }
        // the one place case folding is legitimate for the ADAPTER is a reason to refuse it here, not to allow it
        assertEquals(listOf("ATZ"), adapterWouldRun("atz"))
        assertFalse(CommandGuard.isAllowed("atz"))
    }

    @Test fun veryLongStringsAreRefusedQuickly() {
        val t0 = System.nanoTime()
        listOf("A".repeat(1_000_000), "AT" + "Z".repeat(1_000_000), "AT" + " ".repeat(1_000_000) + "Z", "01" + "0".repeat(1_000_000),
            "ATZ" + "\r".repeat(100_000) + "010C").forEach { assertFalse(CommandGuard.isAllowed(it)) }
        assertTrue("guard must stay linear", (System.nanoTime() - t0) / 1_000_000 < 2_000)
    }

    // ---- property: whatever is allowed is exactly one canonical command for the adapter too ----

    @Test fun randomisedHostileStringsNeverPassUnlessCanonical() {
        val rnd = Random(0xC0C4_17L)
        val alphabet = "ATZPBHSEIWMRVDLCKFJ0123456789ABCDEF @\r\n\t\u0000\u200BΑА０ａ".toList()
        var passed = 0
        repeat(2_000_000) {
            val n = 1 + rnd.nextInt(14)
            val s = buildString { repeat(n) { append(alphabet[rnd.nextInt(alphabet.size)]) } }
            if (CommandGuard.isAllowed(s)) {
                passed++
                assertTrue("passed but not canonical: ${s.escaped()}", s in policyAt || isMode01Read(s))
                assertEquals("adapter must see the very same single command", listOf(s), adapterWouldRun(s))
            }
        }
        assertTrue("fuzz never reached an allowed string, widen it", passed > 0)
    }

    @Test fun mutationsOfEveryAllowedCommandAreRefused() {
        val rnd = Random(7)
        val canonical = policyAt + (0..255).map { "01%02X".format(it) }
        val noise = listOf(" ", "\r", "\n", "\u0000", "0", "A", "F", "P", "\u200B", "０", "-", "+")
        for (c in canonical) repeat(20) {
            val sb = StringBuilder(c)
            sb.insert(rnd.nextInt(sb.length + 1), noise[rnd.nextInt(noise.size)])
            val m = sb.toString()
            assertTrue("mutation passed: ${m.escaped()}", !CommandGuard.isAllowed(m) || m in canonical)
        }
    }

    // ---- the guard is also enforced where bytes reach the wire ----

    @Test fun theWirePathRefusesBeforeWritingOneByte() {
        for (cmd in listOf("04", "ATPP FF OFF", "ATSH7E0", "0100\r04", "2EF190", "ATZ\r04", "")) {
            val out = ByteArrayOutputStream()
            assertThrows(cmd, IllegalArgumentException::class.java) {
                exchangeOverStreams(ByteArrayInputStream("OK\r>".toByteArray()), out, cmd)
            }
            assertEquals("nothing may be written for ${cmd.escaped()}", 0, out.size())
        }
    }

    @Test fun everyCommandTheClientSendsIsAllowed() {
        val sent = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP6") + Pid.values().map { it.request } + listOf("0100", "0120", "0140", "0160")
        sent.forEach { assertTrue(it, CommandGuard.isAllowed(it)) }
    }

    // ---- helpers ----

    private fun String.escaped() = map { if (it.code in 32..126) it.toString() else "\\u%04x".format(it.code) }.joinToString("")

    /** The spellings an attacker (or a buggy caller) might try for [base]. */
    private fun spellings(base: String): List<String> {
        val out = mutableListOf(base, base.lowercase(), base.replaceFirstChar { it.lowercase() })
        out += base.map { it.toString() }.let { chars -> chars.joinToString(" ") }                    // A T P P ...
        out += base.substring(0, 2) + " " + base.substring(2)                                        // AT PP...
        out += base.substring(0, 2) + "  " + base.substring(2)
        out += base.substring(0, 2) + "\t" + base.substring(2)
        out += " $base"; out += "$base "; out += "$base\r"; out += "\r$base"; out += "$base\n"; out += "$base\u0000"
        out += "ATZ\r$base"; out += "$base\r010C"; out += "010C\r$base"; out += "$base\r04"
        out += base.replace("AT", "ＡＴ"); out += base.replace("T", "Τ"); out += base.replace("A", "А")
        out += base + "0".repeat(300); out += "AT" + " ".repeat(64) + base.substring(2)
        return out.distinct()
    }
}
