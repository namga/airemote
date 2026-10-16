package com.example.daikinsmartremote

/**
 * Kotlin port of IRremoteESP8266 GreeProtocol state layout and sendGree framing.
 * Protocol layout is based on the upstream IRGreeAC implementation.
 * Reetech RT9-BM5 compatibility remains unverified.
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
        val state = IntArray(8)
        val temp = temperature.coerceIn(16, 30)
        val modeBits = when (mode.uppercase()) {
            "COOL" -> 1
            "DRY" -> 2
            "FAN" -> 3
            "HEAT" -> 4
            "ECONO", "ECO" -> 5
            else -> 0
        }
        val fanBits = when (fan.uppercase()) {
            "LOW" -> 1
            "MED", "MEDIUM", "NORMAL" -> 2
            "HIGH" -> 3
            else -> 0
        }

        // GreeProtocol bitfields are LSB-first within each byte.
        state[0] = (modeBits and 0x07) or
            ((if (power) 1 else 0) shl 3) or
            ((fanBits and 0x03) shl 4) or
            (1 shl 6) // SwingAuto
        state[1] = (temp - 16) and 0x0F
        // Byte 2: TimerHours=0, Turbo=0, Light=1, ModelA=bit 6, Xfan=0.
        state[2] = (1 shl 5) or ((if (power && model == "YAW1F") 1 else 0) shl 6)
        // Byte 3: unknown1=5 in upper nibble; Celsius and no extra degree.
        state[3] = 0x50
        // Byte 4: swing vertical last position, horizontal swing off.
        state[4] = 0x00
        // Byte 5: unknown2=4 in bits 3..5; all optional features off.
        state[5] = 0x20
        state[6] = 0x00
        state[7] = 0x00

        // Same block checksum used by IRKelvinatorAC::calcBlockChecksum().
        var sum = 10
        for (i in 0..3) sum += state[i] and 0x0F
        for (i in 4..6) sum += (state[i] ushr 4) and 0x0F
        state[7] = (sum and 0x0F) shl 4

        val out = ArrayList<Int>(150)
        // Upstream sendGree(data[], nbytes) sends each byte LSB-first.
        out += HEADER_MARK
        out += HEADER_SPACE
        for (byteIndex in 0..3) appendByteLsbFirst(out, state[byteIndex])
        // Footer #1: 3-bit 010, then mark and long message gap.
        appendBitsLsbFirst(out, 0b010, 3)
        out += BIT_MARK
        out += MESSAGE_SPACE
        // Block #2 has no header, and ends with mark + message gap.
        for (byteIndex in 4..7) appendByteLsbFirst(out, state[byteIndex])
        out += BIT_MARK
        out += MESSAGE_SPACE
        return out.toIntArray()
    }

    private fun appendByteLsbFirst(out: MutableList<Int>, value: Int) =
        appendBitsLsbFirst(out, value, 8)

    private fun appendBitsLsbFirst(out: MutableList<Int>, value: Int, bits: Int) {
        for (bit in 0 until bits) {
            out += BIT_MARK
            out += if (((value ushr bit) and 1) != 0) ONE_SPACE else ZERO_SPACE
        }
    }
}
