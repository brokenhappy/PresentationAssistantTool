package com.woutwerkman.pa

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.woutwerkman.pa.ble.*
import com.woutwerkman.pa.platform.GlobalShortcutManager
import com.woutwerkman.pa.platform.InputMonitoringPermission
import com.woutwerkman.pa.platform.PlatformFileSystem
import com.woutwerkman.pa.platform.SpotlightManager
import com.woutwerkman.pa.presentation.PresentationEngine
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.presentation.TimingAlerts
import com.woutwerkman.pa.repository.AppSettings
import com.woutwerkman.pa.repository.ProfileRepository
import com.woutwerkman.pa.ui.expanded.ExpandedView
import com.woutwerkman.pa.ui.minified.MinifiedView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlinx.coroutines.*
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun main() {
    InputMonitoringPermission.request()
    application {
    val engineScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val fileSystem = remember { PlatformFileSystem(System.getProperty("user.home") + "/.presentationassistant") }
    val repository = remember { ProfileRepository(fileSystem) }
    val appSettings = remember { AppSettings(fileSystem) }
    val engine = remember { PresentationEngine(repository, engineScope) }
    val peerStorage = remember { PeerStorage(fileSystem) }
    val bleService = remember { DesktopBleService(engineScope, peerStorage) }
    val state by engine.state.collectAsState()
    val connectedPeers by bleService.connectedPeers.collectAsState()
    val bleConnectionState by bleService.connectionState.collectAsState()
    val bleError by bleService.error.collectAsState()
    val spotlightManager = remember { SpotlightManager(engineScope, engine::onEvent) }
    val spotlightConnected by spotlightManager.connected.collectAsState()

    var showMinified by remember { mutableStateOf(true) }
    var showExpanded by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }

    val trayIcon = remember { createTrayIcon() }

    LoadLastProfile(engine, appSettings)
    PersistProfilePath(engine, appSettings)
    ForwardEventsToMobile(engine, bleService, connectedPeers)
    HandleIncomingBleMessages(engine, bleService, state)
    SyncStateOnConnect(engine, bleService, connectedPeers)
    AutoReconnect(bleService)

    DisposableEffect(engine) {
        val shortcutManager = GlobalShortcutManager {
            engine.onEvent(PresentationEvent.Advance)
        }
        shortcutManager.register()
        onDispose { shortcutManager.unregister() }
    }

    DisposableEffect(spotlightManager) {
        spotlightManager.start()
        onDispose { spotlightManager.stop() }
    }

    SpotlightTimingAlerts(engine, spotlightManager)

    AppTray(
        trayIcon = trayIcon,
        state = state,
        showMinified = showMinified,
        spotlightConnected = spotlightConnected,
        onToggleMinified = { showMinified = !showMinified },
        onShowExpanded = { showExpanded = true },
        onShowConnection = { showConnection = true },
        onCloseProfile = { engine.onEvent(PresentationEvent.CloseProfile) },
    )

    if (showMinified) {
        MinifiedWindow(
            state = state,
            engine = engine,
            onHide = { showMinified = false },
            onExpand = { showExpanded = true },
        )
    }

    if (showExpanded) {
        Window(
            onCloseRequest = { showExpanded = false },
            title = state.profile?.title ?: "Expanded View",
            state = rememberWindowState(size = DpSize(500.dp, 650.dp)),
        ) {
            AppTheme {
                ExpandedView(state = state, onEvent = engine::onEvent)
            }
        }
    }

    if (showConnection) {
        Window(
            onCloseRequest = { showConnection = false },
            title = "Connect Device",
            state = rememberWindowState(size = DpSize(450.dp, 500.dp)),
        ) {
            val pairedPeers by produceState(emptyList<PairedPeer>()) {
                value = bleService.getPersistedPeers()
            }
            AppTheme {
                DesktopConnectionView(
                    bleService = bleService,
                    connectionState = bleConnectionState,
                    connectedPeers = connectedPeers,
                    pairedPeers = pairedPeers,
                    bleError = bleError,
                )
            }
        }
    }
    }
}

@Composable
private fun LoadLastProfile(engine: PresentationEngine, appSettings: AppSettings) {
    LaunchedEffect(Unit) {
        val lastPath = appSettings.load().lastProfilePath
        if (lastPath != null && File(lastPath).exists()) {
            engine.onEvent(PresentationEvent.LoadProfile(lastPath))
        }
    }
}

@Composable
private fun PersistProfilePath(engine: PresentationEngine, appSettings: AppSettings) {
    LaunchedEffect(engine.profilePath) {
        appSettings.setLastProfilePath(engine.profilePath)
    }
}

