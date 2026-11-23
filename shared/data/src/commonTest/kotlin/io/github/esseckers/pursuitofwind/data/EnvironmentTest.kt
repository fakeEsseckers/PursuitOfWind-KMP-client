package io.github.esseckers.pursuitofwind.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** One build property moves the app from its in-app fake backend to a server. */
class EnvironmentTest {
    @Test
    fun `a build without a server address runs on the fake backend`() {
        assertEquals(Environment.Fake, Environment.of(null))
        assertEquals(Environment.Fake, Environment.of(""))
        assertEquals(Environment.Fake, Environment.of("  "))
    }

    @Test
    fun `a build with a server address talks to that server`() {
        assertEquals(Environment.Remote("https://api.example.com"), Environment.of("https://api.example.com"))
        assertEquals(Environment.Remote("http://10.0.2.2:8080"), Environment.of(" http://10.0.2.2:8080 "))
    }

    @Test
    fun `the server address is an origin and nothing more`() {
        val notOrigins =
            listOf("api.example.com", "https://", "https://api.example.com/", "https://api.example.com/v1", "ftp://api.example.com")

        notOrigins.forEach { assertFailsWith<IllegalArgumentException>(it) { Environment.of(it) } }
    }

    @Test
    fun `a server keeps its data apart from the fake backend's`() {
        assertNotEquals(Environment.Fake.storageName, Environment.Remote("https://api.example.com").storageName)
    }
}
