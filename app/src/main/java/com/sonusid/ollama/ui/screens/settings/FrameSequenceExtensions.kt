package com.sonusid.ollama.ui.screens.settings

fun ReadyAnimationSettings.frames(): List<Int> = frameSequence

fun ReadyAnimationSettings.withFrames(frames: List<Int>): ReadyAnimationSettings =
    copy(frameSequence = frames)

fun InsertionPattern.frames(): List<Int> = frameSequence

fun InsertionPattern.withFrames(frames: List<Int>): InsertionPattern =
    copy(frameSequence = frames)
