package io.github.ninbyo02.lami.tts

import java.io.File

/** Dedicated child process. Parent terminates only this probe after the ready marker. */
object LamiTraceExitProbe {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.single())
        var clock = 100L
        val trace = LamiSpeechTraceCapture(root.resolve("capture"), "exit_probe", "",
            "a".repeat(64), "b".repeat(64)) { clock }
        clock = 125L
        trace.event("synthesis_started")
        root.resolve("ready").writeText("stage_saved")
        Thread.sleep(60000)
        error("Probe must be terminated by its parent")
    }
}
