package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {
    @Test
    fun valuesAreClampedAndSnappedToSteps() {
        assertEquals(50, TextScale.coerce(0))
        assertEquals(200, TextScale.coerce(999))
        assertEquals(100, TextScale.coerce(103))
        assertEquals(110, TextScale.coerce(106))
    }

    @Test
    fun factorMatchesPercent() {
        assertEquals(1.0f, TextScale.factor(100), 0.0001f)
        assertEquals(1.5f, TextScale.factor(150), 0.0001f)
    }
}
