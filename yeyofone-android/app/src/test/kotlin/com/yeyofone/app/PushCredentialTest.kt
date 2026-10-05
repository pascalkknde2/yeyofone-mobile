package com.yeyofone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushCredentialTest {
    private val token = "sk_0123456789abcdefghijklmnop"

    private fun json(
        role: String = "device",
        account: String = "1005",
        device: String = "phone-1005",
        token: String = this.token,
    ) = """{"id":"cred-1","token":"$token","role":"$role","tenant":"pbx-main","account":"$account","device":"$device"}"""

    @Test
    fun `relay CLI device credential for the same account is accepted`() {
        val credential = PushCredential.parse(json(), "1005")
        assertEquals("phone-1005", credential.device)
        assertEquals("pbx-main", credential.tenant)
        assertEquals(token, credential.token)
    }

    @Test
    fun `credential for another account or role is rejected`() {
        assertFailsWith<InvalidPushCredentialException> { PushCredential.parse(json(account = "1006"), "1005") }
        assertFailsWith<InvalidPushCredentialException> { PushCredential.parse(json(role = "pbx"), "1005") }
    }

    @Test
    fun `malformed credentials are rejected`() {
        assertFailsWith<InvalidPushCredentialException> { PushCredential.parse("not json", "1005") }
        assertFailsWith<InvalidPushCredentialException> { PushCredential.parse(json(token = "short"), "1005") }
        assertFailsWith<InvalidPushCredentialException> { PushCredential.parse(json(device = "bad device/id"), "1005") }
    }

    @Test
    fun `string form and errors never contain the bearer token`() {
        assertFalse(token in PushCredential.parse(json(), "1005").toString())
        val failure = assertFailsWith<InvalidPushCredentialException> { PushCredential.parse(json(account = "1006"), "1005") }
        assertFalse(token in failure.message.orEmpty())
    }

    @Test
    fun `relay responses map to client statuses`() {
        assertEquals(PushRelayClient.Status.REGISTERED, statusFor(204))
        assertEquals(PushRelayClient.Status.CREDENTIAL_REJECTED, statusFor(401))
        assertEquals(PushRelayClient.Status.SCOPE_REJECTED, statusFor(403))
        assertEquals(PushRelayClient.Status.FAILED, statusFor(429))
        assertEquals(PushRelayClient.Status.FAILED, statusFor(503))
    }

    @Test
    fun `device endpoint requires HTTPS`() {
        assertEquals(
            "https://relay.example.com/v1/devices/phone-1005",
            deviceEndpoint("https://relay.example.com/", "phone-1005").toString(),
        )
        assertTrue(deviceEndpoint("https://relay.example.com/base", "a@b+c").toString().endsWith("/base/v1/devices/a%40b%2Bc"))
        assertNull(deviceEndpoint("http://relay.example.com", "phone-1005"))
        assertNull(deviceEndpoint("", "phone-1005"))
    }
}
