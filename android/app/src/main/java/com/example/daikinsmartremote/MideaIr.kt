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

    /** Returns alternating mark/space durations accepted by ConsumerIrManager. */
    fun buildPattern(power: Boolean, temperature: Int, mode: String, fan: String): IntArray {
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

        // Midea native state, ordered as transmitted: A1 | mode/fan/power |
        // temperature flags/value | off-timer/beep byte | sensor-temp byte | sum.
        // The reference reset vector 0xA1826FFFFF62 verifies this byte order and
        // checksum implementation. For Celsius, bit 5 (Fahrenheit) is clear;
        // the reserved high bit in the temperature byte is retained as in the
        // library's native representation.
        val modeFanPower = ((if (power) 0x80 else 0) or (fanBits shl 3) or modeBits).toLong()
        val tempByte = (0x40 or tempOffset).toLong()
        var state = (MIDEA_HEADER_AND_COMMAND_TYPE shl 40) or
            (modeFanPower shl 32) or
            (tempByte shl 24) or
            (0xFFFFL shl 8)
        val checksum = calcChecksum(state)
        state = state or checksum.toLong()

        val timings = ArrayList<Int>(400)
        appendFrame(timings, state)
        appendFrame(timings, state xor 0xFFFFFFFFFFFFL)
        return timings.toIntArray()
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
