package com.example.daikinsmartremote

/**
 * Midea 48-bit state encoder based on the public IRremoteESP8266 ir_Midea
 * implementation. Standard Midea frames send a 48-bit state followed by its
 * bitwise-inverted payload; each byte is transmitted MSB-first at 38 kHz.
 * Compatibility remains model-dependent (the upstream implementation itself
 * labels this protocol alpha), so this is not a universal Midea guarantee.
 */
internal object MideaIr {
    private const val CARRIER_HZ = 38_000
    private const val HEADER_MARK_US = 4_480
    private const val HEADER_SPACE_US = 4_480
    private const val BIT_MARK_US = 560
    private const val ONE_SPACE_US = 1_680
    private const val ZERO_SPACE_US = 560
    private const val FRAME_GAP_US = 5_600
    private const val MIDEA_HEADER_AND_COMMAND_TYPE = 0xA1L

    /** Known reset-state vector from upstream IRremoteESP8266 (checksum byte zeroed). */
    fun checksumSelfTest(): Boolean = calcChecksum(0xA1826FFFFF00L) == 0x62

    /** Checks that the Celsius temperature byte has Fahrenheit/reserved bits cleared. */
    fun stateLayoutSelfTest(): Boolean {
        val state = buildState(true, 27, "COOL", "AUTO", reetechUnknownBit = false)
        val tempByte = ((state ushr 24) and 0xFF).toInt()
        val headerByte = ((state ushr 40) and 0xFF).toInt()
        return tempByte == 10 && headerByte == 0xA1 && (state and 0xFF).toInt() == calcChecksum(state)
    }

    fun describePattern(pattern: IntArray): String {
        val totalUs = pattern.sumOf { it.toLong() }
        return "${pattern.size} durations, ${(totalUs / 1000.0).toInt()} ms, checksum=${if (checksumSelfTest()) "PASS" else "FAIL"}, layout=${if (stateLayoutSelfTest()) "PASS" else "FAIL"}"
    }

    /** Returns alternating mark/space durations accepted by ConsumerIrManager. */
    fun buildPattern(power: Boolean, temperature: Int, mode: String, fan: String): IntArray {
        val state = buildState(power, temperature, mode, fan, reetechUnknownBit = false)
        val timings = ArrayList<Int>(400)
        appendFrame(timings, state)
        appendFrame(timings, state xor 0xFFFFFFFFFFFFL)
        return timings.toIntArray()
    }

    /**
     * Reetech trial profile: same public Midea state layout, but sets the
     * otherwise-unknown bit in byte 4. Upstream documents that some OEM units
     * require this bit; this is a targeted compatibility experiment, not a
     * verified Reetech protocol capture.
     */
    fun buildReetechPattern(power: Boolean, temperature: Int, mode: String, fan: String): IntArray {
        val state = buildState(power, temperature, mode, fan, reetechUnknownBit = true)
        val timings = ArrayList<Int>(400)
        appendFrame(timings, state)
        appendFrame(timings, state xor 0xFFFFFFFFFFFFL)
        return timings.toIntArray()
    }

    private fun buildState(power: Boolean, temperature: Int, mode: String, fan: String, reetechUnknownBit: Boolean): Long {
        val tempOffset = temperature.coerceIn(17, 30) - 17
        val modeBits = when (mode.uppercase()) {
            "COOL" -> 0
            "DRY" -> 1
            "AUTO" -> 2
            "HEAT" -> 3
            "FAN" -> 4
            else -> 0
        }
        val fanBits = when (fan.uppercase()) {
            "LOW" -> 1
            "MED", "MEDIUM", "NORMAL" -> 2
            "HIGH" -> 3
            else -> 0 // Auto
        }

        // Midea native state, bytes from most to least significant:
        // header/type | power/sleep/unknown/fan/mode | temp/Fahrenheit |
        // off-timer/beep | sensor-temp | checksum.
        // Important: Celsius temperature occupies only bits 0..4. Bit 5 is
        // Fahrenheit; bits 6..7 are reserved and must be zero. The prior
        // encoder incorrectly set bit 6 (0x40), making every Celsius command
        // malformed for the standard Midea protocol.
        val modeFanPower = ((if (power) 0x80 else 0) or (if (reetechUnknownBit) 0x20 else 0) or (fanBits shl 3) or modeBits).toLong()
        val tempByte = tempOffset.toLong() // Celsius: Fahrenheit and reserved bits cleared.
        var state = (MIDEA_HEADER_AND_COMMAND_TYPE shl 40) or
            (modeFanPower shl 32) or
            (tempByte shl 24) or
            (0xFFFFL shl 8)
        val checksum = calcChecksum(state)
        return state or checksum.toLong()
    }

    private fun appendFrame(out: MutableList<Int>, value: Long) {
        out += HEADER_MARK_US
        out += HEADER_SPACE_US
        for (bit in 47 downTo 0) {
            out += BIT_MARK_US
            out += if (((value ushr bit) and 1L) != 0L) ONE_SPACE_US else ZERO_SPACE_US
        }
        out += BIT_MARK_US
        out += FRAME_GAP_US
    }

    // Same checksum as IRremoteESP8266::IRMideaAC::calcChecksum:
    // reverse bits in bytes 1..5, sum modulo 256, subtract from 256,
    // then reverse bits in the result.
    private fun calcChecksum(state: Long): Int {
        var sum = 0
        for (shift in intArrayOf(8, 16, 24, 32, 40)) {
            sum = (sum + reverseBits(((state ushr shift) and 0xFF).toInt())) and 0xFF
        }
        return reverseBits((256 - sum) and 0xFF)
    }

    private fun reverseBits(input: Int): Int {
        var x = input and 0xFF
        var result = 0
        repeat(8) { result = (result shl 1) or (x and 1); x = x ushr 1 }
        return result
    }
}
