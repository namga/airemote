package com.example.daikinsmartremote

/**
 * Midea 48-bit state-frame encoder based on the public IRremoteESP8266 Midea
 * implementation (38 kHz, 4480 us header mark/space, 560 us bit mark,
 * 1680/560 us one/zero spaces, normal frame followed by inverted frame).
 * Midea support is still model-dependent; this is not a universal Midea guarantee.
 */
internal object MideaIr {
    private const val CARRIER_HZ = 38_000
    private const val HEADER_BYTE = 0xA1L // Midea command type/header

    fun buildPattern(power: Boolean, temperature: Int, mode: String, fan: String): IntArray {
        val temp = temperature.coerceIn(17, 30) - 17
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
            "MED", "MEDIUM" -> 2
            "HIGH" -> 3
            else -> 0
        }
        // Six bytes, MSB first: header/type, mode/fan/power, temperature,
        // timer/beep, sensor/timer, checksum. 0xFF fields disable timers/sensor.
        val header = HEADER_BYTE
        val modeFanPower = (modeBits or (fanBits shl 3) or (if (power) 0x80 else 0)).toLong()
        val tempByte = temp.toLong()
        val bytes = longArrayOf(header, modeFanPower, tempByte, 0xFF, 0xFF, 0x00)
        var raw = 0L
        for (b in bytes) raw = (raw shl 8) or (b and 0xFF)
        val checksum = calcChecksum(raw)
        raw = (raw and 0xFFFFFFFFFFFFFF00UL.toLong()) or checksum.toLong()

        val timings = ArrayList<Int>(400)
        // Send standard 48-bit payload then its bitwise inverse, per Midea framing.
        appendFrame(timings, raw)
        appendFrame(timings, raw xor 0xFFFFFFFFFFFFL)
        // A second pair is commonly sent as repeat; keep total under Android's <2s guidance.
        appendFrame(timings, raw)
        appendFrame(timings, raw xor 0xFFFFFFFFFFFFL)
        return timings.toIntArray()
    }

    private fun appendFrame(out: MutableList<Int>, value: Long) {
        out += 4480; out += 4480
        for (bit in 47 downTo 0) {
            out += 560
            out += if (((value ushr bit) and 1L) != 0L) 1680 else 560
        }
        out += 560
        out += 5600
    }

    private fun calcChecksum(state: Long): Int {
        var sum = 0
        // Match IRremoteESP8266: sum bit-reversed bytes 1..5, complement, reverse bits.
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
