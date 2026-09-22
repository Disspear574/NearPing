package com.disspear574.earshot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanInterfaceSelectorTest {
    @Test fun manualSelectionCanUseDockerInsteadOfAutomaticWifi() {
        val docker = LanInterfaceCandidate("docker0", "172.17.0.1", false)
        val wifi = LanInterfaceCandidate("wlp4s0", "192.168.0.20", true, 600)
        assertEquals(docker, LanInterfaceSelector.select(listOf(wifi, docker), "docker0"))
        assertEquals(wifi, LanInterfaceSelector.select(listOf(wifi, docker)))
    }
    @Test fun manualTunnelCanBeChosenButAutomaticSelectionSkipsIt() {
        val tunnel = LanInterfaceCandidate("tun0", "10.0.0.2", false, 1, false, true)
        assertEquals(tunnel, LanInterfaceSelector.select(listOf(tunnel), "tun0"))
        assertNull(LanInterfaceSelector.select(listOf(tunnel)))
    }
    @Test fun missingManualInterfaceDoesNotFallBackToDifferentNetwork() {
        assertNull(LanInterfaceSelector.select(listOf(
            LanInterfaceCandidate("wlp4s0", "192.168.0.20", true, 600)), "tun0"))
    }

    @Test fun dockerFirstDoesNotStealWifiDiscovery() {
        val wifi = LanInterfaceCandidate("wlp4s0", "192.168.0.20", true, 600)
        assertEquals(wifi, LanInterfaceSelector.select(listOf(
            LanInterfaceCandidate("br-ab1234", "172.18.0.1", false),
            LanInterfaceCandidate("docker0", "172.17.0.1", false), wifi)))
    }
    @Test fun vpnDefaultDoesNotStealLanDiscovery() {
        val wifi = LanInterfaceCandidate("wlp4s0", "192.168.0.20", true, 600)
        assertEquals(wifi, LanInterfaceSelector.select(listOf(
            LanInterfaceCandidate("wg0", "10.1.0.2", false, 0), wifi)))
    }
    @Test fun respectsRouteMetricRatherThanInterfaceEnumeration() {
        val ethernet = LanInterfaceCandidate("enp2s0", "192.168.0.21", true, 100)
        assertEquals(ethernet, LanInterfaceSelector.select(listOf(
            LanInterfaceCandidate("wlp4s0", "192.168.0.20", true, 600), ethernet)))
    }
    @Test fun physicalPrivate172NetworkIsAllowedWithoutDefaultRoute() {
        val ethernet = LanInterfaceCandidate("eth0", "172.18.0.7", true)
        assertEquals(ethernet, LanInterfaceSelector.select(listOf(
            LanInterfaceCandidate("br-ab1234", "172.18.0.1", false), ethernet)))
    }
    @Test fun realLanBridgeIsAllowed() {
        val bridge = LanInterfaceCandidate("br0", "192.168.0.21", false, 100)
        assertEquals(bridge, LanInterfaceSelector.select(listOf(bridge)))
    }
    @Test fun containerOnlyDoesNotSilentlyChooseContainer() {
        assertNull(LanInterfaceSelector.select(listOf(LanInterfaceCandidate("docker0", "172.17.0.1", false))))
    }
    @Test fun parsesActiveDefaultRoutesAndLowestMetric() {
        val table = """
            Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT
            wlp4s0 00000000 0100A8C0 0003 0 0 600 00000000 0 0 0
            wlp4s0 00000000 0100A8C0 0003 0 0 700 00000000 0 0 0
            enp2s0 00000000 0100A8C0 0002 0 0 100 00000000 0 0 0
            docker0 000012AC 00000000 0001 0 0 0 0000FFFF 0 0 0
            broken
        """.trimIndent()
        assertEquals(mapOf("wlp4s0" to 600), LanInterfaceSelector.defaultRouteMetrics(table))
    }
}
