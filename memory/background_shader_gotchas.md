# Background texture & shader gotchas

Three non-obvious failure modes hit during §19 background-texture work. Each one rendered the texture as silently-transparent (= `CanvasDark` showing through, looking like "a black rectangle"). Read this before touching `AlbumBackgroundRenderer.kt` or any other code that puts a `BitmapShader` into a Compose draw.

## 1. Coil's default `Bitmap.Config.HARDWARE` cannot back a `BitmapShader`

Coil 3 decodes images as `Bitmap.Config.HARDWARE` by default for upload-only display performance. **Hardware bitmaps cannot be sampled by `BitmapShader`** — the shader returns transparent without throwing or logging.

Fix: tell Coil to return a software-backed bitmap, AND defensively re-copy to `ARGB_8888` since other configs (`RGB_565`, `RGBA_F16`) are also device-dependent for shader sampling.

```kotlin
val request = ImageRequest.Builder(context)
    .data(refId)
    .allowHardware(false)
    .build()
val raw = (loader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return
val safe = if (raw.config == Bitmap.Config.ARGB_8888) raw
           else raw.copy(Bitmap.Config.ARGB_8888, false) ?: raw
```

See `AlbumBackgroundRenderer.kt :: rememberBackgroundBitmap`.

## 2. Compose's `ShaderBrush(shader: Shader)` is unreliable for pre-built shaders inside a `graphicsLayer`

`ShaderBrush(shader)` wraps an already-constructed Android `Shader`. When the resulting brush is used by a `drawRect` inside a Compose `Modifier.graphicsLayer { ... }`, the result has been observed to render as silently-transparent — even when the same shader, configured identically, draws correctly through the native canvas. The exact cause is unclear (probably some interaction between `ShaderBrush`'s size-based lifecycle and the layer's render target), but the failure mode is reproducible.

Use the native path instead:

```kotlin
val paint = android.graphics.Paint().apply {
    this.shader = bitmapShader
    alpha = (opacity * 255f).roundToInt()
    isFilterBitmap = true   // bilinear, avoids pixelation at non-1:1 scale
    isDither = true         // reduces banding on low-bit panels
}
drawIntoCanvas { canvas ->
    canvas.nativeCanvas.drawRect(left, top, right, bottom, paint)
}
```

See `AlbumBackgroundRenderer.kt :: drawTiledShader`.

If a future maintainer wants to return to a Compose-native API, the right path is **not** `ShaderBrush(prebuiltShader)`. Subclass `ShaderBrush` and construct the shader inside `createShader(size: Size)` so Compose owns its lifecycle. That hasn't been tested but is the principled fix.

## 2b. `Brush.linearGradient` / `Brush.radialGradient` no-op when drawscope size is empty

Same family of failure as #2, hit from a different angle. Compose's gradient brushes extend `ShaderBrush`, whose `applyTo(size, paint, alpha)` contains:

```kotlin
if (size.isEmpty()) {
    shader = null
    internalShader = null
    createdSize = Size.Unspecified
}
```

`DrawScope` passes its own composable layout size to `applyTo` — **not** the destination rect's size. So when a `Spacer` (or any other zero-size layout) draws a gradient at explicit `topLeft` + `size`, the brush still sees `Size(0, 0)`, nulls its shader, and the rect ends up with no shader on its paint. The draw silently produces nothing.

This is exactly the case for `WorldLockedAlbumBackground`'s Spacer: the Spacer has no `.fillMaxSize()`, and the parent camera `Box` is also content-sized, so the drawscope size is `Size.Zero`. `Brush.linearGradient` and `Brush.radialGradient` both fail this way.

Fix: bypass `Brush.linearGradient` / `radialGradient` and use the native shaders directly:

```kotlin
val shader: android.graphics.Shader = android.graphics.LinearGradient(
    x0, y0, x1, y1, colors, null, android.graphics.Shader.TileMode.CLAMP,
)
val paint = android.graphics.Paint().apply { this.shader = shader; isDither = true }
drawIntoCanvas { canvas ->
    canvas.nativeCanvas.drawRect(left, top, right, bottom, paint)
}
```

See `ProceduralBackgroundRenderer.kt :: drawGradient`.

Alternative fix that we did **not** take: give the drawing composable a real layout size (e.g. `.fillMaxSize()` on the Spacer, plus making the parent camera `Box` `fillMaxSize` so the size actually propagates). Theoretically cleaner but requires propagating size through ancestors that don't currently care.

## 3. Zero-size `Spacer + graphicsLayer` with draws at large world coordinates

`WorldLockedAlbumBackground` initially used:

```kotlin
Spacer(
    modifier = Modifier
        .graphicsLayer { transformOrigin = TransformOrigin(0f, 0f); clip = false }
        .drawBehind { /* drawRect at world coords like (-100000, -100000) */ }
)
```

A `graphicsLayer` on a zero-size `Spacer` allocates a 0×0 RenderNode. Even with `clip = false`, draws at very distant offsets from the layer's origin can be lost or zero-sampled by the OS render pipeline (this also manifests as a "black rectangle" — the rect appears, but the shader inside it samples transparent).

Fix: drop the inner `graphicsLayer`. The parent camera `Box` already supplies the world↔screen transform; the Spacer just needs `drawBehind`, drawing directly into the parent's render target.

This pattern is **safe for node renderers** (`CanvasRenderer.kt`) because those use `graphicsLayer { translationX/Y = cx/cy; ... }` and draw around the layer's local `(0, 0)` — the draw is close to the layer's origin even though the layer is composited at a distant position. The world-locked background has no such translation, so drawing at world `(-100000, -100000)` is far from `(0, 0)` of the inner layer.

## Rule of thumb

If a shader-painted rect renders as a featureless dark rectangle, work through these four causes in order:
1. Bitmap config — `androidBitmap.config` must be `ARGB_8888`.
2. Brush wrapper — replace `ShaderBrush(shader)` (or `Brush.linearGradient` / `Brush.radialGradient`) with native `drawIntoCanvas + Paint(shader)`. Compose `ShaderBrush` family nulls its shader when the drawscope size is empty.
3. Layer scope — drop any zero-size inner `graphicsLayer` between the draw and the camera-transformed parent.
4. Drawscope size — when the failing draw uses an explicit `topLeft + size`, remember that brushes still consult the *drawscope* size (the composable's layout size), not the destination rect's size.

All three failures look identical from the screen.
