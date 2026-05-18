# ZoomAlboom — Implementation Memory

Discovered facts, framework gotchas, and project notes that don't yet have a home in `docs/architecture/`. Architecture docs are the source of truth for stable knowledge; this directory is for the rest.

## Index

### Compose / framework gotchas
- [Stale closure in `pointerInput`](compose_stale_closure.md) — never snapshot `state.X` into a local `val` used inside pointerInput callbacks.
- [Consume order in `pointerInput`](compose_consume_order.md) — read `changedToUp()` / `changedToDown()` BEFORE calling `consume()`, not after.
- [Dependency gotchas](dependencies_gotchas.md) — no `material-icons-core`, `TabRow` deprecated, `PrimaryTabRow.divider` signature, `Modifier.offset` import.
- [Camera & resize math gotchas](camera_math_gotchas.md) — rotated-frame focus traps in `Transform.toCamera`, opposite-corner pivot + `2*diag` factor in resize-by-handle. Read before touching anything that produces a `Camera` from a target or scales around a handle.
- [Background texture & shader gotchas](background_shader_gotchas.md) — three causes of silently-transparent texture rendering (Coil HARDWARE bitmap, Compose `ShaderBrush(prebuilt)` inside `graphicsLayer`, zero-size inner `graphicsLayer` with distant draws). Read before touching `AlbumBackgroundRenderer.kt` or any new `BitmapShader` usage.

### Subsystems not yet in `docs/architecture/`
- [IDE panel system](ide_panels.md) — `PanelPosition` / `PanelState` / `IdeUiState` model and the `DockedPanel` / `FloatingPanel` / `PanelSlot` split.
- [SceneGraphSerializer gotchas](scene_graph_serializer.md) — `CanvasNode` discriminator is the FQCN (not `"FRAME"` / `"MEDIA"`); test legacy shapes by mutating `JsonObject`s rather than hand-writing JSON; `ignoreUnknownKeys = true` covers additive schema changes.

### Project status
- [Test status](test_status.md) — known-failing tests unrelated to active work.

## What is NOT here

- **Architecture, coordinate math, selection semantics, rendering pipeline** — see `docs/architecture/`. Those docs are the source of truth; do not duplicate them here.
- **Personal preferences about working with the assistant** — those live in Claude's per-session memory and are not part of the repo.

## How to add an entry

1. Create `memory/<topic>.md` with a single H1 title and concise content (rule, why, how to apply).
2. Add a one-line link in the Index above, under the appropriate group.
3. If a topic graduates to stable architecture, move the content into `docs/architecture/` and delete the memory file.
