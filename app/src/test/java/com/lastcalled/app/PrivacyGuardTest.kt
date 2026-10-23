package com.lastcalled.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static guards for the privacy requirements: no network permission, no networking code and no
 * logging calls in production sources. Runs with the normal unit tests.
 */
class PrivacyGuardTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val productionSources: List<File> = listOf("app/src/main", "domain/src/main")
        .flatMap { File(root, it).walkTopDown().filter { f -> f.extension in setOf("kt", "java") }.toList() }

    @Test fun `manifest does not request INTERNET or other unneeded permissions`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val requested = Regex("""uses-permission\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertTrue(requested == setOf("android.permission.READ_CALL_LOG", "android.permission.READ_CONTACTS"))
        assertFalse(manifest.contains("android.permission.INTERNET"))
    }

    @Test fun `no networking APIs in production code`() {
        val banned = listOf(
            "java.net.", "javax.net.", "HttpURLConnection", "okhttp", "retrofit", "ktor", "Socket(",
            "firebase", "crashlytics", "analytics", "WebView",
        )
        val hits = productionSources.flatMap { f ->
            val text = f.readText()
            banned.filter { text.contains(it, ignoreCase = true) }.map { "${f.name}: $it" }
        }
        assertTrue("Network-related code found: $hits", hits.isEmpty())
    }

    @Test fun `no logging calls in production code`() {
        val logging = Regex("""\b(Log\.[vdiwe]|println|printStackTrace|System\.out|System\.err|Timber)\b""")
        val hits = productionSources.filter { logging.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("Logging found in: $hits", hits.isEmpty())
    }

    @Test fun `no dependencies that phone home`() {
        val deps = File(root, "gradle/libs.versions.toml").readText() + File(root, "app/build.gradle.kts").readText()
        listOf("firebase", "crashlytics", "sentry", "analytics", "okhttp", "retrofit", "play-services").forEach {
            assertFalse("Dependency contains $it", deps.contains(it, ignoreCase = true))
        }
    }
}
