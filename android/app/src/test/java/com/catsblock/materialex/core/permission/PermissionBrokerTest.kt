package com.catsblock.materialex.core.permission

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionBrokerTest {
    private val site = "https://example.com"

    private fun newBroker(): Pair<PermissionBroker, PermissionManager> {
        val manager = PermissionManager(InMemoryPermissionStore())
        return PermissionBroker(manager, "profile") to manager
    }

    private suspend fun TestScope.ask(broker: PermissionBroker, origin: String, type: PermissionType, choice: PromptChoice): Boolean {
        val result = async { broker.resolve(origin, type) }
        runCurrent()
        assertNotNull(broker.prompt.value)
        broker.answer(choice)
        return result.await()
    }

    @Test
    fun allowGrantsAndIsRememberedWithoutPrompting() = runTest {
        val (broker, _) = newBroker()
        assertTrue(ask(broker, site, PermissionType.CAMERA, PromptChoice.ALLOW))
        assertTrue(broker.resolve(site, PermissionType.CAMERA))
        assertNull(broker.prompt.value)
    }

    @Test
    fun alwaysAskRejectsWithoutCountingAndPromptsAgain() = runTest {
        val (broker, manager) = newBroker()
        repeat(5) { assertFalse(ask(broker, site, PermissionType.LOCATION, PromptChoice.ALWAYS_ASK)) }
        val record = manager.overview("profile", site).getValue(PermissionType.LOCATION)
        assertEquals(PermissionState.ASK, record.state)
        assertEquals(0, record.denialCount)
    }

    @Test
    fun threeDenialsBlockAutomaticallyAndStopPrompting() = runTest {
        val (broker, manager) = newBroker()
        repeat(3) { assertFalse(ask(broker, site, PermissionType.MICROPHONE, PromptChoice.DENY)) }
        assertEquals(PermissionState.AUTO_BLOCKED, manager.overview("profile", site).getValue(PermissionType.MICROPHONE).state)
        assertFalse(broker.resolve(site, PermissionType.MICROPHONE))
        assertNull(broker.prompt.value)
        assertEquals(3, manager.overview("profile", site).getValue(PermissionType.MICROPHONE).denialCount)
    }

    @Test
    fun blockedPermissionCanBeRestoredToAsk() = runTest {
        val (broker, manager) = newBroker()
        repeat(3) { ask(broker, site, PermissionType.CAMERA, PromptChoice.DENY) }
        manager.setState("profile", site, PermissionType.CAMERA, PermissionState.ASK)
        assertTrue(ask(broker, site, PermissionType.CAMERA, PromptChoice.ALLOW))
    }

    @Test
    fun countersAreIndependentPerOriginAndPermission() = runTest {
        val (broker, manager) = newBroker()
        repeat(3) { ask(broker, site, PermissionType.CAMERA, PromptChoice.DENY) }
        assertEquals(PermissionState.ASK, manager.overview("profile", site).getValue(PermissionType.MICROPHONE).state)
        assertEquals(PermissionState.ASK, manager.overview("profile", "https://other.org").getValue(PermissionType.CAMERA).state)
    }

    @Test
    fun insecureOriginsCannotUseSensitivePermissions() = runTest {
        val (broker, _) = newBroker()
        assertFalse(broker.resolve("http://example.com", PermissionType.CAMERA))
        assertNull(broker.prompt.value)
    }

    @Test
    fun invalidOriginsAreRejected() = runTest {
        val (broker, _) = newBroker()
        assertFalse(broker.resolve(null, PermissionType.LOCATION))
        assertFalse(broker.resolve("javascript:alert(1)", PermissionType.LOCATION))
        assertFalse(broker.resolve("file:///sdcard/a.html", PermissionType.LOCATION))
    }
}
