# Data Model

> This document describes the **target** data model. See [Migration Notes](#migration-notes) at the bottom for differences from the current implementation.
>
> Related: [overview](overview.md) | [rendering](rendering.md) | [modules & DI](modules.md) | [TODO](../todo.md)

Three independent storage layers:

1. **Metadata & UI State** — relational (Room)
2. **Canvas Scene Graph** — document (JSON)
3. **Operation History / Undo-Redo** — append-only (JSON, planned)

## Domain Models

### Transform

```kotlin
@Serializable
data class Transform(
    val cx: Float = 0f,       // center X in world space
    val cy: Float = 0f,       // center Y in world space
    val w: Float = 100f,      // base width in world units
    val h: Float = 100f,      // base height in world units
    val scale: Float = 1f,    // user-applied scale multiplier (pinch-to-resize)
    val rotation: Float = 0f, // degrees
    val zIndex: Float = 0f,   // draw order
) {
    val renderW: Float get() = w * scale   // rendered width in world units
    val renderH: Float get() = h * scale   // rendered height in world units
}
```

**Coordinate contract:**
- `cx`/`cy` = center of the node in world space (not top-left corner).
- `w`/`h` = base world-unit dimensions. **Not** native pixel size, **not** immutable intrinsic dimensions, **not** final rendered size. They may be rebased later (see Rebasing below) as long as the visual `renderW × renderH` is preserved.
- `scale` = current multiplier over `w/h`. Resize gestures mutate `scale`, **not** `w/h`.
- **Render invariant:** `renderW × renderH = w*scale × h*scale`. Every consumer (rendering, AABB, hit-test, viewport culling, selection bounds, group rect math) MUST read `renderW/renderH`, never raw `w/h`.
- **Do NOT conflate `Transform.scale` with `Camera.scale`** — they have different semantics (see Camera below).

**Creation conventions (`CanvasNodeFactory`):**
Unified across node types: `transform.scale = 1 / camera.scale` at creation, `w/h = targetRender * camera.scale`.

| Node | `targetRenderW × targetRenderH` (world units) |
|------|------------------------------------------------|
| `Frame` | `(screenWidth/camera.scale × 0.8) × (screenHeight/camera.scale × 0.8)` |
| `Media` | aspect-preserving fit of `imageWidth × imageHeight` into 80% of viewport |

The `targetRender` formulas inherently contain a `1/camera.scale` factor (the world-unit viewport shrinks as we zoom in). Putting `1/camera.scale` into `scale` and multiplying `w/h` by `camera.scale` cancels that factor out of `w/h` — so **`w/h` are camera-independent**, representing the canonical render size at `scale = 1`. Two frames created at different zoom levels end up with identical `w/h` (e.g. `screenW × 0.8`).

Visual: `renderW = w * scale = targetRender` regardless of split. Pleasant default: at `camera.scale = 1`, `scale = 1`.

Both factories also set `VisibilityPolicy(referenceScale = camera.scale)` so LOD knows the zoom at which the node is "meant to be viewed."

**Rebasing (future capability):**
Two operations should be supported when grouping, frame transforms, or LOD stabilization needs a renormalized transform — neither changes the visual:
```
normalizeTransform():     // bake scale into w/h
  w = renderW; h = renderH; scale = 1

rebaseScale(newScale):    // change scale, recompute w/h to preserve render
  w = renderW / newScale
  h = renderH / newScale
  scale = newScale
```
Use cases: rebase against camera scale or a parent frame's scale, clean up extreme `scale` values after long resize sessions, post-group transformations, or LOD-stable comparisons.

### Camera

`Camera` lives in `core/math/Camera.kt`:

```kotlin
data class Camera(
    val cx: Float = 0f,       // graphicsLayer translationX (screen-pixel units)
    val cy: Float = 0f,       // graphicsLayer translationY (screen-pixel units)
    val scale: Float = 1f,    // zoom factor: 1.0 = 100%, 2.0 = zoomed in 2×
    val rotation: Float = 0f, // canvas rotation in degrees
) {
    companion object {
        const val MIN_SCALE = 0.00005f
        const val MAX_SCALE = 10000f
    }
}
```

**Camera.cx/cy semantics:** `cx`/`cy` are the `translationX`/`Y` values applied to the Compose `graphicsLayer`. Screen position of world point `(wx, wy)` = `(wx*scale + cx, wy*scale + cy)`. To center world point `(wx, wy)` at screen center: `cx = screenWidth/2 - wx*scale`.

**Camera ↔ Transform conversion** — `Transform.toCamera()` in `core/math/TransformUtils.kt`:
```kotlin
fun Transform.toCamera(screenWidth: Float, screenHeight: Float, fillFraction: Float = 0.9f): Camera {
    val scale = minOf(screenWidth * fillFraction / renderW, screenHeight * fillFraction / renderH)
    return Camera(
        cx = screenWidth / 2f - cx * scale,
        cy = screenHeight / 2f - cy * scale,
        scale = scale,
        rotation = rotation,
    )
}
```

### CanvasNode (sealed)

| Variant | Extra fields | Purpose |
|---------|-------------|---------|
| `Frame` | `label`, `color` (hex string), `overrides: Map<String, FrameMembershipOverride>`, `appearance: FrameAppearance?` | Navigation area / logical grouping. Membership is computed (geometry + overrides) — see [frame-membership.md](frame-membership.md). |
| `Media` | `mediaRefId` (FK to `media_library`), `mediaType`, `tags`, `intrinsicPixelWidth/Height`, `appearance: MediaAppearance?` | Any media asset (image, video, text; future: audio, sticker, animated photo, vector shape) |
| `Widget` | `widgetType: WidgetType`, `config: WidgetConfig`, `dataSource: WidgetDataSource`, `links: List<WidgetLink>` | Canvas-native smart object with data binding and navigation (post-MVP) |

`Media.intrinsicPixelWidth/Height` are the source's native pixel dimensions (after EXIF rotation). They are **media metadata**, not layout — kept separate from `Transform.w/h` per the scaling contract above. LOD uses them to compute `sourcePxPerScreenPx = intrinsicPixelWidth / (renderW * camera.scale)` for downsampling decisions. `0` = unknown (LOD falls back to render-size-only). When `media_library` ships, intrinsic dims will also live there as the source of truth; the field on `Media` is a per-instance cache.

Both share `id: String`, `transform: Transform`, `visibilityPolicy: VisibilityPolicy?`. Both are `@Serializable`.

### AlbumPresentationProfile

Album-level declaration of the intended screen shape for viewing/presenting (aspect ratio, orientation, default fit mode, default outside-frame behaviour, safe-area inset). Also owns nested `frameChrome: FrameChromeDefaults` — per-mode default chrome style (see below). Stored in the scene graph JSON root alongside `albumBackground`. Nullable — older albums and default-state new albums both work without it. Drives new-frame creation defaults, View-mode camera transforms, and editor overlays. Does **not** constrain or resize the infinite canvas.

**→ Full type definitions, persistence shape, camera-math integration, open questions:** [presentation-profile.md](presentation-profile.md)

### FrameChrome (`FrameChromeStyle`, `FrameChromeDefaults`, `FrameChromeOverride`)

The editor/viewer hint layer drawn on the *edge* of each frame — separate from `FrameAppearance` (album content). Closed `FrameChromeStyle` enum: `Hidden`, `CornersOnly`, `SubtleOutline`, `SoftGlow`, `LabelTab`, `FullOutline`, `DebugBounds`. Per-mode defaults live in `AlbumPresentationProfile.frameChrome: FrameChromeDefaults` (serialized). Transient `FrameChromeOverride(target, style, lifetime, reason?)` instances live in `CanvasState.editor.chromeOverrides` and are **never serialized**. A pure resolver picks one style per frame using most-specific-target-wins (MVP targets: `ALL`, `SELECTED`, `CURRENT`) with most-recent-pushed as tiebreaker. Mode default is the implicit lowest-priority entry.

**→ Vocabulary, resolver rules, render layer, producers, open questions:** [frame-chrome.md](frame-chrome.md)

### Backgrounds (`BackgroundData`, `AlbumBackground`)

Backgrounds are **not** `CanvasNode` objects — they are render-layer style properties stored alongside the nodes list in the scene graph. The payload is a sealed `BackgroundData` family with three variants: `SolidBackgroundData` (hex color), `TextureBackgroundData` (Coil-loadable URI + `TileData` — `tileMode` ∈ {None, Stretch, Cover, Contain, Repeat}), and `ProceduralBackgroundData` (parameterised `ProceduralPattern` + optional `fillColor` drawn under the pattern). Albums wrap it as `AlbumBackground(data, anchorMode)` where `anchorMode ∈ { CameraLocked, WorldLocked }`. Frame backgrounds live inside `FrameAppearance.background` (the frame is implicitly its own anchor — no `anchorMode` is stored). `albumBackground` lives in the JSON scene graph root (§1.3 wrapper).

**→ Full type definitions, rendering order, tile algorithm, MVP scope:** [background.md](background.md)

### VisibilityPolicy & RenderDetail

Controls how a node appears at different zoom levels. Stored per-node (`visibilityPolicy` field, nullable — falls back to type-based defaults in `LodResolver`).

```kotlin
@Serializable
data class VisibilityPolicy(
    val referenceScale: Float,       // camera zoom at which node is "meant to be viewed"
    val minRelativeZoom: Float,      // below → belowRangeMode
    val maxRelativeZoom: Float,      // above → aboveRangeMode
    val belowRangeMode: RenderDetail,
    val aboveRangeMode: RenderDetail,
)

@Serializable
enum class RenderDetail { Hidden, Stub, Preview, Full, Simplified }
```

LOD resolution is performed by `LodResolver` (`core/math/LodResolver.kt`) — see [rendering.md §4b](rendering.md#4b-level-of-detail-resolution-lod) for the algorithm.

### MediaType

```kotlin
enum class MediaType {
    IMAGE,        // raster photo/image
    VIDEO,        // video clip
    AUDIO,        // audio clip
    TEXT,         // inline text block
    STICKER,      // static sticker/illustration
    VECTOR_SHAPE, // SVG or vector primitive

    // future
    ANIMATED_PHOTO, // Live Photo / animated GIF
}
```

`IMAGE`, `VIDEO`, `AUDIO`, `TEXT`, `STICKER`, `VECTOR_SHAPE` are MVP variants. `ANIMATED_PHOTO` is planned post-MVP.

### NodeAppearance, MediaAppearance, FrameAppearance

Non-destructive visual styling for canvas nodes. Each variant of `CanvasNode` that supports styling carries its own typed appearance container — `MediaAppearance` on `CanvasNode.Media`, `FrameAppearance` on `CanvasNode.Frame` — both extending a shared sealed `NodeAppearance` base. The base owns cross-cutting properties: `opacity`, `cornerRadius`, `overlays: List<OverlayStyle>`, `border`, `shadow`. Shared *value* types (`OverlayStyle`, `OverlaySource`, `NodeBlendMode`, `BorderStyle`, `ShadowStyle`) are defined once and reused.

> **Proposed evolution.** `cornerRadius: Float` will be replaced by `clip: ClipShape` + `alphaMask: AlphaMask?` (continuous-alpha mask: image, gradient, or procedural source). See [appearance.md § 12](appearance.md#12-proposed-evolution--clip--alphamask). Not yet implemented.

```kotlin
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float
    abstract val cornerRadius: Float
    abstract val overlays: List<OverlayStyle>
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?
}
```

**Core formulae:**
- `source media asset + MediaAppearance = rendered media object on canvas`
- `frame rect + FrameAppearance + linked contents = rendered frame on canvas`

The overlay rule:

> `NodeAppearance.overlays` is a single field. On a media node it paints above the photo pixels (bounded by the media rect); on a frame node it paints above the frame's combined contents output (clipped to the frame). The renderer dispatches by node type — the data model is uniform.

`overlays` is a `List<OverlayStyle>` with declaration-order compositing (entry `[i]` over entry `[i-1]`; empty list = no overlays). See [appearance.md § 4](appearance.md#4-overlay-semantics--one-field-two-render-pipeline-positions) for the per-type render-pipeline positions.

**`MediaAppearance` (one concrete variant — see [media-appearance.md](media-appearance.md) for full surface):**

```kotlin
@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val overlays: List<OverlayStyle> = emptyList(),       // inherited; above this media's pixels
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,
    val crop: CropSettings = CropSettings(),
    val colorAdjustments: MediaColorAdjustments? = null,
    val frameDecoration: MediaFrameDecoration? = null,             // decorative photo-frame around one media — NOT a CanvasNode.Frame
    val caption: CaptionStyle? = null,
) : NodeAppearance()
```

**`FrameAppearance` (the other concrete variant — see [appearance.md § 3](appearance.md#3-frameappearance--containercontent-level-styling)):**

```kotlin
@Serializable
@SerialName("FrameAppearance")
data class FrameAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val overlays: List<OverlayStyle> = emptyList(),       // inherited; above the frame's combined contents, clipped
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,
    val background: BackgroundData? = null,                        // behind the frame's linked contents
    val contentEffect: FrameContentEffect? = null,                 // future: off-screen filter pass over rendered contents
    val titleStyle: FrameTitleStyle? = null,
) : NodeAppearance()
```

`FrameAppearance.background` absorbs what is currently `Frame.background: BackgroundData?` — see [Migration Notes](#migration-notes). The frame is still its own anchor (no `anchorMode` is stored on `FrameAppearance.background` — that field only exists on `AlbumBackground`).

**Render pipeline.** `MediaAppearance` is rendered inside a single `MediaRenderer` pass — overlays included (iterated in list order). `FrameAppearance` requires a *layered* frame renderer (background → linked contents → `overlays` → border/title), because frame overlays composite above the children rather than inside the frame's own surface. See [appearance.md § 6](appearance.md#6-render-pipeline-implication) and [rendering.md § 6b](rendering.md#6b-layered-frame-rendering).

**→ Full type definitions, render-pipeline contract, terminology table, persistence rules, non-goals:** [appearance.md](appearance.md). Media-specific detail (crop, color adjustments, frame decoration, presets, rendered derivatives): [media-appearance.md](media-appearance.md).

**Persistence:** `appearance` is a nullable field on each owning `CanvasNode` variant in the scene graph JSON. `null` = default rendering. `ignoreUnknownKeys` handles old nodes. `NodeAppearance` is polymorphic via the `@SerialName` discriminator (`"MediaAppearance"` / `"FrameAppearance"`).

**Rendered derivatives:** Users can flatten the current `MediaAppearance` into a new image asset registered in `media_library` with `origin = RENDERED_DERIVATIVE`.

### Widget System

Widgets are `CanvasNode.Widget` entries in the scene graph — canvas-native smart objects with a data source binding and clickable navigation links. They participate in hit-testing, selection, drag/resize, LOD, and viewport culling identically to `Frame` and `Media`.

Key types: `WidgetType` enum (Map, Calendar, TagCloud, Portal, FrameNavigator, People, FamilyTree, Route, RecipeIndex, PeriodSummary, …), `WidgetDataSource` sealed class (AlbumNodes/Tags/Dates/Places/StaticConfig), `WidgetLink`, `NavigationTarget` sealed class (ToFrame/ToNode/ToAlbum/ToFilteredView/ToExternalUri), `WidgetConfig` per-type sealed class, `CanvasWidgetRenderer<TConfig>` Composable interface.

**→ Full type definitions, widget categories, renderer contract, LOD table, wizard integration:** [widgets.md](widgets.md)

**Persistence:** `CanvasNode.Widget` serialized in the scene graph nodes array. `dataSource` and `links` stored on the node; widget UI state is transient or in `ide_workspaces`.

### AlbumMeta

```kotlin
data class AlbumMeta(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val thumbnailUri: String?,
)
```

### AlbumData

Pairs metadata with its scene graph: `AlbumMeta` + `List<CanvasNode>`.

### RemoteBinding *(planned — cloud sync)*

Cloud connection is modeled as an optional, per-provider `RemoteBinding` keyed by stable `AlbumId` — **not** as a `storageMode: Local | GoogleDrive` flag on `Album`. Absence of a binding = local-only album. The binding owns the last-synced revision id, pending-sync queue marker, and provider-specific location fields (e.g. Drive `folderId`). New Room table (`remote_bindings`) when the slice ships. Conflict detection uses **revision lineage** (`headRevisionId` + `parentRevisionId` on the scene-graph root, minted at each `FinishInteraction` commit), never timestamps.

**→ Decision, open-flow gating, conflict policy, encryption constraint, deferred questions:** [cloud-sync.md](cloud-sync.md). Implementation slices (deferred): [todo.md § 26](../todo.md#26-cloud-sync-deferred).

## Persistence

### Room (SQLite)

**Database:** `AppDatabase` (`zoom_album.db`)

#### Table `albums`

| Column | Type | Description |
|--------|------|-------------|
| `id` | Long (PK) | Auto-generated |
| `name` | String | Album name |
| `updatedAt` | Long | Epoch millis, used for sorting on home screen |
| `thumbnailUri` | String? | URI of album thumbnail |

#### Table `ide_workspaces` (UI State)

Isolates panel state from the canvas. Panel collapse/expand writes here without touching the scene graph JSON.

| Column | Type | Description |
|--------|------|-------------|
| `album_id` | Long (PK/FK) | Link to album |
| `activeTheme` | String | Selected color theme (e.g. "DarkCyan") |
| `panelsState` | String (JSON) | Serialized panel state: docked/floating, collapsed/expanded, x/y for floating |

#### Table `media_library` (Project Media Files)

Registry of all media used in an album. Allows finding all usages of a single file. Includes both imported assets and rendered derivatives (flattened appearance recipes).

| Column | Type | Description |
|--------|------|-------------|
| `id` | String (PK) | Hash or UUID of the file |
| `album_id` | Long (FK) | Binding to album |
| `sourceUri` | String | Content URI (local) or URL (network) |
| `mediaType` | Enum | IMAGE, VIDEO, TEXT (current); AUDIO, STICKER, ANIMATED_PHOTO, VECTOR_SHAPE (future) |
| `status` | Enum | AVAILABLE, MISSING (validated at startup) |
| `origin` | Enum | IMPORTED (default) or RENDERED_DERIVATIVE |
| `sourceAssetId` | String? | For derivatives: id of the source asset |
| `recipeHash` | String? | For derivatives: hash of the `MediaAppearance` used to render it |
| `widthPx` | Int? | Intrinsic pixel width (null = unknown) |
| `heightPx` | Int? | Intrinsic pixel height (null = unknown) |

Rendered derivatives are stored in `filesDir/media/<albumId>/rendered/`. Imported assets are external URIs or copied to `filesDir/media/<albumId>/imported/`.

### JSON Scene Graph

**Location:** `filesDir/scene_{albumId}.json`

The scene graph is serialized as a `SceneGraph` root object wrapping `albumId`, `camera`, and `nodes`. A migration fallback detects the legacy bare-`List<CanvasNode>` format (raw JSON starting with `[`) and wraps it on read with a default camera.

```json
{
  "albumId": 123,
  "camera": { "cx": 0, "cy": 0, "scale": 1.0, "rotation": 0 },
  // "background": { ... },  // planned — see §19
  // "profile":    { ... },  // optional, omitted when null — see §22 (presentation-profile.md)
  "nodes": [
    {
      "id": "node_1",
      "type": "MEDIA",
      "mediaRefId": "media_abc",
      "transform": { "cx": 300, "cy": 350, "w": 400, "h": 300, "scale": 1.0, "rotation": 0, "zIndex": 1 },
      "tags": ["vacation", "2024"]
      // "appearance" omitted when null — see appearance.md (MediaAppearance: overlays, border, shadow, …)
    },
    {
      "id": "frame_1",
      "type": "FRAME",
      "color": "#FF5555",
      "transform": { "cx": 450, "cy": 450, "w": 800, "h": 600, "scale": 1.0, "rotation": 0, "zIndex": 0 }
      // "overrides" omitted when empty — see frame-membership.md
      // "appearance" omitted when null — see appearance.md (FrameAppearance: background, overlays, …)
    }
  ]
}
```

Key points:
- `camera` saves last camera position (restored on album open) — full `Camera` shape with `cx`, `cy`, `scale`, `rotation`
- `mediaRefId` links to `media_library` table instead of storing URI directly
- Frame membership is computed at read time from geometry plus per-frame `overrides` — see [frame-membership.md](frame-membership.md)
- Serialized via kotlinx-serialization (`prettyPrint`, `ignoreUnknownKeys`)
- Legacy bare-list files are upgraded transparently on first read

**Components:** `FileStorageHelper` (raw file I/O) + `SceneGraphSerializer` (encode/decode) + `MediaRepositoryImpl` (abstraction).

## Undo/Redo Model (Snapshot-Based)

Implemented. Two `ArrayDeque<CanvasCommand>` (undo + redo) inside `CommandHistory`, capped at 50 entries. List-shape `before`/`after: List<CanvasNode>?` collapses single- and multi-node ops into one type — `CommandKind`: ADD, REMOVE, DELETE, DUPLICATE, MOVE, RESIZE, ROTATE. Gestures are grouped via `BeginInteraction` / `FinishInteraction`. Persisted to `filesDir/history_{albumId}.json` on `onCleared()`.

**→ Full type definitions, invariants, grouping mechanics, file map:** [undo-redo.md](undo-redo.md)

## Repository Interfaces

```
domain/repository/
├── ProjectRepository      # observeAlbums(), createAlbum(), deleteAlbum()
├── MediaRepository        # loadSceneGraph(albumId), saveSceneGraph(albumId, nodes)
└── MediaLibraryRepository # register/query/validate media files (planned)
```

Implementations in `data/repository/`, bound via Hilt `@Binds`.

## Migration Notes

| Area | Current | Target |
|------|---------|--------|
| `albums.createdAt` | exists (not yet removed) | to be removed |
| `albums.thumbnailPath` | `thumbnailPath: String?` (not yet renamed) | rename to `thumbnailUri` |
| `Transform.width/height` | `width`, `height` | renamed to `w`, `h` ✓ |
| `Transform.x/y` | top-left corner | renamed to `cx/cy` (center-based) ✓ |
| `Transform.w/h` | normalized (short side = 1) | actual world-unit size ✓ |
| `Transform.zIndex` | separate field on `CanvasNode` | moved into `Transform` ✓ |
| `Camera` | in `CanvasViewModel` | moved to `core/math/Camera.kt` ✓ |
| `CanvasNode.Media.uri` | direct URI string | `mediaRefId` FK to `media_library` |
| `Frame.containsNodeIds` | stored list (never wired) | removed; membership computed from geometry + `Frame.overrides` ([frame-membership.md](frame-membership.md)) |
| `Frame.color` | Long (ARGB) | hex string |
| `Frame.background` | `BackgroundData?` directly on `Frame` | moved under `Frame.appearance.background` as part of the [appearance system](appearance.md). `SceneGraphSerializer` rewrites legacy top-level `background` into `appearance.background` on read; new writes always nest. ✓ |
| `Frame.appearance` | not present | new `FrameAppearance?` container (background + inherited `overlays: List<OverlayStyle>` + border/shadow + titleStyle + contentEffect). See [appearance.md § 3](appearance.md#3-frameappearance--containercontent-level-styling). ✓ |
| `Media.appearance` | not present | new `MediaAppearance?` container (crop + colorAdjustments + inherited `overlays: List<OverlayStyle>` + frameDecoration + caption + border/shadow). See [appearance.md § 2](appearance.md#2-mediaappearance--object-level-styling). ✓ |
| `FrameAppearance.contentOverlays` | legacy field on `FrameAppearance` (pre-2026-05-19) | unified into `NodeAppearance.overlays` (inherited). `SceneGraphSerializer` lifts legacy `contentOverlays` JSON into `overlays` on read; writes emit `overlays` only. ✓ |
| `MediaAppearance` shape | hypothetical `overlays: List<MediaOverlay>` + `OverlayKind`/`OverlayBlendMode` enums (never landed) | `overlays: List<OverlayStyle>` using the shared `OverlayStyle` / `OverlaySource` / `NodeBlendMode` types from [appearance.md § 7](appearance.md#7-shared-value-types). ✓ |
| Decorative photo-frame on media | hypothetical `frameOverlay: FrameOverlay?` with `FrameRenderMode { Stretch, NineSlice }` | landed as `frameDecoration: MediaFrameDecoration?` with `MediaFrameDecorationMode { Stretch, NineSlice }` to finish disambiguating "frame" (decorative photo-frame around one media is not a `CanvasNode.Frame`). ✓ |
| `CanvasNode.tags` | not present | added |
| `ide_workspaces` table | not present | new table |
| `media_library` table | not present | new table |
| Scene graph `camera` | not saved | saved in JSON root ✓ |
| Undo/Redo | not present | Snapshot-based list-shape `CanvasCommand` deque (cap 50), persisted to `history_{albumId}.json` on `onCleared()` |
