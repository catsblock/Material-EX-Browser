package com.catsblock.materialex.core

import com.catsblock.materialex.core.profile.Profile
import com.catsblock.materialex.core.profile.ProfileRules
import com.catsblock.materialex.core.tabs.TabManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test
    fun namesAreNormalizedAndValidated() {
        assertEquals("Work stuff", ProfileRules.normalizeName("  Work   stuff "))
        assertNull(ProfileRules.normalizeName("   "))
        assertNull(ProfileRules.normalizeName("a".repeat(33)))
        assertNull(ProfileRules.normalizeName("bad\u0000name"))
    }

    @Test
    fun encodeDecodeRoundTripDropsInvalidAndDuplicateEntries() {
        val a = Profile(ProfileRules.newId(), "Work")
        val b = Profile(ProfileRules.newId(), "Home")
        val raw = ProfileRules.encode(listOf(a, b)) + "\nbad\tName\n${a.id}\tDup\nnotanid"
        assertEquals(listOf(a, b), ProfileRules.decode(raw))
        assertTrue(ProfileRules.decode(null).isEmpty())
        assertTrue(ProfileRules.decode("").isEmpty())
    }

    @Test
    fun onlyCustomProfilesGetIsolatedContextAndCanBeDeleted() {
        val id = ProfileRules.newId()
        assertEquals(id, ProfileRules.contextId(id, false))
        assertNull(ProfileRules.contextId(id, true))
        assertNull(ProfileRules.contextId(ProfileRules.DEFAULT_ID, false))
        assertNull(ProfileRules.contextId(ProfileRules.GUEST_ID, false))
        assertTrue(ProfileRules.canDelete(id))
        assertFalse(ProfileRules.canDelete(ProfileRules.DEFAULT_ID))
        assertFalse(ProfileRules.canDelete(ProfileRules.GUEST_ID))
        assertTrue(ProfileRules.isRealProfile(ProfileRules.DEFAULT_ID))
        assertFalse(ProfileRules.isRealProfile(ProfileRules.GUEST_ID))
    }

    @Test
    fun duplicateNamesAreCaseInsensitive() {
        val p = Profile(ProfileRules.newId(), "Work")
        assertTrue(ProfileRules.nameTaken(listOf(p), "work"))
        assertFalse(ProfileRules.nameTaken(listOf(p), "work", exceptId = p.id))
    }

    @Test
    fun removingAProfilePurgesItsTabsAndClosedHistoryOnly() {
        val manager = TabManager()
        val keep = manager.open("https://a.example", "default", false)
        val guest1 = manager.open("https://g1.example", ProfileRules.GUEST_ID, true)
        manager.open("https://g2.example", ProfileRules.GUEST_ID, true)
        manager.close(guest1.id)
        manager.select(keep.id)
        val removed = manager.removeProfile(ProfileRules.GUEST_ID)
        assertEquals(1, removed.size)
        val state = manager.state.value
        assertEquals(listOf(keep.id), state.tabs.map { it.id })
        assertTrue(state.recentlyClosed.isEmpty())
        assertEquals(keep.id, state.activeId)
    }
}
