package com.catsblock.materialex.core.permission

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemporaryGrantsTest {
    private val site = "https://example.com"

    @Test
    fun grantIsScopedToTabOriginAndType() {
        val grants = TemporaryGrants(1000)
        grants.grant("p", "tab1", site, PermissionType.CAMERA, 0)
        assertTrue(grants.isGranted("p", "tab1", site, PermissionType.CAMERA, 10))
        assertFalse(grants.isGranted("p", "tab2", site, PermissionType.CAMERA, 10))
        assertFalse(grants.isGranted("p", "tab1", "https://other.example", PermissionType.CAMERA, 10))
        assertFalse(grants.isGranted("p", "tab1", site, PermissionType.MICROPHONE, 10))
        assertFalse(grants.isGranted("q", "tab1", site, PermissionType.CAMERA, 10))
    }

    @Test
    fun grantExpires() {
        val grants = TemporaryGrants(1000)
        grants.grant("p", "tab1", site, PermissionType.CAMERA, 0)
        assertTrue(grants.isGranted("p", "tab1", site, PermissionType.CAMERA, 999))
        assertFalse(grants.isGranted("p", "tab1", site, PermissionType.CAMERA, 1000))
    }

    @Test
    fun tabClosureAndNavigationRevoke() {
        val grants = TemporaryGrants(1000)
        grants.grant("p", "tab1", site, PermissionType.CAMERA, 0)
        grants.revokeScopeOutside("tab1", site)
        assertTrue(grants.isGranted("p", "tab1", site, PermissionType.CAMERA, 1))
        grants.revokeScopeOutside("tab1", "https://other.example")
        assertFalse(grants.isGranted("p", "tab1", site, PermissionType.CAMERA, 1))
        grants.grant("p", "tab1", site, PermissionType.CAMERA, 0)
        grants.revokeScope("tab1")
        assertFalse(grants.isGranted("p", "tab1", site, PermissionType.CAMERA, 1))
    }

    @Test
    fun brokerAllowOnceDoesNotPersistAndSkipsPromptWithinScope() = runTest {
        val manager = PermissionManager(InMemoryPermissionStore())
        var now = 0L
        val broker = PermissionBroker(manager, "p") { now }
        val first = async { broker.resolve(site, PermissionType.CAMERA, "tab1") }
        runCurrent()
        broker.answer(PromptChoice.ALLOW_ONCE)
        assertTrue(first.await())
        assertEquals(PermissionState.ASK, manager.overview("p", site)[PermissionType.CAMERA]?.state)
        assertTrue(broker.resolve(site, PermissionType.CAMERA, "tab1"))
        assertNull(broker.prompt.value)
        broker.tabClosed("tab1")
        val again = async { broker.resolve(site, PermissionType.CAMERA, "tab1") }
        runCurrent()
        assertTrue(broker.prompt.value != null)
        broker.answer(PromptChoice.ALWAYS_ASK)
        assertFalse(again.await())
    }

    @Test
    fun allowOnceIsRefusedForTypesThatCannotBeEnforced() = runTest {
        val manager = PermissionManager(InMemoryPermissionStore())
        val broker = PermissionBroker(manager, "p")
        val result = async { broker.resolve(site, PermissionType.LOCATION, "tab1") }
        runCurrent()
        broker.answer(PromptChoice.ALLOW_ONCE)
        assertFalse(result.await())
    }
}
