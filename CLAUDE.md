# CLAUDE.md

This repository contains **ZoomAlboom**, an Android-first interactive multimedia album on an infinite zoomable canvas.

Users build spatial stories from photos, videos, text, stickers, and frames.
Frames are not just visual containers — they are navigation anchors inside the album space.

## Read first

Source-of-truth docs:

- `docs/product/vision.md`
- `docs/product/PRD.md`
- `docs/architecture/overview.md`
- `docs/architecture/data-model.md`
- `docs/architecture/modules.md`
- `docs/architecture/rendering.md`
- `docs/architecture/coordinates.md` — coordinate spaces, camera math, selection invariants
- `docs/architecture/selection.md` — selection state, gesture mapping, gesture stack
- `docs/architecture/navigation.md`
- - `docs/architecture/decisions.md` (if present)
- `docs/architecture/conventions.md`

Working memory / discovered implementation notes:

- `memory/MEMORY.md` — index of per-topic files in `memory/` (framework gotchas, subsystem notes not yet in `docs/architecture/`).

## How to work in this repo

- Do not invent a second architecture if docs already define one.
- Keep **canvas state** separate from **IDE overlay state**.
- Keep domain models independent from Compose/UI concerns.
- Prefer short targeted edits over broad rewrites.
- If architecture changes, update the relevant docs in `docs/architecture/` and 'docs/todo.md'
- Put discovered implementation facts and gotchas into `memory/` (a new file per topic, linked from `memory/MEMORY.md`), not into architecture docs. When a fact stabilizes, graduate it into `docs/architecture/` and delete the memory file.

## Commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest
./gradlew clean
```

## Tech Stack
- **Language:** Kotlin 2.2.10, **Min SDK:** 24, **Target SDK:** 36
- **UI:** Jetpack Compose + Canvas API + `Modifier.graphicsLayer`
- **DI:** Hilt, 
- **DB:** Room, 
- **Serialization:** kotlinx-serialization, 
- **Images:** Coil 3
- **Async:** Coroutines + Flow
