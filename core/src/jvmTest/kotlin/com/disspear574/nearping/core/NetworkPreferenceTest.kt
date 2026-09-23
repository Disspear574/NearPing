package com.disspear574.nearping.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class NetworkPreferenceTest {
    @Test fun selectionSurvivesRestartAndAutomaticClearsIt() {
        val directory = Files.createTempDirectory("nearping-network-test")
        try {
            val preference = NetworkPreference(directory)
            assertNull(preference.read())
            preference.write("tailscale0")
            assertEquals("tailscale0", NetworkPreference(directory).read())
            preference.write(null)
            assertNull(NetworkPreference(directory).read())
        } finally {
            Files.deleteIfExists(directory.resolve("network-interface.txt"))
            Files.delete(directory)
        }
    }
    @Test fun invalidSelectionDoesNotReplaceSavedNetwork() {
        val directory = Files.createTempDirectory("nearping-network-test")
        try {
            val preference = NetworkPreference(directory)
            preference.write("wlp4s0")
            assertFailsWith<IllegalArgumentException> { preference.write("bad\nname") }
            assertEquals("wlp4s0", preference.read())
        } finally {
            Files.deleteIfExists(directory.resolve("network-interface.txt"))
            Files.delete(directory)
        }
    }
}
