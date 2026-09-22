package com.disspear574.earshot.linux

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
import com.disspear574.earshot.core.CallStatus
import com.disspear574.earshot.core.NodeState
import com.disspear574.earshot.core.PeerView
import kotlinx.coroutines.delay

@Composable
fun PeerListScreen(
    state: NodeState,
    strings: Strings,
    onRename: (String) -> Unit,
    onCall: (String, Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onPause: (Int?) -> Unit,
    onQuit: () -> Unit,
) {
    val colors = LocalEarshotColors.current
    var nameDraft by remember(state.myDisplayName) { mutableStateOf(state.myDisplayName) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
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

        Text(
            state.myFingerprint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var open by remember { mutableStateOf(false) }
            val paused = state.pausedUntilEpochSeconds != null
            OutlinedButton(
                onClick = { open = true },
                shape = RoundedCornerShape(Yaru.buttonRadius),
            ) { Text(if (paused) strings.paused else strings.pause) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(strings.minutes15) }, onClick = { onPause(15); open = false })
                DropdownMenuItem(text = { Text(strings.hour1) }, onClick = { onPause(60); open = false })
                DropdownMenuItem(text = { Text(strings.untilTomorrow) }, onClick = { onPause(60 * 12); open = false })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(strings.resume) }, onClick = { onPause(null); open = false })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onQuit) { Text(strings.quit) }
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
        val nearby = state.peers.filter { !it.isTrusted && it.keyHex !in pendingKeys }

        if (contacts.isNotEmpty()) {
            SectionLabel(strings.contacts)
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(Yaru.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                contacts.forEachIndexed { index, peer ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    ContactRow(peer, state.statuses[peer.keyHex], strings, onCall)
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
) {
    val colors = LocalEarshotColors.current
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

        TextButton(
            onClick = { onCall(peer.keyHex, false) },
            enabled = callable,
            shape = RoundedCornerShape(Yaru.buttonRadius),
        ) { Text("ping") }

        HoldButton(enabled = callable, label = strings.holdForUrgent) { onCall(peer.keyHex, true) }
    }
}

@Composable
private fun StatusDot(peer: PeerView, colors: EarshotColors) {
    // Залитый кружок против полого: различимо и в оттенках серого.
    when {
        peer.isPaused -> Box(Modifier.size(8.dp).clip(CircleShape).background(colors.paused))
        peer.isOnline -> Box(Modifier.size(8.dp).clip(CircleShape).background(colors.online))
        else -> Box(Modifier.size(8.dp).clip(CircleShape).border(1.5.dp, colors.offline, CircleShape))
    }
}

@Composable
private fun HoldButton(enabled: Boolean, label: String, onFire: () -> Unit) {
    val colors = LocalEarshotColors.current
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
