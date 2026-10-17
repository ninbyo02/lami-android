package io.github.ninbyo02.lami.tts

/** No listener or native resource ownership. Enforces one sequential request stream. */
internal class ResidentCpWireSession {
    private var nextFrame = 0
    private var nextPosition = -1
    private var closed = false

    /** Returns true if this packet must trigger a per-frame KV reset. */
    fun accept(packet: ResidentCpWire.Packet): Boolean {
        check(!closed) { "Session closed" }
        check(nextFrame < 256) { "Session frame limit reached" }
        return when (packet.type) {
            ResidentCpWire.RESET -> {
                require(packet.frame == nextFrame && packet.position == 0 && nextPosition == -1) {
                    "Unexpected frame reset"
                }
                nextPosition = 0
                true
            }
            ResidentCpWire.STEP -> {
                require(packet.frame == nextFrame && packet.position == nextPosition &&
                    nextPosition in 0..15) { "Out-of-order CP step" }
                nextPosition++
                if (nextPosition == 16) {
                    nextFrame++
                    nextPosition = -1
                }
                false
            }
            else -> error("Session only accepts RESET and STEP")
        }
    }

    fun close() { closed = true }
}
