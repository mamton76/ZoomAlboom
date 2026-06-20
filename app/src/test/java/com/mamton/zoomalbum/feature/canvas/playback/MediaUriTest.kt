package com.mamton.zoomalbum.feature.canvas.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `mediaRefId` → `Uri` conversion rule ([mediaRefToUri]) at the level
 * that prevents the original bug: a `content://` (or any already-formed URI)
 * string must NOT be wrapped in `Uri.fromFile(File(uriString))`, which corrupts
 * it into `file://content:/…`.
 *
 * The actual `Uri.parse` / `Uri.fromFile` calls need `android.net.Uri`, which is
 * not available in plain JVM unit tests (no Robolectric in this module). Those
 * are covered by the manual QA checklist (`todo.md § 27.10`). The decision that
 * drives the regression — "does this ref already carry a scheme?" — is the pure
 * [mediaRefHasScheme], exhaustively tested here:
 *  - scheme present → [mediaRefToUri] uses `Uri.parse` (URI preserved);
 *  - no scheme → it uses `Uri.fromFile`.
 */
class MediaUriTest {

    @Test
    fun `content uri has a scheme so it is parsed, not file-wrapped`() {
        assertTrue(mediaRefHasScheme("content://media/external/video/media/123"))
    }

    @Test
    fun `file uri has a scheme`() {
        assertTrue(mediaRefHasScheme("file:///storage/emulated/0/DCIM/clip.mp4"))
    }

    @Test
    fun `http and https urls have a scheme`() {
        assertTrue(mediaRefHasScheme("http://example.com/clip.mp4"))
        assertTrue(mediaRefHasScheme("https://example.com/clip.mp4"))
    }

    @Test
    fun `bare absolute filesystem path has no scheme (gets Uri-fromFile)`() {
        // The current import flow stores an app-storage absolute path; it must keep
        // routing through Uri.fromFile (preserves today's working behaviour).
        assertFalse(mediaRefHasScheme("/storage/emulated/0/DCIM/clip.mp4"))
        assertFalse(mediaRefHasScheme("/data/user/0/com.mamton.zoomalbum/files/media/1/media_123.mp4"))
    }

    @Test
    fun `relative path and empty string have no scheme`() {
        assertFalse(mediaRefHasScheme("media/1/clip.mp4"))
        assertFalse(mediaRefHasScheme(""))
    }

    @Test
    fun `scheme detection is anchored at the start (a colon mid-path is not a scheme)`() {
        // A path that merely contains a colon later isn't a scheme — still a path.
        assertFalse(mediaRefHasScheme("/storage/emulated/0/my:weird/clip.mp4"))
    }
}
