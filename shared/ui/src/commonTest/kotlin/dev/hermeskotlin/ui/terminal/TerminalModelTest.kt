package dev.hermeskotlin.ui.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalModelTest {

    @Test
    fun theFirstHostKeyIsTrustedAndRemembered() {
        assertEquals(HostKeyVerdict.TrustFirstUse, hostKeyVerdict(remembered = null, presented = "SHA256:abc"))
    }

    @Test
    fun theSameHostKeyMatches() {
        assertEquals(HostKeyVerdict.Match, hostKeyVerdict("SHA256:abc", "SHA256:abc"))
    }

    @Test
    fun aDifferentHostKeyIsRefused() {
        assertEquals(HostKeyVerdict.Mismatch, hostKeyVerdict("SHA256:abc", "SHA256:xyz"))
    }

    @Test
    fun hostKeysAreKeptPerHost() {
        assertEquals("terminal.hostkey.100.78.230.91", hostKeyStoreKey("100.78.230.91"))
    }

    @Test
    fun loginNamesAreLinuxStyle() {
        assertTrue(isValidTerminalUser("dayltan"))
        assertTrue(isValidTerminalUser("_svc-1"))
        assertFalse(isValidTerminalUser(""))
        assertFalse(isValidTerminalUser("Dayl"))
        assertFalse(isValidTerminalUser("a b"))
        assertFalse(isValidTerminalUser("a'; rm -rf ~"))
        assertFalse(isValidTerminalUser("a".repeat(33)))
    }

    @Test
    fun theAuthorizeCommandAppendsTheKey() {
        val command = authorizeCommand("ssh-ed25519 AAAAC3 herald")
        assertTrue(command.contains(">> ~/.ssh/authorized_keys"))
        assertTrue(command.contains("'ssh-ed25519 AAAAC3 herald'"))
    }
}
