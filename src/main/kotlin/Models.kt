import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class Chat(
    val id: String,
    var title: String,
    var messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppState(
    var apiKey: String = "",
    val chats: List<Chat> = emptyList(),
    val isStreaming: Boolean = true
)

val jsonWorker = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    isLenient = true
}

object Storage {
    private val file = File(System.getProperty("java.io.tmpdir"), "onlysqai.json")

    fun save(state: AppState) {
        try {
            val json = jsonWorker.encodeToString(state)
            file.writeText(json)
        } catch (e: Exception) {
            println("Ошибка сохранения: ${e.message}")
        }
    }

    fun load(): AppState {
        return try {
            if (file.exists()) {
                jsonWorker.decodeFromString<AppState>(file.readText())
            } else AppState()
        } catch (e: Exception) {
            AppState()
        }
    }
}