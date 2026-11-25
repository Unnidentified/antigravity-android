package com.antigravity.wrapper.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRouterTest {

    @Test
    fun routesAntigravityAndGoogleAuthDomainsInApp() {
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://antigravity.google.com"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://antigravity.google.com/chat/123"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://sub.antigravity.google.com/workspace"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://accounts.google.com/v3/signin"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://accounts.google.co.uk/ServiceLogin"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://accounts.youtube.com/accounts/SetSID"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://myaccount.google.com/security"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://gds.google.com/web/chip"))
        assertEquals(UrlRouter.Route.IN_APP, UrlRouter.route("https://oauth2.googleapis.com/token"))
    }

    @Test
    fun routesExternalDomainsToExternalBrowser() {
        assertEquals(UrlRouter.Route.EXTERNAL_BROWSER, UrlRouter.route("https://github.com/google/antigravity"))
        assertEquals(UrlRouter.Route.EXTERNAL_BROWSER, UrlRouter.route("https://developer.android.com"))
        assertEquals(UrlRouter.Route.EXTERNAL_BROWSER, UrlRouter.route("https://evil-antigravity.google.com.example.com"))
    }

    @Test
    fun routesNonHttpSchemesToSystemIntent() {
        assertEquals(UrlRouter.Route.SYSTEM_INTENT, UrlRouter.route("mailto:support@example.com"))
        assertEquals(UrlRouter.Route.SYSTEM_INTENT, UrlRouter.route("tel:+15551234567"))
        assertEquals(UrlRouter.Route.SYSTEM_INTENT, UrlRouter.route("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun validatesTrustedPermissionOrigins() {
        assertTrue(UrlRouter.isTrustedPermissionOrigin("https://antigravity.google.com"))
        assertTrue(UrlRouter.isTrustedPermissionOrigin("https://accounts.google.com"))
        assertFalse(UrlRouter.isTrustedPermissionOrigin("http://antigravity.google.com"))
        assertFalse(UrlRouter.isTrustedPermissionOrigin("https://example.com"))
    }
}
