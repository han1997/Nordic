package com.nordic.mediahub.data

import okhttp3.HttpUrl
import java.security.MessageDigest
import kotlin.random.Random

internal const val NAVIDROME_API_VERSION = "1.16.1"
internal const val NAVIDROME_CLIENT_NAME = "Nordic"

private const val AUTH_SALT_LENGTH = 12
private const val AUTH_SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

internal data class NavidromeAuthParams(
    val token: String,
    val salt: String
)

internal fun normalizeNavidromeBaseUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""

    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

internal fun NavidromeConfig.normalizedBaseUrl(): String {
    return normalizeNavidromeBaseUrl(serverUrl)
}

internal fun NavidromeConfig.authParams(): NavidromeAuthParams {
    val salt = buildString(AUTH_SALT_LENGTH) {
        repeat(AUTH_SALT_LENGTH) {
            append(AUTH_SALT_CHARS[Random.nextInt(AUTH_SALT_CHARS.length)])
        }
    }
    return NavidromeAuthParams(
        token = md5("$password$salt"),
        salt = salt
    )
}

internal fun HttpUrl.Builder.addNavidromeAuth(
    config: NavidromeConfig,
    auth: NavidromeAuthParams = config.authParams()
): HttpUrl.Builder {
    return addQueryParameter("u", config.username)
        .addQueryParameter("t", auth.token)
        .addQueryParameter("s", auth.salt)
        .addQueryParameter("v", NAVIDROME_API_VERSION)
        .addQueryParameter("c", NAVIDROME_CLIENT_NAME)
}

private fun md5(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
