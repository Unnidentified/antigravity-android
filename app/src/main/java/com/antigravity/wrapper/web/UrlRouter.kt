package com.antigravity.wrapper.web

import java.net.URI
import java.util.Locale

/**
 * Determines how URLs requested by the WebView should be routed.
 */
object UrlRouter {

    enum class Route {
        IN_APP,
        EXTERNAL_BROWSER,
        SYSTEM_INTENT
    }

    const val START_URL = "https://antigravity.google.com"

    private val IN_APP_EXACT_HOSTS = setOf(
        "antigravity.google.com",
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "signin.google.com",
        "consent.google.com",
        "gds.google.com",
        "ogs.google.com",
        "oauth2.googleapis.com",
        "sts.googleapis.com",
        "families.google.com",
        "passwords.google.com"
    )

    private val IN_APP_SUFFIX_HOSTS = listOf(
        ".antigravity.google.com"
    )

    // Regional Google auth domains such as accounts.google.co.uk or accounts.google.de
    private val REGIONAL_GOOGLE_ACCOUNTS_REGEX = Regex("^accounts\\.google\\.[a-z]{2,3}(?:\\.[a-z]{2})?$")

    fun route(urlString: String?): Route {
        if (urlString.isNullOrBlank()) {
            return Route.IN_APP
        }

        val trimmed = urlString.trim()
        if (trimmed.equals("about:blank", ignoreCase = true)) {
            return Route.IN_APP
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return Route.SYSTEM_INTENT
        }

        val scheme = uri.scheme?.lowercase(Locale.US) ?: return Route.SYSTEM_INTENT
        if (scheme != "http" && scheme != "https") {
            return Route.SYSTEM_INTENT
        }

        val host = uri.host?.lowercase(Locale.US) ?: return Route.EXTERNAL_BROWSER
        return if (isInAppHost(host)) {
            Route.IN_APP
        } else {
            Route.EXTERNAL_BROWSER
        }
    }

    fun isInAppHost(rawHost: String?): Boolean {
        val host = rawHost?.lowercase(Locale.US)?.trimEnd('.') ?: return false
        if (host in IN_APP_EXACT_HOSTS) {
            return true
        }
        if (IN_APP_SUFFIX_HOSTS.any { host.endsWith(it) }) {
            return true
        }
        return REGIONAL_GOOGLE_ACCOUNTS_REGEX.matches(host)
    }

    fun isTrustedPermissionOrigin(originString: String?): Boolean {
        if (originString.isNullOrBlank()) return false
        val uri = try {
            URI(originString)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        if (scheme != "https") return false
        return isInAppHost(uri.host)
    }
}
