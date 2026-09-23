package com.disspear574.nearping.linux

import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disspear574.nearping.core.CallStatus
import com.disspear574.nearping.core.NodeState
import com.disspear574.nearping.core.PeerView
import kotlinx.coroutines.delay

@Composable
fun PeerListScreen(
    state: NodeState,
    strings: Strings,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onRename: (String) -> Unit,
    onCall: (String, Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onForget: (String) -> Unit,
    onPause: (Int?) -> Unit,
    onQuit: () -> Unit,
    onSelectNetwork: (String?) -> Unit,
    onRefreshNetworks: () -> Unit,
) {
    var showingSettings by remember { mutableStateOf(false) }
    var showPause by remember { mutableStateOf(false) }
    var contactToRemove by remember { mutableStateOf<PeerView?>(null) }
    var nameDraft by remember(state.myDisplayName) { mutableStateOf(state.myDisplayName) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (showingSettings) {
                TextButton(onClick = { showingSettings = false }) { Text(strings.back) }
                Text(strings.settings, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            } else {
                Image(painterResource("nearping.png"), contentDescription = null, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(8.dp))
                Text("NearPing", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.weight(1f))
                Text(state.myDisplayName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                TextButton(onClick = { showingSettings = true }, modifier = Modifier.size(34.dp),
                    contentPadding = PaddingValues(0.dp)) { Text("⚙", fontSize = 20.sp) }
            }
        }
        AnimatedContent(targetState = showingSettings, label = "settings") { settingsVisible ->
            Column {
                if (settingsVisible) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            label = { Text(strings.yourName) },
                            singleLine = true,
                            shape = RoundedCornerShape(Yaru.buttonRadius),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { onRename(nameDraft) },
                            shape = RoundedCornerShape(Yaru.buttonRadius),
                            enabled = nameDraft.isNotBlank() && nameDraft != state.myDisplayName,
                        ) { Text("OK") }
                    }

                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(strings.appearance, style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            listOf(
                                ThemeMode.SYSTEM to strings.systemTheme,
                                ThemeMode.LIGHT to strings.lightTheme,
                                ThemeMode.DARK to strings.darkTheme,
                            ).forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = { onTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                        Text(strings.discoveryNetwork, style = MaterialTheme.typography.labelLarge)
                        var networkMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { onRefreshNetworks(); networkMenu = true },
                                enabled = !state.networkChanging,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(state.selectedInterface ?: strings.automaticNetwork, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                                DropdownMenuItem(text = { Text(strings.automaticNetwork) }, onClick = {
                                    networkMenu = false; onSelectNetwork(null)
                                })
                                state.networkInterfaces.forEach { network ->
                                    DropdownMenuItem(text = {
                                        Column(Modifier.widthIn(max = 320.dp)) {
                                            Text(network.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(network.addresses, style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (!network.multicast) Text(strings.noMulticast, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }, onClick = { networkMenu = false; onSelectNetwork(network.name) })
                                }
                            }
                        }
                        Text(when {
                            state.networkChanging -> strings.networkChanging
                            state.networkFailed -> strings.networkFailed
                            else -> state.activeInterface ?: strings.networkNotConnected
                        }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(strings.virtualNetworkHint, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Text(
                        state.myFingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { onRename(nameDraft); showingSettings = false },
                            enabled = nameDraft.isNotBlank(),
                        ) { Text(strings.save) }
                    }

                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val paused = state.pausedUntilEpochSeconds != null
                        if (paused) Text("Ⅱ", color = LocalNearPingColors.current.paused, fontWeight = FontWeight.Bold)
                        else Box(Modifier.size(10.dp).clip(CircleShape).background(LocalNearPingColors.current.online))
                        Spacer(Modifier.width(8.dp))
                        Text(if (paused) strings.paused else strings.available,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (paused) onPause(null) else showPause = true }) {
                            Text(if (paused) strings.resume else "Ⅱ ${strings.pause}")
                        }
                    }

                    if (state.pendingRequests.isNotEmpty()) {
                        SectionLabel(strings.pairingRequests)
                        state.pendingRequests.forEach { peer ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(peer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        peer.fingerprint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { onAccept(peer.keyHex) }, shape = RoundedCornerShape(Yaru.buttonRadius)) {
                                    Text(strings.accept)
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { onDecline(peer.keyHex) }, shape = RoundedCornerShape(Yaru.buttonRadius)) {
                                    Text(strings.decline)
                                }
                            }
                        }
                    }

                    val contacts = state.peers.filter { it.isTrusted }
                    val pendingKeys = state.pendingRequests.map { it.keyHex }.toSet()
                    val nearby = state.peers.filter { !it.isTrusted && it.isOnline && it.keyHex !in pendingKeys }

                    if (contacts.isNotEmpty()) {
                        SectionLabel(strings.contacts)
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(Yaru.cardRadius),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            contacts.forEachIndexed { index, peer ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                ContactRow(peer, state.statuses[peer.keyHex], strings, onCall) {
                                    contactToRemove = peer
                                }
                            }
                        }
                    }

                    SectionLabel(strings.nearby)
                    if (nearby.isEmpty()) {
                        Text(
                            strings.nobodyNearby,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        nearby.forEach { peer ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(peer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        state.statuses[peer.keyHex]?.text ?: peer.fingerprint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onAdd(peer.keyHex) },
                                    enabled = peer.isOnline,
                                    shape = RoundedCornerShape(Yaru.buttonRadius),
                                ) { Text(strings.add) }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.urgentInMenu, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onQuit) { Text(strings.quit) }
                    }
                }
            }
        }
    }
    if (showPause) {
        var minutes by remember { mutableStateOf("30") }
        val valid = minutes.toIntOrNull()?.let { it in 1..10080 } == true
        AlertDialog(
            onDismissRequest = { showPause = false },
            title = { Text(strings.pause) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(5) },
                        label = { Text(strings.minutes) }, singleLine = true)
                    Text(strings.pauseRange, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { onPause(minutes.toInt()); showPause = false }, enabled = valid) {
                    Text(strings.pause)
                }
            },
            dismissButton = { TextButton(onClick = { showPause = false }) { Text(strings.cancel) } },
        )
    }
    contactToRemove?.let { peer ->
        AlertDialog(
            onDismissRequest = { contactToRemove = null },
            title = { Text(strings.removeContactQuestion) },
            text = { Text(peer.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                Button(
                    onClick = { onForget(peer.keyHex); contactToRemove = null },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalNearPingColors.current.urgent),
                ) { Text(strings.removeContact) }
            },
            dismissButton = {
                TextButton(onClick = { contactToRemove = null }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ContactRow(
    peer: PeerView,
    status: CallStatus?,
    strings: Strings,
    onCall: (String, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalNearPingColors.current
    val callable = peer.isTrusted && peer.isOnline && !peer.isPaused

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(peer.displayName), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))

        // Имя произвольной длины не должно выдавливать точку и кнопку за край.
        Column(Modifier.weight(1f)) {
            Text(peer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status?.text ?: when {
                    peer.isPaused -> strings.pausedUntil
                    peer.isOnline -> strings.online
                    else -> strings.offline
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (status?.kind) {
                    CallStatus.Kind.GOOD -> colors.online
                    CallStatus.Kind.BAD -> colors.urgent
                    CallStatus.Kind.WARN -> colors.paused
                    else -> if (peer.isPaused) colors.paused else MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))

        StatusDot(peer, colors)
        Spacer(Modifier.width(10.dp))

        var actions by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { actions = true }, modifier = Modifier.width(32.dp),
                contentPadding = PaddingValues(0.dp)) { Text("⋯") }
            DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.holdForUrgent, modifier = Modifier.widthIn(max = 200.dp))
                    Spacer(Modifier.width(12.dp))
                    HoldButton(enabled = callable, label = strings.holdForUrgent) {
                        actions = false; onCall(peer.keyHex, true)
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(strings.removeContact) }, onClick = {
                    actions = false
                    onRemove()
                })
            }
        }
        Button(
            onClick = { onCall(peer.keyHex, false) },
            enabled = callable,
            modifier = Modifier.width(88.dp).height(42.dp),
            shape = RoundedCornerShape(Yaru.buttonRadius),
        ) { Text("Ping", fontWeight = FontWeight.SemiBold) }

    }
}

@Composable
private fun StatusDot(peer: PeerView, colors: NearPingColors) {
    // Залитый кружок против полого: различимо и в оттенках серого.
    when {
        peer.isPaused -> Box(Modifier.size(8.dp).clip(CircleShape).background(colors.paused))
        peer.isOnline -> Box(Modifier.size(8.dp).clip(CircleShape).background(colors.online))
        else -> Box(Modifier.size(8.dp).clip(CircleShape).border(1.5.dp, colors.offline, CircleShape))
    }
}

@Composable
private fun HoldButton(enabled: Boolean, label: String, onFire: () -> Unit) {
    val colors = LocalNearPingColors.current
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(holding) {
        if (!holding) { progress = 0f; return@LaunchedEffect }
        val step = 0.02f
        while (progress < 1f) {
            delay(20)
            progress += step / HOLD_SECONDS
        }
        holding = false
        onFire()
    }

    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (enabled) colors.urgent else colors.urgent.copy(alpha = 0.3f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (holding) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(30.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }
        Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private const val HOLD_SECONDS = 0.9f

private fun initials(name: String): String {
    val letters = name.trim().split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString("")
    return letters.ifEmpty { "?" }.uppercase()
}
