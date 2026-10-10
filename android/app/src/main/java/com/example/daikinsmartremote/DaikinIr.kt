package com.example.daikinsmartremote

/**
 * Experimental Daikin-style 280-bit IR encoder.
 * Daikin models use multiple protocol variants; validate against the original remote/model.
 */
object DaikinIr {
    private const val KHZ = 38000
    private const val HEADER_MARK = 3500
    private const val HEADER_SPACE = 1750
    private const val BIT_MARK = 460
    private const val ZERO_SPACE = 420
    private const val ONE_SPACE = 1300
    private const val GAP = 29000

    fun buildState(power: Boolean, temperature: Int, mode: String, fan: String): ByteArray {
        // Daikin-style 35-byte state skeleton; fields/checksums may differ by model.
        val b = ByteArray(35)
        val defaults = intArrayOf(
            0x11,0xDA,0x27,0x00,0xC5,0x00,0x00,0xD0,
            0x11,0xDA,0x27,0x00,0x42,0x00,0x00,0x00,
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
            0x00,0x00,0x00
        )
        defaults.forEachIndexed { i, v -> b[i] = v.toByte() }
        // Common Daikin state layouts place operating settings in the third section.
        b[21] = ((temperature.coerceIn(16, 30) shl 1) or (if (power) 1 else 0)).toByte()
        b[22] = when (mode.uppercase()) {
            "DRY" -> 0x20
            "FAN" -> 0x60
            "HEAT" -> 0x40
            else -> 0x00 // COOL
        }.toByte()
        b[23] = when (fan.uppercase()) {
            "LOW" -> 0x30
            "MED" -> 0x40
            "HIGH" -> 0x50
            else -> 0xA0 // Auto
        }.toByte()
        // Placeholder section checksums; protocol variants can use different byte ranges.
        b[7] = (b.slice(0..6).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        b[15] = (b.slice(8..14).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        b[34] = (b.slice(16..33).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        return b
    }

    fun waveform(state: ByteArray): IntArray {
        val p = ArrayList<Int>()
        fun section(start: Int, end: Int) {
            p.add(HEADER_MARK); p.add(HEADER_SPACE)
            for (i in start until end) {
                val v = state[i].toInt() and 0xFF
                for (bit in 0..7) {
                    p.add(BIT_MARK)
                    p.add(if ((v shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE)
                }
            }
        }
        section(0, 8); p.add(GAP)
        section(8, 16); p.add(GAP)
        section(16, state.size)
        p.add(BIT_MARK)
        return p.toIntArray()
    }

    fun frequency() = KHZ * 1000
}
