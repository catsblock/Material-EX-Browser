package com.catsblock.materialex.core.permission

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTest {
    private val profile = "default"
    private val origin = "https://example.com"
    private fun manager() = PermissionManager(InMemoryPermissionStore())

    @Test
    fun defaultIsAskAndPrompts() = runBlocking {
        assertEquals(Decision.PROMPT, manager().decide(profile, origin, PermissionType.CAMERA))
    }

    @Test
    fun allowGrantsAndPersistsInStore() = runBlocking {
        val m = manager()
        m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.ALLOW)
        assertEquals(Decision.GRANT, m.decide(profile, origin, PermissionType.CAMERA))
    }

    @Test
    fun alwaysAskKeepsStateAndDoesNotCount() = runBlocking {
        val m = manager()
        repeat(5) { m.choose(profile, origin, PermissionType.LOCATION, PromptChoice.ALWAYS_ASK) }
        val record = m.overview(profile, origin).getValue(PermissionType.LOCATION)
        assertEquals(PermissionRecord(PermissionState.ASK, 0), record)
        assertEquals(Decision.PROMPT, m.decide(profile, origin, PermissionType.LOCATION))
    }

    @Test
    fun denyIncrementsAndBlocksExactlyOnThirdDenial() = runBlocking {
        val m = manager()
        val first = m.choose(profile, origin, PermissionType.MICROPHONE, PromptChoice.DENY)
        assertEquals(PermissionRecord(PermissionState.ASK, 1), first)
        val second = m.choose(profile, origin, PermissionType.MICROPHONE, PromptChoice.DENY)
        assertEquals(PermissionRecord(PermissionState.ASK, 2), second)
        assertEquals(Decision.PROMPT, m.decide(profile, origin, PermissionType.MICROPHONE))
        val third = m.choose(profile, origin, PermissionType.MICROPHONE, PromptChoice.DENY)
        assertEquals(PermissionRecord(PermissionState.AUTO_BLOCKED, 3), third)
        assertEquals(Decision.REJECT, m.decide(profile, origin, PermissionType.MICROPHONE))
    }

    @Test
    fun autoBlockedDoesNotIncrementFurther() = runBlocking {
        val m = manager()
        repeat(3) { m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.DENY) }
        val after = m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.DENY)
        assertEquals(3, after.denialCount)
        assertEquals(PermissionState.AUTO_BLOCKED, after.state)
    }

    @Test
    fun blockIsScopedToPermissionAndOrigin() = runBlocking {
        val m = manager()
        repeat(3) { m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.DENY) }
        assertEquals(Decision.PROMPT, m.decide(profile, origin, PermissionType.MICROPHONE))
        assertEquals(Decision.PROMPT, m.decide(profile, "https://other.example.com", PermissionType.CAMERA))
        assertEquals(Decision.PROMPT, m.decide(profile, "https://example.com:8443", PermissionType.CAMERA))
    }

    @Test
    fun profilesAreIsolated() = runBlocking {
        val m = manager()
        m.choose("a", origin, PermissionType.LOCATION, PromptChoice.ALLOW)
        assertEquals(Decision.GRANT, m.decide("a", origin, PermissionType.LOCATION))
        assertEquals(Decision.PROMPT, m.decide("b", origin, PermissionType.LOCATION))
        m.clearProfile("a")
        assertEquals(Decision.PROMPT, m.decide("a", origin, PermissionType.LOCATION))
    }

    @Test
    fun restoringAskFromBlockedResetsCounterAndPromptsAgain() = runBlocking {
        val m = manager()
        repeat(3) { m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.DENY) }
        val restored = m.setState(profile, origin, PermissionType.CAMERA, PermissionState.ASK)
        assertEquals(PermissionRecord(PermissionState.ASK, 0), restored)
        assertEquals(Decision.PROMPT, m.decide(profile, origin, PermissionType.CAMERA))
    }

    @Test
    fun resetCounterUnblocks() = runBlocking {
        val m = manager()
        repeat(3) { m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.DENY) }
        val reset = m.resetCounter(profile, origin, PermissionType.CAMERA)
        assertEquals(PermissionRecord(PermissionState.ASK, 0), reset)
    }

    @Test
    fun manualAllowFromBlockedGrants() = runBlocking {
        val m = manager()
        repeat(3) { m.choose(profile, origin, PermissionType.CAMERA, PromptChoice.DENY) }
        m.setState(profile, origin, PermissionType.CAMERA, PermissionState.ALLOW)
        assertEquals(Decision.GRANT, m.decide(profile, origin, PermissionType.CAMERA))
    }

    @Test
    fun originNormalization() {
        assertEquals("https://example.com", Origin.normalize("https://Example.com/path?q=1"))
        assertEquals("https://example.com", Origin.normalize("https://example.com:443/"))
        assertEquals("http://example.com:8080", Origin.normalize("http://example.com:8080/x"))
        assertEquals("https://a.example.com", Origin.normalize("https://a.example.com"))
        assertNull(Origin.normalize("javascript:alert(1)"))
        assertNull(Origin.normalize("file:///x"))
        assertNull(Origin.normalize(null))
    }

    @Test
    fun secureOriginCheck() {
        assertTrue(Origin.isSecure("https://example.com"))
        assertTrue(Origin.isSecure("http://localhost:3000"))
        assertFalse(Origin.isSecure("http://example.com"))
    }
}
