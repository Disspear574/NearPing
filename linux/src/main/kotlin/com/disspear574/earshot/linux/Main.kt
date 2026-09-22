package com.disspear574.earshot.linux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.disspear574.earshot.core.EarshotNode
import com.disspear574.earshot.core.IncomingCall
import kotlinx.coroutines.delay
import java.nio.file.Path

private fun dataDirectory(): Path {
    val base = System.getenv("XDG_DATA_HOME")
        ?: (System.getProperty("user.home") + "/.local/share")
    return Path.of(base, "earshot")
}

fun main() = application {
    val strings = remember { Strings.current() }
    val node = remember { EarshotNode(dataDirectory()) }
    val state by node.state.collectAsState()
    var incoming by remember { mutableStateOf<IncomingCall?>(null) }

    LaunchedEffect(Unit) { node.start() }

    LaunchedEffect(Unit) {
        node.calls.collect { call ->
            Tone.play(call.urgent)
            if (call.urgent) {
                incoming = call
            } else {
                // Обычный вызов не требует подтверждения глазами: звук и есть сообщение.
                node.markSeen(call.keyHex, call.callId)
            }
        }
    }

    val quit = {
        node.stop()
        exitApplication()
    }

    Window(
        onCloseRequest = quit,
        title = strings.appName,
        state = rememberWindowState(width = 420.dp, height = 620.dp),
    ) {
        EarshotTheme {
            PeerListScreen(
                state = state,
                strings = strings,
                onRename = node::rename,
                onCall = { key, urgent -> node.call(key, urgent) },
                onAdd = node::requestPairing,
                onAccept = node::acceptPairing,
                onDecline = node::rejectPairing,
                onPause = node::pause,
                onQuit = quit,
            )
        }
    }

    incoming?.let { call ->
        UrgentWindow(
            call = call,
            strings = strings,
            onSeen = {
                Tone.stop()
                node.markSeen(call.keyHex, call.callId)
                incoming = null
            },
        )
    }
}

/**
 * Срочный вызов: отдельное окно поверх всего, без рамки.
 * Системные уведомления тут не участвуют — их глушит «не беспокоить».
 */
@Composable
private fun UrgentWindow(call: IncomingCall, strings: Strings, onSeen: () -> Unit) {
    var remaining by remember(call.callId) { mutableStateOf(1f) }

    LaunchedEffect(call.callId) {
        val step = 0.02f
        while (remaining > 0f) {
            delay(20)
            remaining -= step / AUTO_DISMISS_SECONDS
        }
        onSeen()
    }

    Window(
        onCloseRequest = onSeen,
        title = strings.appName,
        undecorated = true,
        alwaysOnTop = true,
        resizable = false,
        state = rememberWindowState(
            width = 460.dp,
            height = 108.dp,
            position = WindowPosition(Alignment.TopCenter),
        ),
    ) {
        EarshotTheme {
            val colors = LocalEarshotColors.current
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(colors.urgent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${call.displayName} ${strings.isCalling}",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            strings.urgent,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.urgent,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onSeen, shape = RoundedCornerShape(Yaru.buttonRadius)) {
                        Text(strings.gotIt)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(remaining.coerceIn(0f, 1f))
                            .background(colors.urgent)
                    )
                }
            }
        }
    }
}

private const val AUTO_DISMISS_SECONDS = 8f
