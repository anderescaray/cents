package io.github.anderescaray.cents.data.export

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Reading a backup's rows: the field helpers [BackupReader] and the legacy portfolio share. */

internal fun <T> JsonObject.rows(
    name: String,
    row: (JsonObject) -> T,
): List<T> = (this[name] as? JsonArray).orEmpty().map { row(it as JsonObject) }

internal fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

/**
 * A field that has to be there.
 *
 * It throws, and `BackupReader.parse` turns that into [BackupParse.Damaged] naming the field. A restore
 * that silently substituted a zero for a missing amount would be the failure this whole
 * class exists to prevent — money that is quietly wrong is worse than a file that is
 * honestly refused.
 */
internal fun JsonObject.requireLong(name: String): Long =
    this[name]?.jsonPrimitive?.longOrNull ?: error("$name is missing or is not a number")

internal fun JsonObject.requireString(name: String): String = string(name) ?: error("$name is missing")

internal fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

/** Absent or null reads as none; anything else has to be a number, or the file is refused. */
internal fun JsonObject.optionalLong(name: String): Long? {
    val value = this[name]
    if (value == null || value is JsonNull) return null
    return value.jsonPrimitive.longOrNull ?: error("$name is not a number")
}

internal fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.takeUnless { it.toString() == "null" }?.content

internal fun JsonObject.boolean(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false
