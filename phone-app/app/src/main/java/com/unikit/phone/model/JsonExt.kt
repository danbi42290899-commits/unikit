package com.unikit.phone.model

import org.json.JSONObject

/**
 * org.json represents an explicit JSON `null` as the JSONObject.NULL
 * sentinel, not absence of the key -- these helpers collapse both "key
 * missing" and "key explicitly null" into a Kotlin `null`, which is what
 * every nullable field in the UNI-HUB data contract needs (see
 * docs/data_contracts.md). Ported verbatim from glass-app/model/JsonExt.kt.
 */

fun JSONObject.optNullableString(key: String): String? {
    if (isNull(key)) return null
    return if (has(key)) optString(key) else null
}

fun JSONObject.optNullableDouble(key: String): Double? {
    if (isNull(key) || !has(key)) return null
    return optDouble(key).takeUnless { it.isNaN() }
}

fun JSONObject.optNullableInt(key: String): Int? {
    if (isNull(key) || !has(key)) return null
    return optInt(key)
}
