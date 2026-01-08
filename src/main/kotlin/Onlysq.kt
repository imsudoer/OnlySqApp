import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.collections.component1
import kotlin.collections.component2

val client = HttpClient(CIO) {
    install(ContentNegotiation) { json(jsonWorker) }
}

suspend fun callAI(postbox: JsonObject, apikey: String): String? {
    return try {
        val response = client.post("$baseUrl/ai/v2") {
            header("Authorization", "Bearer $apikey")
            contentType(ContentType.Application.Json)
            setBody(postbox)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.bodyAsText()
                val answer = jsonWorker.parseToJsonElement(body).jsonObject["choices"]
                    ?.jsonArray?.get(0)?.jsonObject?.get("message")
                    ?.jsonObject?.get("content")?.jsonPrimitive?.content

                answer
            }

            HttpStatusCode.Unauthorized -> {
                val body = response.bodyAsText()
                val error = jsonWorker.parseToJsonElement(body).jsonObject["error"]?.jsonObject["message"]?.toString()
                throw IllegalStateException(error)
            }

            else -> {
                null
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.localizedMessage}")
        null
    }
}

suspend fun callAIStream(postbox: JsonObject, apikey: String, onDelta: (String) -> Unit) {
    try {
        client.preparePost("$baseUrl/ai/v2") {
            header("Authorization", "Bearer $apikey")
            contentType(ContentType.Application.Json)

            val streamPostbox = buildJsonObject {
                postbox.forEach { (k, v) ->
                    if (k == "request") {
                        put("request", buildJsonObject {
                            v.jsonObject.forEach { (rk, rv) -> put(rk, rv) }
                            put("stream", true)
                        })
                    } else {
                        put(k, v)
                    }
                }
            }
            setBody(streamPostbox)
        }.execute { response ->
            when (response.status) {
                HttpStatusCode.OK -> {
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val jsonStr = line.substring(6).trim()
                            if (jsonStr == "[DONE]") break
                            try {
                                val content = jsonWorker.parseToJsonElement(jsonStr)
                                    .jsonObject["choices"]?.jsonArray?.get(0)
                                    ?.jsonObject?.get("delta")?.jsonObject?.get("content")
                                    ?.jsonPrimitive?.content ?: ""
                                if (content.isNotEmpty()) onDelta(content)
                            } catch (e: Exception) {
                                println(e.localizedMessage)
                            }
                        }
                    }
                }

                HttpStatusCode.Unauthorized -> {
                    val body = response.bodyAsText()
                    val errorMessage = try {
                        jsonWorker.parseToJsonElement(body)
                            .jsonObject["error"]
                            ?.jsonObject?.get("message")
                            ?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        throw IllegalStateException("Unauthorized: Invalid API Key")
                    }

                    if (errorMessage != null) {
                        throw IllegalStateException("Error: $errorMessage")
                    }
                }

                else -> {
                    throw IllegalStateException("Server error: ${response.status.value}")
                }
            }
        }
    } catch (e: Exception) {
        println("Stream error: ${e.localizedMessage}")
        throw IllegalStateException("Connection error: ${e.localizedMessage}")
    }
}