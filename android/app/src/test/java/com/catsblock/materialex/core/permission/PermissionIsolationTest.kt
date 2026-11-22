package com.catsblock.materialex.core.permission

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionIsolationTest {
    private val site = "https://example.com"
    private val persistent = InMemoryPermissionStore()
    private val ephemeral = InMemoryPermissionStore()
    private val manager = PermissionManager(RoutingPermissionStore(persistent, ephemeral))
    private val profiles = mapOf("normal" to "default", "other" to "p0123", "private" to EPHEMERAL_PERMISSION_PROFILE)
    private val broker = PermissionBroker(manager, "default", { scope -> profiles.getValue(scope) })

    private suspend fun TestScope.ask(scope: String, type: PermissionType, choice: PromptChoice): Boolean {
        val result = async { broker.resolve(site, type, scope) }
        runCurrent()
        assertNotNull(broker.prompt.value)
        broker.answer(choice)
        return result.await()
    }

    @Test
    fun privateGrantsNeverReachTheNormalProfileOrPersistentStorage() = runTest {
        assertTrue(ask("private", PermissionType.CAMERA, PromptChoice.ALLOW))
        assertTrue(broker.resolve(site, PermissionType.CAMERA, "private"))
        assertEquals(PermissionState.ASK, manager.overview("default", site).getValue(PermissionType.CAMERA).state)
        assertTrue(persistent.listForOrigin(EPHEMERAL_PERMISSION_PROFILE, site).isEmpty())
        assertFalse(ephemeral.listForOrigin(EPHEMERAL_PERMISSION_PROFILE, site).isEmpty())
    }

    @Test
    fun normalGrantsDoNotLeakIntoPrivateOrOtherProfiles() = runTest {
        assertTrue(ask("normal", PermissionType.MICROPHONE, PromptChoice.ALLOW))
        val privateAnswer = async { broker.resolve(site, PermissionType.MICROPHONE, "private") }
        runCurrent()
        assertNotNull(broker.prompt.value)
        broker.answer(PromptChoice.DENY)
        assertFalse(privateAnswer.await())
        val otherAnswer = async { broker.resolve(site, PermissionType.MICROPHONE, "other") }
        runCurrent()
        assertNotNull(broker.prompt.value)
        broker.answer(PromptChoice.DENY)
        assertFalse(otherAnswer.await())
        assertTrue(broker.resolve(site, PermissionType.MICROPHONE, "normal"))
    }

    @Test
    fun clearingTheEphemeralProfileForgetsPrivateDecisions() = runTest {
        assertTrue(ask("private", PermissionType.CAMERA, PromptChoice.ALLOW))
        manager.clearProfile(EPHEMERAL_PERMISSION_PROFILE)
        assertEquals(PermissionState.ASK, manager.overview(EPHEMERAL_PERMISSION_PROFILE, site).getValue(PermissionType.CAMERA).state)
    }

    @Test
    fun temporaryGrantsAreScopedPerProfile() = runTest {
        assertTrue(ask("private", PermissionType.CAMERA, PromptChoice.ALLOW_ONCE))
        assertTrue(broker.isTemporarilyAllowed(site, PermissionType.CAMERA, "private"))
        assertFalse(broker.isTemporarilyAllowed(site, PermissionType.CAMERA, "normal"))
        broker.temporary.revokeProfile(EPHEMERAL_PERMISSION_PROFILE)
        assertFalse(broker.isTemporarilyAllowed(site, PermissionType.CAMERA, "private"))
    }
}
