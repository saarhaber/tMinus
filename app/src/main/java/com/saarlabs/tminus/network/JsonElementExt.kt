package com.saarlabs.tminus.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON:API uses JSON `null` for empty to-one links. That deserializes as [JsonNull], not Kotlin
 * `null`, so `element?.jsonObject` still calls [JsonElement.jsonObject] and throws.
 */
internal fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
    when (this) {
        null, JsonNull -> null
        is JsonObject -> this
        else -> null
    }

internal fun JsonElement?.asJsonArrayOrNull(): JsonArray? =
    when (this) {
        null, JsonNull -> null
        is JsonArray -> this
        else -> null
    }

/** Primary `data` array from a JSON:API document (empty if missing or not an array). */
internal fun JsonObject.dataArrayElements(): List<JsonElement> =
    get("data")?.asJsonArrayOrNull()?.toList() ?: emptyList()

/** `included` array from a JSON:API document. */
internal fun JsonObject.includedArrayElements(): List<JsonElement> =
    get("included")?.asJsonArrayOrNull()?.toList() ?: emptyList()

/**
 * Reads `relationships[name].data.id` for a to-one link. Returns null if the link is missing or
 * JSON `null` (empty link).
 */
internal fun JsonObject.jsonApiRelationshipDataId(relationshipName: String): String? =
    get("relationships")
        ?.asJsonObjectOrNull()
        ?.get(relationshipName)
        ?.asJsonObjectOrNull()
        ?.get("data")
        ?.asJsonObjectOrNull()
        ?.get("id")
        ?.jsonPrimitive
        ?.content
