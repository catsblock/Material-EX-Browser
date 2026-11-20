package com.catsblock.materialex.core.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryGrantsTest {
    private var now = 0L
    private val grants = TemporaryGrants({ now }, 1000L)

    @Test
    fun grantIsScopedToProfileOriginTypeAndTab() {
        grants.grant("p", "https://a.com", PermissionType.CAMERA, "tab1")
        assertTrue(grants.isActive("p", "https://a.com", PermissionType.CAMERA, "tab1"))
        assertFalse(grants.isActive("p", "https://b.com", PermissionType.CAMERA, "tab1"))
        assertFalse(grants.isActive("p", "https://a.com", PermissionType.MICROPHONE, "tab1"))
        assertFalse(grants.isActive("p", "https://a.com", PermissionType.CAMERA, "tab2"))
        assertFalse(grants.isActive("q", "https://a.com", PermissionType.CAMERA, "tab1"))
    }

    @Test
    fun grantExpires() {
        grants.grant("p", "https://a.com", PermissionType.CAMERA, "tab1")
        now = 999L
        assertTrue(grants.isActive("p", "https://a.com", PermissionType.CAMERA, "tab1"))
        now = 1000L
        assertFalse(grants.isActive("p", "https://a.com", PermissionType.CAMERA, "tab1"))
    }

    @Test
    fun revokeScopeRemovesOnlyThatTab() {
        grants.grant("p", "https://a.com", PermissionType.CAMERA, "tab1")
        grants.grant("p", "https://a.com", PermissionType.CAMERA, "tab2")
        grants.revokeScope("tab1")
        assertFalse(grants.isActive("p", "https://a.com", PermissionType.CAMERA, "tab1"))
        assertTrue(grants.isActive("p", "https://a.com", PermissionType.CAMERA, "tab2"))
    }
}