@Composable
private fun ForwardEventsToMobile(
    engine: PresentationEngine,
    bleService: DesktopBleService,
    connectedPeers: List<PairedPeer>,
) {
    val currentPeers by rememberUpdatedState(connectedPeers)
    LaunchedEffect(bleService) {
        engine.appliedEvents.collect { event ->
            if (currentPeers.isNotEmpty()) {
                when (event) {
                    is PresentationEvent.LoadProfile,
                    is PresentationEvent.CloseProfile ->
                        bleService.sendMessage(BleMessage.FullSync(engine.state.value.forBleSync()))
                    else -> bleService.sendMessage(BleMessage.Event(event))
                }
            }
        }
    }
}

@Composable
private fun HandleIncomingBleMessages(
    engine: PresentationEngine,
    bleService: DesktopBleService,
    state: PresentationState,
) {
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(bleService) {
        bleService.incomingMessages.collect { message ->
            when (message) {
                is BleMessage.Event -> engine.onEvent(message.event)
                is BleMessage.SyncRequest ->
                    bleService.sendMessage(BleMessage.FullSync(currentState.forBleSync()))
                is BleMessage.FullSync -> {}
            }
        }
    }
}

@Composable
private fun SyncStateOnConnect(
    engine: PresentationEngine,
    bleService: DesktopBleService,
    connectedPeers: List<PairedPeer>,
) {
    LaunchedEffect(connectedPeers) {
        if (connectedPeers.isNotEmpty()) {
            bleService.sendMessage(BleMessage.FullSync(engine.state.value.forBleSync()))
        }
    }
}

@Composable
private fun AutoReconnect(bleService: DesktopBleService) {
    LaunchedEffect(Unit) {
        bleService.startAdvertisingOrScanning()
    }
}

@Composable
private fun SpotlightTimingAlerts(
    engine: PresentationEngine,
    spotlightManager: SpotlightManager,
) {
    LaunchedEffect(spotlightManager) {
        TimingAlerts(engine.state, spotlightManager::vibrate).run()
    }
}

@Composable
private fun ApplicationScope.AppTray(
    trayIcon: Painter,
    state: PresentationState,
    showMinified: Boolean,
    spotlightConnected: Boolean,
    onToggleMinified: () -> Unit,
    onShowExpanded: () -> Unit,
    onShowConnection: () -> Unit,
    onCloseProfile: () -> Unit,
) {
    Tray(
        icon = trayIcon,
        tooltip = "Presentation Assistant",
        menu = {
            Item(
                if (showMinified) "Hide Presentation" else "Show Presentation",
                onClick = onToggleMinified,
            )
            Item("Expanded View", onClick = onShowExpanded)
            Item("Connect Device", onClick = onShowConnection)
            Separator()
            Item(
                "Spotlight: ${if (spotlightConnected) "Connected" else "Not Found"}",
                enabled = false,
                onClick = {},
            )
            Separator()
            if (state.profile != null) {
                Item("Close Profile", onClick = onCloseProfile)
                Separator()
            }
            Item("Quit", onClick = ::exitApplication)
        },
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MinifiedWindow(
    state: PresentationState,
    engine: PresentationEngine,
    onHide: () -> Unit,
    onExpand: () -> Unit,
) {
    Window(
        onCloseRequest = onHide,
        title = "Presentation Assistant",
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        state = rememberWindowState(size = DpSize(520.dp, 48.dp)),
        resizable = true,
    ) {
        val dropTarget = remember {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val transferable = event.awtTransferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        val jsonFile = files.firstOrNull { it.extension == "json" }
                        if (jsonFile != null) {
                            engine.onEvent(PresentationEvent.LoadProfile(jsonFile.absolutePath))
                            return true
                        }
                    }
                    return false
                }
            }
        }

        val dropModifier = Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { true },
            target = dropTarget,
        )

        WindowDraggableArea {
            AppTheme {
                ContextMenuArea(
                    items = {
                        buildList {
                            if (state.isActive) {
                                add(ContextMenuItem("Advance") { engine.onEvent(PresentationEvent.Advance) })
                                add(ContextMenuItem("Go Back") { engine.onEvent(PresentationEvent.GoBack) })
                            }
                        }
                    }
                ) {
                    MinifiedView(
                        state = state,
                        onEvent = engine::onEvent,
                        onExpand = onExpand,
                        onHide = onHide,
                        modifier = dropModifier,
                    )
                }
            }
        }
    }
}

private fun createTrayIcon(): Painter {
    val size = 22
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = java.awt.Color(144, 202, 249)
    g.fillRoundRect(2, 2, size - 4, size - 4, 4, 4)
    g.color = java.awt.Color(13, 27, 42)
    g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 14)
    val fm = g.fontMetrics
    val text = "P"
    val x = (size - fm.stringWidth(text)) / 2
    val y = (size - fm.height) / 2 + fm.ascent
    g.drawString(text, x, y)
    g.dispose()
    return BitmapPainter(image.toComposeImageBitmap())
}
