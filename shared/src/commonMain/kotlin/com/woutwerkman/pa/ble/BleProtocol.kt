package com.woutwerkman.pa.ble

import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val bleJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
sealed interface BleMessage {
    @Serializable
    data class FullSync(val state: PresentationState) : BleMessage

    @Serializable
    data class Event(val event: PresentationEvent) : BleMessage

    @Serializable
    data object SyncRequest : BleMessage
}

fun BleMessage.encode(): ByteArray =
    bleJson.encodeToString(BleMessage.serializer(), this).encodeToByteArray()

fun decodeBleMessage(bytes: ByteArray): BleMessage =
    bleJson.decodeFromString(BleMessage.serializer(), bytes.decodeToString())

fun PresentationState.forBleSync(): PresentationState =
    if (runs.isEmpty()) this else copy(runs = emptyList())

private const val CHUNK_COMPLETE: Byte = 0x00
private const val CHUNK_START: Byte = 0x01
private const val CHUNK_CONTINUE: Byte = 0x02
private const val CHUNK_END: Byte = 0x03
private const val MAX_CHUNK_PAYLOAD = 499

fun BleMessage.encodeChunked(): List<ByteArray> {
    val data = encode()
    if (data.size <= MAX_CHUNK_PAYLOAD) {
        return listOf(byteArrayOf(CHUNK_COMPLETE) + data)
    }
    val chunks = mutableListOf<ByteArray>()
    var offset = 0
    while (offset < data.size) {
        val end = minOf(offset + MAX_CHUNK_PAYLOAD, data.size)
        val chunkData = data.copyOfRange(offset, end)
        val flag = when {
            offset == 0 -> CHUNK_START
            end == data.size -> CHUNK_END
            else -> CHUNK_CONTINUE
        }
        chunks.add(byteArrayOf(flag) + chunkData)
        offset = end
    }
    return chunks
}

class MessageAssembler {
    private var buffer = ByteArray(0)

    fun processChunk(chunk: ByteArray): BleMessage? {
        if (chunk.isEmpty()) return null
        val flag = chunk[0]
        val data = if (chunk.size > 1) chunk.copyOfRange(1, chunk.size) else ByteArray(0)
        return when (flag) {
            CHUNK_COMPLETE -> {
                buffer = ByteArray(0)
                decodeBleMessage(data)
            }
            CHUNK_START -> {
                buffer = data
                null
            }
            CHUNK_CONTINUE -> {
                buffer += data
                null
            }
            CHUNK_END -> {
                val assembled = buffer + data
                buffer = ByteArray(0)
                decodeBleMessage(assembled)
            }
            else -> {
                // Legacy: raw JSON without chunk header (starts with '{' = 0x7B)
                try { decodeBleMessage(chunk) } catch (_: Exception) { null }
            }
        }
    }
}
