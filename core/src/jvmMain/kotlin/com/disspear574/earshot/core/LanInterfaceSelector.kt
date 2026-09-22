package com.disspear574.earshot.core

internal data class LanInterfaceCandidate(
    val name: String,
    val address: String,
    val physical: Boolean,
    val routeMetric: Int? = null,
    val multicast: Boolean = true,
    val pointToPoint: Boolean = false,
)

internal object LanInterfaceSelector {
    // Do not classify by IP range: 172.16/12 can also be a real LAN.
    private val nonLanName = Regex("^(docker[0-9]*|br-[0-9a-f]+|veth.*|virbr.*|tun[0-9]*|tap[0-9]*|wg.*|tailscale.*|zt.*|utun.*|cni.*|flannel.*)$")

    fun select(candidates: List<LanInterfaceCandidate>, preferredName: String? = null): LanInterfaceCandidate? {
        if (preferredName != null) return candidates.filter { it.name == preferredName }.minByOrNull { it.address }
        return candidates.filter { it.multicast && !it.pointToPoint && !nonLanName.matches(it.name) }
            .sortedWith(compareBy<LanInterfaceCandidate>(
                { if (it.routeMetric != null) 0 else 1 },
                { it.routeMetric ?: Int.MAX_VALUE },
                { if (it.physical) 0 else 1 },
                { if (it.name.startsWith("wl") || it.name.startsWith("en") || it.name.startsWith("eth")) 0 else 1 },
                { it.name }, { it.address },
            )).firstOrNull()
    }

    fun defaultRouteMetrics(table: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (line in table.lineSequence()) {
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 8 || fields[1] != "00000000" || fields[7] != "00000000") continue
            val flags = fields[3].toIntOrNull(16) ?: continue
            if (flags and 1 == 0 || flags and 0x200 != 0) continue
            val metric = fields[6].toIntOrNull()?.takeIf { it >= 0 } ?: continue
            val name = fields[0]
            result[name] = minOf(result[name] ?: Int.MAX_VALUE, metric)
        }
        return result
    }
}
