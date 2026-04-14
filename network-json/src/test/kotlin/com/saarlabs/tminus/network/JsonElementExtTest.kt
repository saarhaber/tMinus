package com.saarlabs.tminus.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Specification for JSON:API parsing helpers.
 *
 * JSON `null` deserializes as [JsonNull]. It is a non-null [kotlinx.serialization.json.JsonElement],
 * so optional chaining on a [JsonObject] get does **not** skip it. Unsafe casts such as
 * [kotlinx.serialization.json.JsonElement.jsonObject] throw when the element is [JsonNull] (the
 * runtime error often mentions JsonNull vs JsonObject). Helpers [asJsonObjectOrNull],
 * [asJsonArrayOrNull], and [asJsonPrimitiveOrNull] must be used instead.
 */
class JsonElementExtTest {

    @Test
    fun `jsonObject on JsonNull throws even when reached through optional chain on map get`() {
        val parentRel =
            buildJsonObject {
                put(
                    "parent_station",
                    buildJsonObject {
                        put("data", JsonNull)
                    },
                )
            }
        val dataElement = parentRel["parent_station"]?.jsonObject?.get("data")
        assertEquals(JsonNull, dataElement)
        val failure = runCatching { dataElement?.jsonObject }.exceptionOrNull()
        assertNotNull(
            failure,
            "JSON:API empty to-one links use JSON null; jsonObject cast must not be used on those values",
        )
    }

    @Test
    fun `asJsonObjectOrNull returns null for JSON null where a nested object was expected`() {
        val rel =
            buildJsonObject {
                put("data", JsonNull)
            }
        assertNull(rel["data"]?.asJsonObjectOrNull())
    }

    @Test
    fun `asJsonPrimitiveOrNull maps JSON null to Kotlin null for optional attributes`() {
        val attrs =
            buildJsonObject {
                put("latitude", JsonNull)
                put("name", JsonPrimitive("Alewife"))
            }
        assertNull(attrs["latitude"]?.asJsonPrimitiveOrNull())
        assertEquals("Alewife", attrs["name"]?.asJsonPrimitiveOrNull()?.content)
    }

    @Test
    fun `jsonApiRelationshipDataId returns null when relationship data is JSON null`() {
        val resource =
            buildJsonObject {
                put(
                    "relationships",
                    buildJsonObject {
                        put(
                            "parent_station",
                            buildJsonObject {
                                put("data", JsonNull)
                            },
                        )
                    },
                )
            }
        assertNull(resource.jsonApiRelationshipDataId("parent_station"))
    }

    @Test
    fun `jsonApiRelationshipDataId returns id when relationship data is a resource identifier object`() {
        val resource =
            buildJsonObject {
                put(
                    "relationships",
                    buildJsonObject {
                        put(
                            "parent_station",
                            buildJsonObject {
                                put(
                                    "data",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("stop"))
                                        put("id", JsonPrimitive("place-alfcl"))
                                    },
                                )
                            },
                        )
                    },
                )
            }
        assertEquals("place-alfcl", resource.jsonApiRelationshipDataId("parent_station"))
    }
}
