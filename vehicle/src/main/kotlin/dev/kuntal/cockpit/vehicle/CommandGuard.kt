package dev.kuntal.cockpit.vehicle

/** Read-only enforcement: ELM327 `AT` configuration and OBD Mode 01 reads are the only commands ever sent. */
object CommandGuard {
    private val at = Regex("^AT(?!PP)[A-Z0-9 ]{1,12}$")
    private val mode01 = Regex("^01[0-9A-F]{2}$")
    fun isAllowed(cmd: String): Boolean = at.matches(cmd) || mode01.matches(cmd)
}
