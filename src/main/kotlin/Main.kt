@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH", "DEPRECATION")

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.mikepenz.markdown.m3.Markdown
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.*

const val baseUrl = "https://api.onlysq.ru"

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xffff6a00),
            onPrimary = Color.Black,
            surface = Color(0xFF1E1E1E),
            background = Color.Black,
            primaryContainer = Color(0xFF333333),
            onPrimaryContainer = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xffff6a00),
            onPrimary = Color.White,
            surface = Color(0xFFF5F5F5),
            background = Color.White,
            primaryContainer = Color(0xFFE0E0E0),
            onPrimaryContainer = Color.Black
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    val appState = remember { mutableStateOf(Storage.load()) }
    val currentChatId = remember { mutableStateOf<String?>(null) }
    val inputText = remember { mutableStateOf(TextFieldValue("")) }
    val isSettingsOpen = remember { mutableStateOf(false) }
    val showMobileMenu = remember { mutableStateOf(false) }

    val availableModels = remember { mutableStateListOf<Triple<String, String, String>>() }
    val selectedModel = remember { mutableStateOf("gpt-5.2-chat") }
    val listState = rememberLazyListState()

    val isLoading = remember { mutableStateOf(false) }

    val isStreaming = remember { mutableStateOf(true) }



    LaunchedEffect(Unit) {
        try {
            val response: String = client.get("https://api.onlysq.ru/ai/models").bodyAsText()
            val root = jsonWorker.parseToJsonElement(response).jsonObject
            val textModels = root["classified"]?.jsonObject?.get("text")?.jsonObject

            if (textModels != null) {
                availableModels.clear()
                textModels.forEach { (id, modelData) ->
                    val name = modelData.jsonObject["name"]?.jsonPrimitive?.content ?: id
                    val owner = modelData.jsonObject["owner"]?.jsonPrimitive?.content ?: "Unknown"
                    availableModels.add(Triple(id, name, owner))
                }
            }
        } catch (e: Exception) {
            println("Model load err: ${e.message}")
            if (availableModels.isEmpty()) {
                availableModels.add(Triple("gpt-5.2-chat", "GPT-5.2 Chat", "OpenAI"))
                availableModels.add(Triple("qwen3-max", "Qwen3 Max", "Qwen"))
            }
        }
    }

    val currentChat = appState.value.chats.find { it.id == currentChatId.value }

    fun aiTitle() {
        val chat = appState.value.chats.find { it.id == currentChatId.value } ?: return

        val postbox = buildJsonObject {
            put("model", selectedModel.value)
            put("request", buildJsonObject {
                put("messages", buildJsonArray {
                    addJsonObject {
                        put("role", "system")
                        put(
                            "content",
                            "Look at that chat history and return ONLY short title for chat describing user or user's prompt. Max. chars: 25. Do not use quotes."
                        )
                    }
                    chat.messages.forEach { msg ->
                        addJsonObject {
                            put("role", msg.role)
                            put("content", msg.content)
                        }
                    }
                })
            })
        }

        scope.launch {
            try {

                val answer = callAI(postbox, appState.value.apiKey)
                if (answer != null) {
                    val cleanTitle = answer.replace("\"", "").trim()
                    val index = appState.value.chats.indexOfFirst { it.id == chat.id }

                    if (index != -1) {
                        val updatedChat = appState.value.chats[index].copy(title = cleanTitle)
                        val newChats = appState.value.chats.toMutableList()
                        newChats[index] = updatedChat

                        appState.value = appState.value.copy(chats = newChats)

                        Storage.save(appState.value)
                    }
                }
            } catch (_: Exception) {
                val index = appState.value.chats.indexOfFirst { it.id == chat.id }

                if (index != -1) {
                    val updatedChat = appState.value.chats[index].copy(title = "Error chat")
                    val newChats = appState.value.chats.toMutableList()
                    newChats[index] = updatedChat

                    appState.value = appState.value.copy(chats = newChats)

                    Storage.save(appState.value)
                }
            }
        }

    }

    fun sendMessage() {
        val text = inputText.value.text.trim()
        if (text.isEmpty() || isLoading.value) return

        var first = false

        if (currentChatId.value == null) {
            val newChat = Chat(UUID.randomUUID().toString(), "Wait...")
            first = true
            appState.value = appState.value.copy(
                chats = listOf(newChat) + appState.value.chats
            )

            currentChatId.value = newChat.id
            Storage.save(appState.value)
        }

        val chat = appState.value.chats.find { it.id == currentChatId.value } ?: return
        chat.messages += Message("user", text)
        inputText.value = TextFieldValue("")
//        isLoading.value = true
        appState.value = appState.value.copy()
        Storage.save(appState.value)

        val postbox = buildJsonObject {
            put("model", selectedModel.value)
            put("request", buildJsonObject {
                put("messages", jsonWorker.encodeToJsonElement(chat.messages))
            })
        }

        scope.launch {
            try {
                if (isStreaming.value) {
                    val assist = Message("assistant", "")
                    chat.messages += assist

                    callAIStream(postbox, appState.value.apiKey) { delta ->
                        val updatedChats = appState.value.chats.map { chat ->
                            if (chat.id == currentChatId.value) {
                                val newMessages = chat.messages.toMutableList()
                                val lastMsg = newMessages.last()

                                newMessages[newMessages.lastIndex] = lastMsg.copy(
                                    content = lastMsg.content + delta
                                )
                                chat.copy(messages = newMessages)
                            } else chat
                        }

                        appState.value = appState.value.copy(chats = updatedChats)
                    }
                } else {

                    isLoading.value = true
                    val answer = callAI(postbox, appState.value.apiKey)

                    if (answer != null && answer.isNotEmpty()) {
                        chat.messages += Message("assistant", answer)
                        appState.value = appState.value.copy()
                        Storage.save(appState.value)
                    }

                    isLoading.value = false
                    listState.animateScrollToItem(chat.messages.size - 1)

                }
                if (first) {
                    aiTitle()
                }

                appState.value = appState.value.copy()
                Storage.save(appState.value)

            } catch (exc: Exception) {
                isLoading.value = false
                val errorMsg = Message("error", exc.localizedMessage)
                val updatedChats = appState.value.chats.map { c ->
                    if (c.id == currentChatId.value) {
                        c.copy(messages = c.messages + errorMsg)
                    } else {
                        c
                    }
                }
                appState.value = appState.value.copy(chats = updatedChats)
                Storage.save(appState.value)
                scope.launch {
                    listState.animateScrollToItem(
                        appState.value.chats.find { it.id == currentChatId.value }?.messages?.lastIndex ?: 0
                    )
                }
            }
        }
    }

    AppTheme {
        BoxWithConstraints {
            val isNarrow = maxWidth < 650.dp

            Scaffold(
                topBar = {
                    if (isNarrow) {
                        CenterAlignedTopAppBar(
                            title = { Text("AI Chat") },
                            navigationIcon = {
                                IconButton(onClick = { showMobileMenu.value = true }) { Icon(Icons.Default.Menu, null) }
                            }
                        )
                    }
                }
            ) { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    if (!isNarrow) {
                        ChatSidebar(appState, currentChatId) { isSettingsOpen.value = true }
                    }

                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp)) {
                        ModelSelector(availableModels, selectedModel)

                        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(currentChat?.messages ?: emptyList()) { msg ->
                                val modelInfo = availableModels.find { it.first == selectedModel.value }
                                val displayName = modelInfo?.second ?: "Assistant"
                                ChatBubble(msg, displayName)
                            }

                            if (isLoading.value) {
                                item {
                                    LoadingBubble(selectedModel.value)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = inputText.value,
                            onValueChange = { inputText.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .heightIn(min = 56.dp, max = 200.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                        if (event.isShiftPressed) {
                                            val textFieldValue = inputText.value
                                            val text = textFieldValue.text
                                            val selection = textFieldValue.selection

                                            val newText = text.replaceRange(
                                                selection.start,
                                                selection.end,
                                                "\n"
                                            )
                                            val newCursorPosition = selection.start + 1

                                            inputText.value = TextFieldValue(
                                                text = newText,
                                                selection = TextRange(newCursorPosition)
                                            )
                                            true
                                        } else {
                                            sendMessage()
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                },
                            placeholder = { Text("Enter message...") },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
//                                        .fillMaxHeight()
                                        .padding(bottom = 4.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    IconButton(onClick = ::sendMessage) {
                                        Icon(Icons.AutoMirrored.Filled.Send, null)
                                    }
                                }
                            },
                            singleLine = false,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (isSettingsOpen.value) {
                SettingsDialog(appState) { isSettingsOpen.value = false }
            }

            if (isNarrow && showMobileMenu.value) {
                Dialog(onDismissRequest = { showMobileMenu.value = false }) {
                    Surface(Modifier.fillMaxSize()) {
                        ChatSidebar(appState, currentChatId) {
                            isSettingsOpen.value = true
                            showMobileMenu.value = false
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatBubble(msg: Message, modelName: String) {
    val isUser = msg.role == "user"
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (msg.role == "assistant") {
            Text(
                modelName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.95f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {

            Surface(
                color = when (msg.role) {
                    "error" -> Color(0xFFaa0000)
                    "user" -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(if (isUser) 16.dp else 8.dp),
                modifier = Modifier.weight(1f, fill = false).widthIn(max = if (isUser) 500.dp else 800.dp)
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Box(modifier = Modifier.padding(if (msg.role != "assistant") 12.dp else 0.dp)) {
                        Markdown(msg.content)
                    }
                }
            }

        }
        Box(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .padding(top = 4.dp, start = 4.dp, end = 4.dp)
        ) {
            TextButton(
                onClick = { clipboardManager.setText(AnnotatedString(msg.content)) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
//                Icon(
//                    imageVector = Icons.Default.Share,
//                    contentDescription = "Copy",
//                    modifier = Modifier.size(14.dp)
//                )
                Spacer(Modifier.width(6.dp))
                Text("Copy", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ModelSelector(models: List<Triple<String, String, String>>, selected: MutableState<String>) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val currentModel = models.find { it.first == selected.value }
    val currentName = currentModel?.second ?: selected.value

    val filteredModels = models.filter {
        it.second.contains(searchQuery, ignoreCase = true) ||
                it.third.contains(searchQuery, ignoreCase = true)
    }

    Box(Modifier.padding(top = 8.dp)) {
        AssistChip(
            onClick = {
                searchQuery = ""
                expanded = true
            },
            modifier = Modifier.height(56.dp),
            label = {
                Column {
                    Text(currentName, style = MaterialTheme.typography.labelMedium)
                    if (currentModel != null) {
                        Text(currentModel.third, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            shape = RoundedCornerShape(12.dp)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = 0.dp, y = 4.dp),
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = 500.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text("Searching model...") },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (filteredModels.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Nothing found", color = Color.Gray) },
                    onClick = { },
                    enabled = false
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredModels.forEach { (id, name, owner) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        owner,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                selected.value = id
                                expanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatSidebar(state: MutableState<AppState>, currentId: MutableState<String?>, onSettings: () -> Unit) {
    Column(Modifier.width(300.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Button(onClick = {
            val new = Chat(UUID.randomUUID().toString(), "New chat")
            state.value = state.value.copy(
                chats = listOf(new) + state.value.chats
            )

            currentId.value = new.id
            Storage.save(state.value)
        }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("+ New chat") }

        LazyColumn(Modifier.weight(1f).padding(vertical = 12.dp)) {
            items(state.value.chats) { chat ->
                var isHovered by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier.height(60.dp).fillMaxWidth()
                        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { isHovered = false },
                    contentAlignment = Alignment.CenterStart
                ) {
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(chat.title, maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(
                                    text = formatTimestamp(chat.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        },
                        selected = chat.id == currentId.value,
                        onClick = { currentId.value = chat.id },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isHovered) {
                        IconButton(
                            onClick = {
                                val updatedChats = state.value.chats.filter { it.id != chat.id }
                                state.value = state.value.copy(chats = updatedChats)
                                if (currentId.value == chat.id) {
                                    currentId.value = updatedChats.firstOrNull()?.id
                                }
                                Storage.save(state.value)
                            },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = onSettings, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text("Options")
        }
    }
}

@Composable
fun SettingsDialog(state: MutableState<AppState>, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Card(Modifier.width(400.dp).clickable(enabled = false) {}) {
            Column(Modifier.padding(24.dp)) {
                Text("Options", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.value.apiKey,
                    onValueChange = {
                        state.value = state.value.copy(apiKey = it)
                        Storage.save(state.value)
                    },
                    label = { Text("OnlySq API Key") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
                TextButton(
                    onClick = { openWebpage("https://my.onlysq.ru") },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "My OnlySq",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Streaming", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Stream AI reply to chat",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = state.value.isStreaming,
                        onCheckedChange = {
                            state.value = state.value.copy(isStreaming = it)
                            Storage.save(state.value)
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose, Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

@Composable
fun LoadingBubble(modelName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "$modelName thinking...",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LinearProgressIndicator(
            modifier = Modifier
                .width(150.dp)
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "OnlySq AI Chat",
        state = rememberWindowState(width = 1000.dp, height = 600.dp),
        icon = painterResource("icon.png")
    ) {
        MainScreen()
    }
}