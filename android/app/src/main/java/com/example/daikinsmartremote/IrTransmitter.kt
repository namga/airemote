package com.example.daikinsmartremote

import android.hardware.ConsumerIrManager

/** Small Android-native transmitter adapter; protocol encoders only produce timing arrays. */
internal object IrTransmitter {
    fun send(manager: ConsumerIrManager, carrierHz: Int, timings: IntArray) {
        require(carrierHz in 20_000..60_000) { "Unsupported IR carrier" }
        require(timings.size >= 6 && timings.size <= 1000) { "Invalid IR timing count" }
        require(timings.all { it in 1..1_000_000 }) { "Invalid IR timing value" }
        require(timings.sumOf { it.toLong() } < 1_900_000L) { "IR pattern too long for safe Android transmit" }
        manager.transmit(carrierHz, timings)
    }
}
