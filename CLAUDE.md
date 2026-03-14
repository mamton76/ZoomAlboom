# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew assembleDebug              # Build debug APK
./gradlew test                       # Run unit tests
./gradlew testDebugUnitTest          # Run a specific test class: add --tests "com.mamton.zoomalboom.ExampleUnitTest"
./gradlew connectedAndroidTest       # Run instrumented tests on connected device/emulator
./gradlew clean                      # Clean build artifacts
```

## Architecture

> **Full docs:** [`docs/architecture/overview.md`](docs/architecture/overview.md) — source of truth
> See also: [data-model](docs/architecture/data-model.md) | [modules & DI](docs/architecture/modules.md) | [rendering](docs/architecture/rendering.md) | [navigation](docs/architecture/navigation.md)

Always refer to docs/architecture/

## Tech Stack
- **Language:** Kotlin 2.2.10, **Min SDK:** 24, **Target SDK:** 36
- **UI:** Jetpack Compose + Canvas API + `Modifier.graphicsLayer`
- **DI:** Hilt, 
- **DB:** Room, 
- **Serialization:** kotlinx-serialization, 
- **Images:** Coil 3
- **Async:** Coroutines + Flow
