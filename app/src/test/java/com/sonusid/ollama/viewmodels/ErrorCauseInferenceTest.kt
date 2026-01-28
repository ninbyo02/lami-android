package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.ui.screens.settings.ErrorCause
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorCauseInferenceTest {

    @Test
    fun `blank message becomes unknown`() {
        assertEquals(ErrorCause.UNKNOWN, inferErrorCause(null))
        assertEquals(ErrorCause.UNKNOWN, inferErrorCause(""))
        assertEquals(ErrorCause.UNKNOWN, inferErrorCause("   "))
    }

    @Test
    fun `heavy keywords are treated as heavy`() {
        assertEquals(ErrorCause.HEAVY, inferErrorCause("Socket timeout"))
        assertEquals(ErrorCause.HEAVY, inferErrorCause("Connection refused"))
        assertEquals(ErrorCause.HEAVY, inferErrorCause("UnknownHostException"))
        assertEquals(ErrorCause.HEAVY, inferErrorCause("connectexception while calling"))
    }

    @Test
    fun `non matching message becomes light`() {
        assertEquals(ErrorCause.LIGHT, inferErrorCause("Failed to generate response"))
    }
}
