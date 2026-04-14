package com.saarlabs.tminus.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * JSON:API uses JSON `null` for empty to-one links. That deserializes as [JsonNull], not Kotlin
 * `null`, so `element?.jsonObject` still calls [JsonElement.jsonObject] and throws when the link is
 * empty.
 */
public fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
    when (this) {
        null, JsonNull -> null
        is JsonObject -> this
        else -> null
    }

public fun JsonElement?.asJsonArrayOrNull(): JsonArray? =
    when (this) {
        null, JsonNull -> null
        is JsonArray -> this
        else -> null
    }

/**
 * Treats JSON `null` as absent for optional primitive attributes (e.g. latitude). [JsonNull] is a
 * [JsonPrimitive] at runtime; mapping it to Kotlin `null` avoids propagating sentinel nulls into
 * domain parsing.
 */
public fun JsonElement?.asJsonPrimitiveOrNull(): JsonPrimitive? =
    when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> this
        else -> null
    }

/** Primary `data` array from a JSON:API document (empty if missing or not an array). */
public fun JsonObject.dataArrayElements(): List<JsonElement> =
    get("data")?.asJsonArrayOrNull()?.toList() ?: emptyList()

/** `included` array from a JSON:API document. */
public fun JsonObject.includedArrayElements(): List<JsonElement> =
    get("included")?.asJsonArrayOrNull()?.toList() ?: emptyList()

/**
 * Reads `relationships[name].data.id` for a to-one link. Returns null if the link is missing or
 * JSON `null` (empty link).
 */
public fun JsonObject.jsonApiRelationshipDataId(relationshipName: String): String? =
    get("relationships")
        ?.asJsonObjectOrNull()
        ?.get(relationshipName)
        ?.asJsonObjectOrNull()
        ?.get("data")
        ?.asJsonObjectOrNull()
        ?.get("id")
        ?.asJsonPrimitiveOrNull()
        ?.content
