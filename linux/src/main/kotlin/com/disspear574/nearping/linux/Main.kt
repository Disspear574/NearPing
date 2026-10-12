package com.disspear574.nearping.linux

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import com.disspear574.nearping.core.NearPingNode
import com.disspear574.nearping.core.IncomingCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import javax.swing.SwingUtilities
import java.awt.MouseInfo
import java.awt.Toolkit
import androidx.compose.ui.input.key.*
import java.nio.file.Path
import java.util.prefs.Preferences

private fun dataDirectory(): Path {
    val base = System.getenv("XDG_DATA_HOME")
        ?: (System.getProperty("user.home") + "/.local/share")
    return Path.of(base, "nearping")
}

fun main(args: Array<String>) = application {
    val strings = remember { Strings.current() }
    val node = remember { NearPingNode(dataDirectory()) }
    val state by node.state.collectAsState()
    val preferences = remember { Preferences.userNodeForPackage(NearPingNode::class.java) }
    var themeMode by remember {
        mutableStateOf(
            runCatching { ThemeMode.valueOf(preferences.get("theme", ThemeMode.SYSTEM.name)) }
                .getOrDefault(ThemeMode.SYSTEM)
        )
    }
    var incoming by remember { mutableStateOf<IncomingCall?>(null) }
    val autostart = remember { Autostart(preferences).also { it.onStart() } }
    var launchAtLogin by remember { mutableStateOf(autostart.isEnabled()) }
    var windowVisible by remember { mutableStateOf(Autostart.BACKGROUND_ARG !in args) }
    var trayReady by remember { mutableStateOf(false) }
    var trayUnavailable by remember { mutableStateOf(false) }
    var notificationFailure by remember { mutableStateOf(false) }
    var showRequest by remember { mutableStateOf(0) }
    val mainWindowState = rememberWindowState(width = 340.dp, height = 540.dp)


    LaunchedEffect(Unit) { node.start() }

    LaunchedEffect(Unit) {
        node.calls.collect { call ->
            Tone.play(call.urgent)
            // Плашка одна: вытесненный вызов считается увиденным, иначе у его
            // отправителя навсегда останется «доставлено».
            incoming?.let { previous -> node.markSeen(previous.keyHex, previous.callId) }
            incoming = call
            val notificationText = if (call.urgent) strings.urgent else strings.wantsAttention
            // Предупреждение снимается, как только уведомления снова работают.
            notificationFailure = !DesktopNotifications.show(call.displayName, notificationText, call.urgent)
        }
    }

    val quit = {
        Tone.stop()
        node.stop()
        exitApplication()
    }

    LaunchedEffect(Unit) {
        try {
            PanelIndicator().run(strings) { event ->
                when (event) {
                    PanelEvent.READY -> { trayReady = true; trayUnavailable = false }
                    PanelEvent.UNAVAILABLE -> { trayReady = false; trayUnavailable = true; windowVisible = true }
                    PanelEvent.OPEN -> { windowVisible = true; mainWindowState.isMinimized = false; showRequest++ }
                    PanelEvent.PAUSE -> node.pause(15)
                    PanelEvent.RESUME -> node.pause(null)
                    PanelEvent.QUIT -> quit()
                }
            }
            trayReady = false
            trayUnavailable = true
            windowVisible = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            System.err.println("NearPing panel unavailable: ${error.message}")
            trayReady = false
            trayUnavailable = true
            windowVisible = true
        }
    }
    LaunchedEffect(Unit) {
        delay(5000)
        if (!trayReady) { trayUnavailable = true; windowVisible = true }
    }

    Window(
        visible = windowVisible,
        onCloseRequest = { if (trayReady) windowVisible = false else mainWindowState.isMinimized = true },
        title = strings.appName,
        undecorated = false,
        resizable = true,
        icon = painterResource("nearping.png"),
        state = mainWindowState,
        onKeyEvent = { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                if (trayReady) windowVisible = false else mainWindowState.isMinimized = true
                true
            } else false
        },
    ) {
        LaunchedEffect(showRequest, windowVisible) {
            if (windowVisible) SwingUtilities.invokeLater {
                if (showRequest == 1 && trayReady) {
                    val pointer = MouseInfo.getPointerInfo()
                    val config = pointer.device.defaultConfiguration
                    val bounds = config.bounds
                    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
                    val left = bounds.x + insets.left
                    val top = bounds.y + insets.top
                    val right = bounds.x + bounds.width - insets.right - window.width
                    val bottom = bounds.y + bounds.height - insets.bottom - window.height
                    window.setLocation((pointer.location.x - window.width + 20).coerceIn(left, maxOf(left, right)),
                        (pointer.location.y + 12).coerceIn(top, maxOf(top, bottom)))
                }
                window.toFront()
                window.requestFocus()
            }
        }
        NearPingTheme(themeMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                Column {
                    if (notificationFailure) {
                        Text(strings.notificationFailed, modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (trayUnavailable) {
                        Text(strings.trayUnavailable, modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    PeerListScreen(
                        state = state,
                        strings = strings,
                        themeMode = themeMode,
                        launchAtLogin = launchAtLogin.takeIf { autostart.isAvailable },
                        onLaunchAtLogin = { enabled ->
                            autostart.setEnabled(enabled)
                            launchAtLogin = autostart.isEnabled()
                        },
                        onTheme = { mode ->
                            themeMode = mode
                            preferences.put("theme", mode.name)
                        },
                        onRename = node::rename,
                        onCall = { key, urgent -> node.call(key, urgent) },
                        onAdd = node::requestPairing,
                        onAccept = node::acceptPairing,
                        onDecline = node::rejectPairing,
                        onForget = node::forget,
                        onPause = node::pause,
                        onQuit = quit,
                        onSelectNetwork = node::selectNetworkInterface,
                        onRefreshNetworks = node::refreshNetworkInterfaces,
                    )
                }
            }
        }
    }

    incoming?.let { call ->
        val edges = remember { ScreenEdges() }
        if (call.urgent) {
            DisposableEffect(call.callId) {
                edges.show()
                onDispose { edges.close() }
            }
        }
        IncomingWindow(
            call = call,
            strings = strings,
            themeMode = themeMode,
            onSeen = {
                Tone.stop()
                node.markSeen(call.keyHex, call.callId)
                incoming = null
            },
        )
    }
}

/**
 * Входящий Ping: отдельная плашка поверх остальных окон.
 * Сирена и красные края включаются только для срочного Ping.
 */
@Composable
private fun IncomingWindow(call: IncomingCall, strings: Strings, themeMode: ThemeMode, onSeen: () -> Unit) {
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
        NearPingTheme(themeMode) {
            val colors = LocalNearPingColors.current
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
              Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (call.urgent) colors.urgentFill else colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource("nearping.png"), contentDescription = "NearPing",
                            modifier = Modifier.size(40.dp))
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
                            if (call.urgent) strings.urgent else strings.wantsAttention,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (call.urgent) colors.urgent else colors.accent,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onSeen,
                        shape = RoundedCornerShape(Yaru.buttonRadius),
                        colors = if (call.urgent) {
                            ButtonDefaults.buttonColors(containerColor = colors.urgentFill, contentColor = Color.White)
                        } else ButtonDefaults.buttonColors(),
                    ) {
                        Text(strings.stop)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(remaining.coerceIn(0f, 1f))
                            .background(if (call.urgent) colors.urgentFill else colors.accent)
                    )
                }
              }
            }
        }
    }
}

private const val AUTO_DISMISS_SECONDS = 8f
