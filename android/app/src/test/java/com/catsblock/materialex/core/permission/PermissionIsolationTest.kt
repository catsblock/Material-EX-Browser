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

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionRevocationTest {
    private val site = "https://example.com"

    @Test
    fun activeUseIsTakenOnlyForMatchingProfileOriginAndType() {
        val use = ActivePermissionUse()
        use.record("t1", "default", site, PermissionType.CAMERA)
        use.record("t2", "default", site, PermissionType.LOCATION)
        use.record("t3", "private", site, PermissionType.CAMERA)
        use.record("t4", "default", "https://other.com", PermissionType.CAMERA)
        assertEquals(setOf("t1"), use.take(Revocation("default", site, PermissionType.CAMERA)))
        assertEquals(emptySet<String>(), use.take(Revocation("default", site, PermissionType.CAMERA)))
        assertEquals(setOf("t2"), use.take(Revocation("default", site, null)))
        assertEquals(setOf("t4"), use.take(Revocation("default", null, null)))
        assertEquals(setOf("t3"), use.take(Revocation("private", null, null)))
    }

    @Test
    fun navigatingAwayOrClosingForgetsUse() {
        val use = ActivePermissionUse()
        use.record("t1", "default", site, PermissionType.CAMERA)
        use.dropOutside("t1", site)
        assertEquals(setOf("t1"), use.take(Revocation("default", site, null)))
        use.record("t1", "default", site, PermissionType.CAMERA)
        use.dropOutside("t1", "https://other.com")
        assertEquals(emptySet<String>(), use.take(Revocation("default", site, null)))
        use.record("t2", "default", site, PermissionType.CAMERA)
        use.dropTab("t2")
        assertEquals(emptySet<String>(), use.take(Revocation("default", null, null)))
    }

    @Test
    fun revokingAnAllowedPermissionNotifiesAndOtherChangesDoNot() = runTest {
        val manager = PermissionManager(InMemoryPermissionStore())
        val seen = mutableListOf<Revocation>()
        manager.onRevoked = { seen += it }
        manager.setState("default", site, PermissionType.CAMERA, PermissionState.ALLOW)
        assertTrue(seen.isEmpty())
        manager.setState("default", site, PermissionType.CAMERA, PermissionState.DENY)
        assertEquals(listOf(Revocation("default", site, PermissionType.CAMERA)), seen)
        manager.setState("default", site, PermissionType.CAMERA, PermissionState.ASK)
        assertEquals(1, seen.size)
        manager.clearProfile("default")
        assertEquals(Revocation("default", null, null), seen.last())
    }

    @Test
    fun revokingTemporaryGrantsNotifiesWithTheScopeProfile() = runTest {
        val broker = PermissionBroker(PermissionManager(InMemoryPermissionStore()), "default", { "p1" })
        val seen = mutableListOf<Revocation>()
        broker.onTemporaryRevoked = { seen += it }
        broker.revokeTemporary(site, PermissionType.MICROPHONE, "tab")
        broker.revokeTemporaryForOrigin(site, "tab")
        assertEquals(
            listOf(Revocation("p1", site, PermissionType.MICROPHONE), Revocation("p1", site, null)),
            seen
        )
    }
}
