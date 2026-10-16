package com.example.daikinsmartremote

/**
 * Experimental Gree/YAC-family timing encoder for test builds.
 * IMPORTANT: this is not a captured profile for Reetech RT9-BM5. Gree remotes
 * use multiple state layouts; the byte mapping below is a conservative generic
 * trial profile and must be validated on the target AC before being treated as supported.
 */
internal object GreeIr {
    const val CARRIER_HZ = 38_000
    private const val HEADER_MARK = 9000
    private const val HEADER_SPACE = 4500
    private const val BIT_MARK = 620
    private const val ONE_SPACE = 1600
    private const val ZERO_SPACE = 540
    private const val FOOTER_SPACE = 19000

    fun buildExperimentalPattern(power: Boolean, temperature: Int, mode: String, fan: String, model: String = "YAW1F"): IntArray {
        // Generic 8-byte Gree-family state skeleton. Keep this isolated and labelled
        // experimental because OEM state layout/checksum may differ from this model.
        val state = IntArray(8)
        state[0] = 0x00
        state[1] = 0x09
        state[2] = if (model == "YAW1F") 0x60 else 0x20 // ModelA bit: YAW1F vs YBOFB/YX1FSF
        state[3] = ((temperature.coerceIn(16, 30) - 16) and 0x0F) shl 4
        val modeBits = when (mode.uppercase()) {
            "COOL" -> 0x03
            "DRY" -> 0x02
            "FAN" -> 0x04
            "HEAT" -> 0x01
            else -> 0x00
        }
        val fanBits = when (fan.uppercase()) {
            "LOW" -> 0x01
            "MED", "MEDIUM", "NORMAL" -> 0x02
            "HIGH" -> 0x03
            else -> 0x00
        }
        state[4] = modeBits or (fanBits shl 3) or (if (power) 0x80 else 0)
        state[5] = 0x00
        state[6] = 0x00
        state[7] = state.take(7).sum() and 0x0F

        val out = ArrayList<Int>(150)
        out += HEADER_MARK; out += HEADER_SPACE
        for (byte in state) {
            for (bit in 7 downTo 0) {
                out += BIT_MARK
                out += if (((byte ushr bit) and 1) == 1) ONE_SPACE else ZERO_SPACE
            }
        }
        out += BIT_MARK; out += FOOTER_SPACE
        return out.toIntArray()
    }
}
