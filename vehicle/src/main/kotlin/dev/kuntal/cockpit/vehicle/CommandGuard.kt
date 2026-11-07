package dev.kuntal.cockpit.vehicle

/**
 * Read-only enforcement: the only bytes this app may ever put on the adapter link are
 *  - a short, fixed list of ELM327 `AT` commands that only change the adapter's own volatile output/format state or read its
 *    identity (see [AT_ALLOWED] and docs/SECURITY.md for what is refused and why), and
 *  - OBD-II mode 01 reads (`01` + one PID byte): "show current data", which no ECU treats as a write.
 *
 * It is an exact-match whitelist on the canonical spelling and deliberately does NOT normalise (no trimming, no case folding,
 * no space removal). An ELM327 ignores spaces and accepts lower case, so a pattern-based guard that "allows AT plus anything
 * but PP" can be walked around with `AT PP FF OFF`; refusing every non-canonical spelling removes any parsing difference
 * between this guard and the adapter. Anything the adapter would read as a bare hex string (a raw OBD/CAN request, i.e. every
 * other mode: 03/04/07/08/09/0A, UDS 10/11/14/22/27/28/2E/2F/31/3B/3D/85 ...) is refused unless it is exactly `01xx`.
 */
object CommandGuard {
    /** Exactly these `AT` commands; each leaves what the adapter puts on the vehicle bus unchanged. */
    val AT_ALLOWED: Set<String> = setOf(
        "ATZ",                                   // reset to power-on defaults (volatile)
        "ATI", "AT@1", "ATRV", "ATDP", "ATDPN",  // read-only: version, identifier, pin-16 voltage, current protocol
        "ATE0", "ATE1", "ATL0", "ATL1", "ATS0", "ATS1", "ATH0", "ATH1",   // echo, line feeds, spaces, headers: output format only
        "ATAT0", "ATAT1", "ATAT2",               // adaptive timing: only how long the adapter waits for a reply
        "ATSP6",                                 // ISO 15765-4 CAN 11-bit/500 kbit/s, the EcoSport's bus (no protocol search)
        "ATCAF1",                                // CAN auto-formatting ON (the default); CAF0 would allow raw frames
    )

    fun isAllowed(cmd: String): Boolean = cmd in AT_ALLOWED || isMode01(cmd)

    private fun isMode01(c: String): Boolean =
        c.length == 4 && c[0] == '0' && c[1] == '1' && isUpperHex(c[2]) && isUpperHex(c[3])

    private fun isUpperHex(ch: Char) = ch in '0'..'9' || ch in 'A'..'F'
}
