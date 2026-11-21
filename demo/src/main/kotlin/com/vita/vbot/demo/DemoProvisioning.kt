package com.vita.vbot.demo

import android.content.ContentResolver
import android.net.Uri
import com.vita.vbot.sdk.VbotAortaOptions
import com.vita.vbot.sdk.VbotDevice
import com.vita.vbot.sdk.VbotTransport
import org.json.JSONObject

/** Optional implementation on a test host's Application, outside the SDK. */
interface DemoProvisioning {
    val suggestedSerial: String
    suspend fun authorize(device: VbotDevice, transport: VbotTransport): Boolean
    fun aortaOptions(device: VbotDevice): VbotAortaOptions?
}

internal class ImportedProvisioning private constructor(
    override val suggestedSerial: String,
    private val operatorId: String,
    private val zenohConfig: String,
) : DemoProvisioning {
    override suspend fun authorize(device: VbotDevice, transport: VbotTransport) =
        device.serialNumber == suggestedSerial && transport == VbotTransport.AORTA

    override fun aortaOptions(device: VbotDevice): VbotAortaOptions? =
        if (device.serialNumber == suggestedSerial) VbotAortaOptions(operatorId) { zenohConfig } else null

    companion object {
        fun read(resolver: ContentResolver, uri: Uri): ImportedProvisioning {
            val document = requireNotNull(resolver.openInputStream(uri)).bufferedReader().use { reader ->
                val buffer = CharArray(64 * 1024 + 1)
                var size = 0
                while (size < buffer.size) {
                    val count = reader.read(buffer, size, buffer.size - size)
                    if (count < 0) break
                    size += count
                }
                require(size in 1 until buffer.size)
                JSONObject(String(buffer, 0, size))
            }
            val serial = VbotDevice(document.getString("serialNumber")).serialNumber
            val operator = document.getString("operatorId")
            require(operator.isNotBlank() && operator.encodeToByteArray().size <= 255)
            val config = when (val value = document.get("zenohConfig")) {
                is String -> value
                is JSONObject -> value.toString()
                else -> error("Expected a Zenoh configuration object or JSON5 string")
            }
            require(config.isNotBlank())
            return ImportedProvisioning(serial, operator, config)
        }
    }
}
