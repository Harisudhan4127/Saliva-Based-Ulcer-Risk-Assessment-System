package com.harisudhan.suras.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseParserTest {

    private val client = GitHubReleaseClient()

    @Test
    fun parsesVersionDigestAndHttpsUrl() {
        val body = """
            {
              "tag_name": "v2.2.0",
              "body": "Fixes",
              "html_url": "https://github.com/example/suras/releases/tag/v2.2.0",
              "assets": [
                {
                  "name": "suras-2.2.0-universal.apk",
                  "browser_download_url": "https://github.com/example/suras/releases/download/v2.2.0/suras-2.2.0-universal.apk",
                  "digest": "sha256:884da6e01e442119a969094746fa2bbf484a393fe511685c91e3617acc65f78a"
                }
              ]
            }
        """.trimIndent()

        val release = client.parseRelease(body)
        assertEquals("2.2.0", release.version)
        assertEquals("v2.2.0", release.tagName)
        assertEquals(
            "884da6e01e442119a969094746fa2bbf484a393fe511685c91e3617acc65f78a",
            release.apk?.sha256,
        )
        assertEquals("suras-2.2.0-universal.apk", release.apk?.name)
    }

    @Test
    fun assetWithoutDigestKeepsNullAndInstallerRefusesLater() {
        val body = """
            {
              "tag_name": "2.2.0",
              "assets": [
                {
                  "name": "suras.apk",
                  "browser_download_url": "https://example.com/suras.apk"
                }
              ]
            }
        """.trimIndent()

        val release = client.parseRelease(body)
        assertNull(release.apk?.sha256)
    }

    @Test
    fun httpAssetIsIgnored() {
        val body = """
            {
              "tag_name": "2.2.0",
              "assets": [
                {
                  "name": "suras.apk",
                  "browser_download_url": "http://example.com/suras.apk",
                  "digest": "sha256:884da6e01e442119a969094746fa2bbf484a393fe511685c91e3617acc65f78a"
                }
              ]
            }
        """.trimIndent()

        assertNull(client.parseRelease(body).apk)
    }

    @Test
    fun nonSemverTagIsRejected() {
        val body = """{"tag_name":"nightly","assets":[]}"""
        assertThrows(UpdateException::class.java) { client.parseRelease(body) }
    }

    @Test
    fun malformedDigestIsNotTrusted() {
        val body = """
            {
              "tag_name": "2.2.0",
              "assets": [
                {
                  "name": "suras.apk",
                  "browser_download_url": "https://example.com/suras.apk",
                  "digest": "sha256:not-a-real-digest"
                }
              ]
            }
        """.trimIndent()

        assertNull(client.parseRelease(body).apk?.sha256)
    }
}
