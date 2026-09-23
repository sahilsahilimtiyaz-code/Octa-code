package com.sahil.octacode.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @Test
    fun `redacts bearer tokens`() {
        val out = SecretRedactor.redact("Authorization: Bearer sk-abc123XYZ789")
        assertFalse(out.contains("abc123XYZ789"))
        assertTrue(out.contains("Bearer ***"))
    }

    @Test
    fun `redacts bare sk- keys`() {
        val out = SecretRedactor.redact("key=sk-secret123456789")
        assertFalse(out.contains("secret123456789"))
        assertTrue(out.contains("sk-***"))
    }

    @Test
    fun `redacts x-api-key headers`() {
        val out = SecretRedactor.redact("x-api-key: hunter2token")
        assertFalse(out.contains("hunter2token"))
    }

    @Test
    fun `redacts github pat`() {
        val out = SecretRedactor.redact("token github_pat_11CMHNVMA0v3SHsH0O7TKK")
        assertFalse(out.contains("11CMHNVMA0"))
        assertTrue(out.contains("github_pat_***"))
    }

    @Test
    fun `redacts api_key json fields`() {
        val out = SecretRedactor.redact("""{"api_key":"sk-json-secret-999"}""")
        assertFalse(out.contains("sk-json-secret-999"))
    }

    @Test
    fun `clean text passes through unchanged`() {
        val clean = "Phase 7 implement complete in 1.2s"
        assertEquals(clean, SecretRedactor.redact(clean))
    }
}
