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
- `docs/architecture/selection.md` — selection state, gesture mapping, gesture stack (today's behavior)
- `docs/architecture/editor-tools.md` — three-axis interaction model (`EditorMode` × `ActiveTool` × Global navigation), per-tool gesture maps (all 7 locked), popup-derivation rule, `EditorState` shape. Future-state authority on gesture allocation; selection.md will graduate to match as the migration lands.
- `docs/architecture/editor-surfaces.md` — logical editor surfaces (`GlobalChromeSurface`, `ToolControlSurface`, `SelectionActionSurface`, `ConceptEditorSurface`, `AddContentSurface`). One baseline layout for phone + tablet; future panels are alternative placements.
- `docs/architecture/navigation.md`
- `docs/architecture/appearance.md` — shared `NodeAppearance` model, `MediaAppearance` vs `FrameAppearance`, `OverlayStyle`, `MaskNode` constraints.
- `docs/architecture/media-appearance.md` — media-specific appearance surface (crop, color, frame decoration, presets, derivatives)
- `docs/architecture/context-menu.md` — long-press context menu; `(selection, anchor)` model; baseline rendering of the `SelectionActionSurface`. Status: § 15.4 gesture rewrite + § 15.5 bar removal shipped; remaining slices in `todo.md § 15`.
- `docs/architecture/presentation-profile.md` — album/frame presentation form factor
- `docs/architecture/cloud-sync.md` — local-first cloud sync (per-album `RemoteBinding`, revision lineage, conflict-copy policy, encryption-readiness). Decided 2026-06-03, implementation deferred — slices in `todo.md § 26`.
- `docs/architecture/decisions.md` (if present)
- `docs/architecture/conventions.md`

Working memory / discovered implementation notes:

- `memory/MEMORY.md` — index of per-topic files in `memory/` (framework gotchas, subsystem notes not yet in `docs/architecture/`).

Pending discussions (scratch, not source-of-truth — may overlap with or contradict architecture docs until reconciled):

- `docs/to_discuss.md` — open design questions. As of 2026-06-03, **no open topics**: § 5 Album storage & cloud sync graduated to `docs/architecture/cloud-sync.md`. Recent graduations (§ 5 cloud sync, § 8 MaskNode UX, § 9 editor surfaces, § 11 EditorState) live in the "Recently graduated" trailer with pointers to their architecture docs.

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
