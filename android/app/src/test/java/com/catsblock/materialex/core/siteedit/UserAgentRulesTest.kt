package com.catsblock.materialex.core.siteedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentRulesTest {
    @Test
    fun customValueValidation() {
        assertTrue(UserAgentRules.validateCustom("Mozilla/5.0 Test"))
        assertFalse(UserAgentRules.validateCustom("   "))
        assertFalse(UserAgentRules.validateCustom("a\nb"))
        assertFalse(UserAgentRules.validateCustom("x".repeat(UserAgentRules.MAX_CUSTOM_LENGTH + 1)))
    }

    @Test
    fun roundTripKeepsModesAndCustomValues() {
        val choices = mapOf(
            "a.com" to UaChoice(UaMode.DESKTOP),
            "b.com" to UaChoice(UaMode.CUSTOM, "Agent: 1/2"),
            "c.com" to UaChoice(UaMode.MOBILE)
        )
        assertEquals(choices, UserAgentRules.decode(UserAgentRules.encode(choices)))
    }

    @Test
    fun invalidEntriesAreDropped() {
        val raw = SiteStyleRules.encode(
            mapOf("a.com" to "BOGUS:", "b.com" to "CUSTOM:", "c.com" to "DEFAULT:", "d.com" to "DESKTOP:")
        )
        assertEquals(mapOf("d.com" to UaChoice(UaMode.DESKTOP)), UserAgentRules.decode(raw))
    }

    @Test
    fun withRemovesWhenDefaultOrNull() {
        val base = mapOf("a.com" to UaChoice(UaMode.DESKTOP))
        assertEquals(emptyMap<String, UaChoice>(), UserAgentRules.with(base, "a.com", null))
        assertEquals(emptyMap<String, UaChoice>(), UserAgentRules.with(base, "a.com", UaChoice(UaMode.DEFAULT)))
        assertEquals(
            mapOf("a.com" to UaChoice(UaMode.MOBILE)),
            UserAgentRules.with(base, "a.com", UaChoice(UaMode.MOBILE))
        )
    }
}
