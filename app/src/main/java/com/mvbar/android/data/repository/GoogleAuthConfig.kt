package com.mvbar.android.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal fun parseGoogleAuthConfig(body: String): AuthRepository.GoogleAuthInfo {
    val config = Json.parseToJsonElement(body) as? JsonObject
        ?: return AuthRepository.GoogleAuthInfo(false, null)
    val enabled = config["enabled"] as? JsonPrimitive
    val clientId = config["clientId"] as? JsonPrimitive
    return AuthRepository.GoogleAuthInfo(
        enabled = enabled?.isString == false && enabled.booleanOrNull == true,
        clientId = clientId?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    )
}
