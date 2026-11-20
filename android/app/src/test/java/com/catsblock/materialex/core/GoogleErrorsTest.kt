package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleErrorsTest {
    @Test
    fun statusCodesAreClassified() {
        assertEquals(GoogleFailure.NOT_REGISTERED, GoogleErrors.classify("8"))
        assertEquals(GoogleFailure.NOT_REGISTERED, GoogleErrors.classify("10"))
        assertEquals(GoogleFailure.NETWORK, GoogleErrors.classify("7"))
        assertEquals(GoogleFailure.OTHER, GoogleErrors.classify("NO_EMAIL"))
    }

    @Test
    fun fingerprintIsColonSeparatedUppercaseHex() {
        assertEquals("0A:FF:10", GoogleErrors.fingerprint(byteArrayOf(0x0A, 0xFF.toByte(), 0x10)))
    }
}
