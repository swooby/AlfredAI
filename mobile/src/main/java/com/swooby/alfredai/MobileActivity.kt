package com.swooby.alfredai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.swooby.Utils.getFriendlyPermissionName
import com.swooby.Utils.quote
import com.swooby.Utils.showToast
import com.swooby.alfredai.ui.theme.AlfredAITheme
import kotlinx.coroutines.flow.collectLatest

class MobileActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PushToTalkActivity"
    }

    private val mobileViewModel by lazy { (application as AlfredAiApp).mobileViewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate(savedInstanceState=$savedInstanceState)")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileApp(mobileViewModel)
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewLight() {
    MobileApp(MobileViewModelPreview())
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewDark() {
    MobileApp(MobileViewModelPreview())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MobileApp(mobileViewModel: MobileViewModelInterface) {
    @Suppress("LocalVariableName")
    val TAG = "MobileApp"

    val context = LocalContext.current

    //
    //region Conversation
    //

    /**
     * Intentionally not a StateFlow.collectAsState() State.
     * This allows the ViewModel to more efficiently update individual items.
     */
    val conversationItems = mobileViewModel.conversationItems

    //
    //endregion
    //

    val isConfigured = mobileViewModel.isConfigured.collectAsState()

    var showPreferences by remember { mutableStateOf(!isConfigured.value) }
    var onSaveButtonClick: (() -> Unit)? by remember { mutableStateOf(null) }

    //
    //region Connect/Disconnect
    //

    val isConnected = mobileViewModel.isConnected.collectAsState()
    val isConnectingOrConnected = mobileViewModel.isConnectingOrConnected.collectAsState()

    val isCancelingResponse = mobileViewModel.isCancelingResponse.collectAsState()

    //
    //endregion
    //

    //
    //region Permissions
    //

    val hasAllRequiredPermissions = mobileViewModel.hasAllRequiredPermissions.collectAsState()

    fun showPermissionsDeniedDialog(
        permissionsResult: Map<String, Boolean>,
        requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    ) {
        (context as? Activity)?.also { activity ->
            var anyDenied = false
            val shouldShowRequestPermissionRationale = mutableSetOf<String>()
            val requiredPermissions = mobileViewModel.requiredPermissions
            requiredPermissions.forEach { permission ->
                if (permissionsResult[permission] == false) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                        shouldShowRequestPermissionRationale.add(getFriendlyPermissionName(permission))
                    } else {
                        anyDenied = true
                    }
                }
            }
            if (shouldShowRequestPermissionRationale.isNotEmpty()) {
                // Show rationale dialog and allow the user to try again
                val permissionsText = shouldShowRequestPermissionRationale.joinToString("\", \"")
                AlertDialog.Builder(activity)
                    .setTitle("Permission(s) Required")
                    .setMessage("This app needs “${permissionsText}” permissions for full functionality.\nPlease grant the permission.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestPermissionsLauncher.launch(requiredPermissions)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                if (anyDenied) {
                    // User selected “don’t ask again” or system can’t show rationale.
                    // Guide the user to app settings.
                    AlertDialog.Builder(activity)
                        .setTitle("Permission(s) Required")
                        .setMessage("Some required permissions have been denied.\nPlease enable them in app settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            activity.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", activity.packageName, null)
                                }
                            )
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    requestPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult: Map<String, Boolean> ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            Log.i(TAG, "requestPermissionsLauncher: All required permissions granted")
            mobileViewModel.updatePermissionsState()
        } else {
            Log.w(TAG, "requestPermissionsLauncher: All required permissions NOT granted")
            showPermissionsDeniedDialog(permissionsResult, requestPermissionsLauncher!!)
        }
    }

    //
    //endregion
    //

    //
    //region UI
    //

    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)

    AlfredAITheme(
        dynamicColor = false,
        darkTheme = true,
    ) {
        Scaffold(modifier = Modifier
            .fillMaxSize(),
            topBar = {
                TopAppBar(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    title = { Text(if (showPreferences) "Preferences" else "PushToTalk") },
                    navigationIcon = {
                        if (showPreferences) {
                            IconButton(onClick = { showPreferences = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (showPreferences) {
                            TextButton(onClick = {
                                onSaveButtonClick?.invoke()
                            }) {
                                Text("Save")
                            }
                        } else {
                            Switch(
                                checked = isConnectingOrConnected.value,
                                onCheckedChange = { newValue ->
                                    if (newValue) {
                                        if (hasAllRequiredPermissions.value) {
                                            Log.i(TAG, "Manual Connect!")
                                            mobileViewModel.connect()
                                        } else {
                                            requestPermissionsLauncher.launch(mobileViewModel.requiredPermissions)
                                        }
                                    } else {
                                        Log.i(TAG, "Manual Disconnect!")
                                        mobileViewModel.disconnect(isManual = true)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = {
                                    showPreferences = true
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_settings_24),
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->

            if (isConnected.value) {
                KeepScreenOnComposable()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showPreferences) {
                    var interceptBack by remember { mutableStateOf(true) }
                    BackHandler(enabled = interceptBack) {
                        showPreferences = false
                        interceptBack = false
                    }
                    PushToTalkPreferenceScreen(
                        mobileViewModel = mobileViewModel,
                        onSaveSuccess = {
                            showPreferences = !isConfigured.value
                        },
                        setSaveButtonCallback = {
                            onSaveButtonClick = it
                        },
                    )
                } else {
                    BackHandler(enabled = isConnectingOrConnected.value) {
                        mobileViewModel.disconnect(isManual = true)
                    }
                    LaunchedEffect(Unit) {
                        if (!hasAllRequiredPermissions.value) {
                            requestPermissionsLauncher.launch(mobileViewModel.requiredPermissions)
                        }
                    }

                    //
                    //region Conversation
                    //
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color.LightGray, shape = RoundedCornerShape(8.dp))
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    modifier = Modifier.padding(start = 4.dp),
                                    text = "Conversation:",
                                )
                                IconButton(onClick = { mobileViewModel.conversationItemsClear() }) {
                                    Icon(
                                        painterResource(id = R.drawable.baseline_clear_all_24),
                                        contentDescription = "Clear All",
                                    )
                                }
                            }
                            if (conversationItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            Color.LightGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No conversation items",
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                var visibleTop by remember { mutableIntStateOf(0) }
                                var lastItemHeight by remember { mutableStateOf(0) }

                                val conversationListState = rememberLazyListState()

                                LaunchedEffect(conversationItems) {
                                    snapshotFlow { conversationItems.lastOrNull()?.text }
                                        .collectLatest {
                                            if (conversationItems.isNotEmpty()) {
                                                val lastIndex = conversationItems.size - 1
                                                conversationListState.animateScrollToItem(
                                                    index = lastIndex,
                                                    scrollOffset = lastItemHeight
                                                )
                                            }
                                        }
                                }

                                val lazyColumnContentPadding = 8

                                LazyColumn(
                                    modifier = Modifier
                                        .border(1.dp, Color.LightGray, shape = RoundedCornerShape(8.dp))
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            visibleTop = coordinates.positionInRoot().y.toInt()
                                        },
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(lazyColumnContentPadding.dp),
                                    state = conversationListState,
                                ) {
                                    items(
                                        count = conversationItems.size,
                                    ) { index ->
                                        var avatarHeight by remember { mutableIntStateOf(0) }
                                        var avatarOffsetY by remember { mutableIntStateOf(0) }

                                        val item = conversationItems[index]
                                        val avatarResId: Int
                                        val horizontalAlignment: Arrangement.Horizontal
                                        val contentAlignment: Alignment
                                        val textAlign: TextAlign

                                        when (item.type) {
                                            MobileViewModel.ConversationItemType.Local -> {
                                                avatarResId = R.drawable.baseline_person_24
                                                horizontalAlignment = Arrangement.Start
                                                contentAlignment = Alignment.CenterStart
                                                textAlign = TextAlign.Start
                                            }

                                            MobileViewModel.ConversationItemType.Function,
                                            MobileViewModel.ConversationItemType.Remote -> {
                                                avatarResId = R.drawable.baseline_memory_24
                                                horizontalAlignment = Arrangement.End
                                                contentAlignment = Alignment.CenterEnd
                                                textAlign = TextAlign.End
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min) // Makes Row as tall as the tallest item
                                                .onGloballyPositioned { coordinates ->
                                                    val firstVisibleItemIndex = conversationListState.firstVisibleItemIndex
                                                    val lastItemIndex = conversationItems.size - 1
                                                    when (index) {
                                                        firstVisibleItemIndex -> {
                                                            val positionInRoot = coordinates.positionInRoot()
                                                            val rowTop = positionInRoot.y.toInt()
                                                            val rowHeight = coordinates.size.height
                                                            val rowBottom = rowTop + rowHeight

                                                            if (rowTop < visibleTop) {
                                                                // top of the row/item is above the visible area;
                                                                // offset the avatar down
                                                                if (visibleTop + avatarHeight + lazyColumnContentPadding < rowBottom) {
                                                                    // the row/item has enough height to show the whole avatar;
                                                                    // show it at the top of the visible area
                                                                    avatarOffsetY = visibleTop - rowTop + lazyColumnContentPadding
                                                                } else {
                                                                    // the row/item does not have enough height to show the whole avatar;
                                                                    // let it scroll off the visible area
                                                                    avatarOffsetY = rowHeight - avatarHeight - lazyColumnContentPadding
                                                                }
                                                            } else {
                                                                // top of the row/item is at or in the visible area;
                                                                // restore the offset to 0
                                                                avatarOffsetY = 0
                                                            }
                                                        }
                                                        lastItemIndex -> {
                                                            lastItemHeight = coordinates.size.height
                                                        }
                                                    }
                                                },
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = horizontalAlignment
                                        ) {
                                            if (item.type == MobileViewModel.ConversationItemType.Local) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset { IntOffset(0, avatarOffsetY) },
                                                    contentAlignment = Alignment.TopCenter
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                                            avatarHeight = coordinates.size.height
                                                        },
                                                        painter = painterResource(id = avatarResId),
                                                        contentDescription = "Local",
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f),
                                                contentAlignment = contentAlignment
                                            ) {
                                                val text = when (item.type) {
                                                    MobileViewModel.ConversationItemType.Function -> {
                                                        val text = item.text.trimEnd('`')
                                                        "$text(${quote(item.functionArguments)})`"
                                                    }
                                                    else -> {
                                                        item.text
                                                    }
                                                }
                                                SelectionContainer {
                                                    Text(
                                                        modifier = Modifier
                                                            .border(1.dp, if (item.incomplete) Color.Yellow else Color.Gray, shape = RoundedCornerShape(8.dp))
                                                            .padding(8.dp),
                                                        text = text,
                                                        textAlign = textAlign,
                                                    )
                                                }
                                            }
                                            if (item.type == MobileViewModel.ConversationItemType.Remote ||
                                                item.type == MobileViewModel.ConversationItemType.Function) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .offset { IntOffset(0, avatarOffsetY) },
                                                    contentAlignment = Alignment.TopCenter
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                                            avatarHeight = coordinates.size.height
                                                        },
                                                        painter = painterResource(id = avatarResId),
                                                        contentDescription = "Remote",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var inputText by remember { mutableStateOf("") }

                                fun doSendInputText() {
                                    mobileViewModel.sendText(inputText)
                                    inputText = ""
                                }

                                TextField(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                                if (event.isCtrlPressed) {
                                                    doSendInputText()
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                                    singleLine = false,
                                    enabled = isConnected.value,
                                    label = { Text("Text Input") },
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    trailingIcon = {
                                        IconButton(
                                            enabled = isConnected.value && inputText.trim().isNotBlank(),
                                            onClick = { doSendInputText() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send"
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    //
                    //endregion
                    //

                    //
                    //region Reset, PushToTalk, Stop
                    //
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        //
                        //region Reset
                        //
                        Box(
                            modifier = Modifier
                                .border(
                                    4.dp,
                                    if (isConnected.value) MaterialTheme.colorScheme.primary else disabledColor,
                                    shape = CircleShape
                                ),
                        ) {
                            IconButton(
                                enabled = isConnected.value && !isCancelingResponse.value,
                                onClick = {
                                    showToast(
                                        context = context,
                                        text = "Reconnecting",
                                        forceInvokeOnMain = true,
                                    )
                                    mobileViewModel.reconnect()
                                },
                                modifier = Modifier
                                    .size(66.dp)
                                    // Whoever designed this `restart_alt` icon did it wrong.
                                    // They should have centered it in its border instead of having an asymmetric protrusion on the top.
                                    .offset(y = (-2).dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_restart_alt_24),
                                    contentDescription = "Reset",
                                    modifier = Modifier
                                        .size(50.dp)
                                )
                            }
                        }

                        //
                        //endregion
                        //

                        Spacer(modifier = Modifier.weight(1f))

                        //
                        //region PushToTalk
                        //
                        Box(
                            modifier = Modifier
                                .size(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box {
                                when {
                                    isConnected.value -> {
                                        CircularProgressIndicator(
                                            progress = { 1f },
                                            color = Color.Green,
                                            strokeWidth = 6.dp,
                                            modifier = Modifier.size(150.dp)
                                        )
                                    }

                                    isConnectingOrConnected.value -> {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 6.dp,
                                            modifier = Modifier.size(150.dp)
                                        )
                                    }

                                    else -> {
                                        CircularProgressIndicator(
                                            progress = { 0f },
                                            color = disabledColor,
                                            strokeWidth = 6.dp,
                                            modifier = Modifier.size(150.dp)
                                        )
                                    }
                                }
                            }
                            PushToTalkButton(
                                mobileViewModel = mobileViewModel,
                                enabled = isConnected.value && !isCancelingResponse.value,
                                onPushToTalkStart = {
                                    mobileViewModel.pushToTalk(true)
                                },
                                onPushToTalkStop = {
                                    mobileViewModel.pushToTalk(false)
                                },
                            )
                        }

                        //
                        //endregion
                        //

                        Spacer(modifier = Modifier.weight(1f))

                        //
                        //region Stop
                        //
                        Box(
                            modifier = Modifier
                                .border(
                                    4.dp,
                                    if (isConnected.value) MaterialTheme.colorScheme.primary else disabledColor,
                                    shape = CircleShape
                                ),
                        ) {
                            IconButton(
                                enabled = isConnected.value && !isCancelingResponse.value,
                                onClick = {
                                    // If true, will be set back to false in onServerEventOutputAudioBufferAudioStopped
                                    mobileViewModel.sendCancelResponse()
                                    /*
                                    It takes a REALLY long time for `output_audio_buffer.audio_stopped` to be received.
                                    I have seen it take up to 20 seconds (proportional to the amount of speech in response)
2025-01-22 16:38:45.287 17703-17703 RealtimeClient          com.swooby.alfredai                  D  dataSend: text="{"type":"response.cancel","event_id":"evt_SbrzipkcoeecHwabk"}"
...
2025-01-22 16:38:45.590 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.audio.done"
2025-01-22 16:38:45.601 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseAudioDone(RealtimeServerEventResponseAudioDone(eventId=event_AsfX3PzYdfufTHPO6EgGQ, type=responsePeriodAudioPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, itemId=item_AsfX2leMQadkxBACGBdgr, outputIndex=0, contentIndex=0))
2025-01-22 16:38:45.601 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.audio_transcript.done"
2025-01-22 16:38:45.606 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseAudioTranscriptDone(RealtimeServerEventResponseAudioTranscriptDone(eventId=event_AsfX3A3s59KvfvVMZI0ws, type=responsePeriodAudio_transcriptPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, itemId=item_AsfX2leMQadkxBACGBdgr, outputIndex=0, contentIndex=0, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with))
2025-01-22 16:38:45.606 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.content_part.done"
2025-01-22 16:38:45.619 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseContentPartDone(RealtimeServerEventResponseContentPartDone(eventId=event_AsfX3kZXNbclgSThE1QMJ, type=responsePeriodContent_partPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, itemId=item_AsfX2leMQadkxBACGBdgr, outputIndex=0, contentIndex=0, part=RealtimeServerEventResponseContentPartDonePart(type=audio, text=null, audio=null, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with)))
2025-01-22 16:38:45.620 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.output_item.done"
2025-01-22 16:38:45.624 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseOutputItemDone(RealtimeServerEventResponseOutputItemDone(eventId=event_AsfX3US6OdHBIghibc0j4, type=responsePeriodOutput_itemPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, outputIndex=0, item=RealtimeConversationItem(id=item_AsfX2leMQadkxBACGBdgr, type=message, object=realtimePeriodItem, status=incomplete, role=assistant, content=[RealtimeConversationItemContent(type=audio, text=null, id=null, audio=null, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with)], callId=null, name=null, arguments=null, output=null)))
2025-01-22 16:38:45.624 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.done"
2025-01-22 16:38:45.631 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseDone(RealtimeServerEventResponseDone(eventId=event_AsfX3UkPktMYSvQYvrS4h, type=responsePeriodDone, response=RealtimeResponse(id=resp_AsfX2yF5teyOCOyVSTCow, object=realtimePeriodResponse, status=cancelled, statusDetails=RealtimeResponseStatusDetails(type=cancelled, reason=client_cancelled, error=null), output=[RealtimeConversationItem(id=item_AsfX2leMQadkxBACGBdgr, type=message, object=realtimePeriodItem, status=incomplete, role=assistant, content=[RealtimeConversationItemContent(type=audio, text=null, id=null, audio=null, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with)], callId=null, name=null, arguments=null, output=null)], metadata=null, usage=RealtimeResponseUsage(totalTokens=400, inputTokens=167, outputTokens=233, inputTokenDetails=RealtimeResponseUsageInputTokenDetails(cachedTokens=0, textTokens=130, audioTokens=37), outputTokenDetails=RealtimeResponseUsageOutputTokenDetails(textTokens=50, audioTokens=183)))))
...
2025-01-22 16:38:51.975 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="output_audio_buffer.audio_stopped"
2025-01-22 16:38:51.976 17703-17785 RealtimeClient          com.swooby.alfredai                  W  onDataChannelText: unknown type=output_audio_buffer.audio_stopped
                                    */
                                    if (isCancelingResponse.value) {
                                        showToast(
                                            context = context,
                                            text = "Cancelling Response; this can take a long time to complete.",
                                            duration = Toast.LENGTH_LONG,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(66.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_stop_24),
                                    contentDescription = "Stop",
                                    modifier = Modifier
                                        .size(50.dp)
                                )
                            }
                        }

                        //
                        //endregion
                        //
                    }

                    val audioDevices = mobileViewModel.audioDevices.collectAsState()
                    val selectedAudioDevice = mobileViewModel.selectedAudioDevice.collectAsState()
                    if (audioDevices.value.isNotEmpty() && selectedAudioDevice.value != null) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            TextField(
                                readOnly = true,
                                value = selectedAudioDevice.value!!.name,
                                onValueChange = { /* read-only; ignore */ },
                                label = { Text("Audio Device") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(
                                        type = MenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                audioDevices.value.forEach { audioDevice ->
                                    DropdownMenuItem(
                                        text = { Text(audioDevice.name) },
                                        onClick = {
                                            mobileViewModel.selectAudioDevice(audioDevice)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //
    //endregion
    //
}

@Composable
fun KeepScreenOnComposable() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }
}
