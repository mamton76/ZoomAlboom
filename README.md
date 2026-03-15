# ZoomAlboom

ZoomAlboom is an Android-first interactive multimedia album built around an infinite zoomable canvas.

## Core idea
Users create spatial stories from photos, videos, text, stickers, and frames.
Frames help structure the space and support navigation through the album.

## Main use cases
- Family album
- Travel diary
- Cookbook
- Educational album
- Project album

## Documentation
- Product vision: `docs/product/vision.md`
- Product requirements: `docs/product/PRD.md`
- Architecture overview: `docs/architecture/overview.md`
- Data model: `docs/architecture/data-model.md`
- Rendering: `docs/architecture/rendering.md`
- Navigation: `docs/architecture/navigation.md`

## Tech stack
- Kotlin
- Jetpack Compose
- Hilt
- Room
- kotlinx-serialization
- Coil
- Coroutines + Flow

## Build
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
