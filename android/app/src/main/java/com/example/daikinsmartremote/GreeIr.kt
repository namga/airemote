package com.example.daikinsmartremote

/**
 * Kotlin port of the standard 64-bit Gree frame layout/timing from
 * IRremoteESP8266 IRGreeAC. This is a protocol-accurate Gree encoder port,
 * but Reetech RT9-BM5 compatibility is not confirmed.
 */
internal object GreeIr {
    const val CARRIER_HZ = 38_000
    private const val HEADER_MARK = 9000
    private const val HEADER_SPACE = 4500
    private const val BIT_MARK = 620
    private const val ONE_SPACE = 1600
    private const val ZERO_SPACE = 540
    private const val MESSAGE_SPACE = 19980

    fun buildExperimentalPattern(
        power: Boolean,
        temperature: Int,
        mode: String,
        fan: String,
        model: String = "YAW1F"
    ): IntArray {
        // IRGreeAC::stateReset() defaults: Auto, off, fan auto, 25C, light on.
        val state = IntArray(8)
        val temp = temperature.coerceIn(16, 30)
        val modeBits = when (mode.uppercase()) {
            "COOL" -> 1
            "DRY" -> 2
            "FAN" -> 3
            "HEAT" -> 4
            else -> 0
        }
        val fanBits = when (fan.uppercase()) {
            "LOW" -> 1
            "MED", "MEDIUM", "NORMAL" -> 2
            "HIGH" -> 3
            else -> 0
        }

        // GreeProtocol bitfield layout, byte 0 (LSB-first field positions).
        state[0] = (modeBits and 0x07) or
            ((if (power) 1 else 0) shl 3) or
            ((fanBits and 0x03) shl 4) or
            (1 shl 6) // SwingAuto default
        // Byte 1: temperature is encoded as Celsius - 16.
        state[1] = (temp - 16) and 0x0F
        // Byte 2: light on by default; YAW1F model bit is set only when powered on.
        state[2] = (1 shl 5) or
            ((if (power && model == "YAW1F") 1 else 0) shl 6)
        // Byte 3: unknown nibble must be 0b0101.
        state[3] = 0x50
        // Byte 4: default vertical swing last-position, horizontal swing off.
        state[4] = 0x00
        // Byte 5: unknown2 field is 0b100 (bits 3..5), matching IRGreeAC reset.
        state[5] = 0x20
        state[6] = 0x00
        // Byte 7: checksum in upper nibble; checksum is set below.

        // IRKelvinatorAC::calcBlockChecksum() used by Gree:
        // start at 10, sum low nibbles of bytes 0..3, then high nibbles 4..6.
        var sum = 10
        for (i in 0..3) sum += state[i] and 0x0F
        for (i in 4..6) sum += (state[i] ushr 4) and 0x0F
        state[7] = (sum and 0x0F) shl 4

        val out = ArrayList<Int>(140)
        out += HEADER_MARK
        out += HEADER_SPACE
        // First 32-bit block.
        for (byteIndex in 0..3) appendByte(out, state[byteIndex])
        // Mandatory 3-bit mid-message footer B010, MSB first, followed by gap.
        appendBits(out, 0b010, 3)
        out += BIT_MARK
        out += MESSAGE_SPACE
        // Second 32-bit block; the standard protocol ends with a mark and gap.
        for (byteIndex in 4..7) appendByte(out, state[byteIndex])
        out += BIT_MARK
        out += MESSAGE_SPACE
        return out.toIntArray()
    }

    private fun appendByte(out: MutableList<Int>, value: Int) = appendBits(out, value, 8)

    private fun appendBits(out: MutableList<Int>, value: Int, bits: Int) {
        for (bit in bits - 1 downTo 0) {
            out += BIT_MARK
            out += if (((value ushr bit) and 1) != 0) ONE_SPACE else ZERO_SPACE
        }
    }
}
