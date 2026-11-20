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
class PermissionBrokerOnceTest {
    private val site = "https://example.com"

    private suspend fun TestScope.ask(broker: PermissionBroker, scope: String, choice: PromptChoice): Boolean {
        val result = async { broker.resolve(site, PermissionType.CAMERA, scope) }
        runCurrent()
        assertNotNull(broker.prompt.value)
        broker.answer(choice)
        return result.await()
    }

    @Test
    fun allowOnceGrantsWithinTabWithoutPersisting() = runTest {
        val manager = PermissionManager(InMemoryPermissionStore())
        val broker = PermissionBroker(manager, "p")
        assertTrue(ask(broker, "tab1", PromptChoice.ALLOW_ONCE))
        assertTrue(broker.resolve(site, PermissionType.CAMERA, "tab1"))
        assertNull(broker.prompt.value)
        assertEquals(PermissionState.ASK, manager.overview("p", site).getValue(PermissionType.CAMERA).state)
    }

    @Test
    fun allowOnceDoesNotApplyToAnotherTabOrAfterRevocation() = runTest {
        val broker = PermissionBroker(PermissionManager(InMemoryPermissionStore()), "p")
        assertTrue(ask(broker, "tab1", PromptChoice.ALLOW_ONCE))
        assertFalse(ask(broker, "tab2", PromptChoice.ALWAYS_ASK))
        broker.temporary.revokeScope("tab1")
        assertFalse(ask(broker, "tab1", PromptChoice.ALWAYS_ASK))
    }

    @Test
    fun denyOverridesTemporaryGrant() = runTest {
        val manager = PermissionManager(InMemoryPermissionStore())
        val broker = PermissionBroker(manager, "p")
        assertTrue(ask(broker, "tab1", PromptChoice.ALLOW_ONCE))
        manager.setState("p", site, PermissionType.CAMERA, PermissionState.DENY)
        assertFalse(broker.resolve(site, PermissionType.CAMERA, "tab1"))
    }
}
