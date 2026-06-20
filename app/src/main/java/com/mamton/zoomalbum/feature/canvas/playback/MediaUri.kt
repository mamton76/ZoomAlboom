package com.mamton.zoomalbum.feature.canvas.playback

import android.net.Uri
import java.io.File

/**
 * Converts a `CanvasNode.Media.mediaRefId` into a [Uri] for playback / loading.
 *
 * `mediaRefId` is "a raw URI string" per the data model, but in practice it can
 * be any of: a `content://` URI from the Android media picker, a `file://` URI,
 * an `http(s)://` URL, or a bare filesystem path (today the import flow copies
 * picked media into app storage and stores its absolute path).
 *
 * Rule — decided by the pure [mediaRefHasScheme]:
 * - **Has a URI scheme** (`content:`, `file:`, `http:`, …) → [Uri.parse], so the
 *   original URI is preserved untouched. Wrapping a `content://` string in
 *   `Uri.fromFile(File(...))` would corrupt it into `file://content:/...` — the
 *   bug this helper exists to prevent.
 * - **Bare path** (no scheme, e.g. `/storage/emulated/0/clip.mp4`) → [Uri.fromFile],
 *   which percent-encodes path characters and yields a valid `file://` URI. This
 *   preserves the behaviour of the current import flow (absolute app-storage path).
 *
 * The framework `Uri.parse` / `Uri.fromFile` calls aren't exercised by JVM unit
 * tests (android.net.Uri isn't available without Robolectric, which we don't pull
 * in for one helper). The decision logic — [mediaRefHasScheme] — is pure and
 * unit-tested; the full `Uri` round-trip is covered by the manual QA checklist
 * (`todo.md § 27.10`).
 */
fun mediaRefToUri(mediaRefId: String): Uri =
    if (mediaRefHasScheme(mediaRefId)) Uri.parse(mediaRefId) else Uri.fromFile(File(mediaRefId))

/**
 * True iff [mediaRefId] begins with a URI scheme (`scheme:`), per RFC 3986:
 * `ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )` followed by `:`. A bare absolute
 * path like `/storage/…/clip.mp4` has no scheme and returns false. Pure (no
 * Android framework) so it is directly unit-testable.
 */
internal fun mediaRefHasScheme(mediaRefId: String): Boolean =
    URI_SCHEME_REGEX.containsMatchIn(mediaRefId)

private val URI_SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:")
