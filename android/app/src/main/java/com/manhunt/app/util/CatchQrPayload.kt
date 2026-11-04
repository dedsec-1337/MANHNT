package com.manhunt.app.util

/**
 * Wire format of the Hider's tag QR, shared by encoder and decoder so they cannot drift:
 *
 *     MANHNT|<joinCode>|<playerId>|<catchCode>
 *
 * The QR carries exactly what the Seeker could type by hand (the 4-digit catch code) plus the
 * routing info needed to pick the right Hider. It grants nothing the on-screen code does not.
 * Every tag is still validated by the host and by the Firebase rules, whatever its source.
 */
data class CatchQrPayload(
    val joinCode: String,
    val playerId: String,
    val catchCode: String
) {
    fun encode(): String = "$PREFIX$SEPARATOR$joinCode$SEPARATOR$playerId$SEPARATOR$catchCode"

    companion object {
        const val PREFIX = "MANHNT"
        private const val SEPARATOR = "|"
        private val JOIN_CODE = Regex("^[A-Z0-9]{4,12}$")
        private val PLAYER_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
        private val CATCH_CODE = Regex("^[0-9]{4}$")

        /** Returns null for anything that is not a well-formed MANHNT tag QR. */
        fun parse(raw: String?): CatchQrPayload? {
            if (raw == null) return null
            val parts = raw.trim().split(SEPARATOR)
            if (parts.size != 4 || parts[0] != PREFIX) return null
            val joinCode = parts[1].uppercase()
            val playerId = parts[2]
            val catchCode = parts[3]
            if (!JOIN_CODE.matches(joinCode)) return null
            if (!PLAYER_ID.matches(playerId)) return null
            if (!CATCH_CODE.matches(catchCode)) return null
            return CatchQrPayload(joinCode, playerId, catchCode)
        }
    }
}
