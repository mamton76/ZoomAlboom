# Editor Tools

> Related: [editor-surfaces](editor-surfaces.md) | [selection](selection.md) | [overview](overview.md) | [context-menu](context-menu.md) | [presentation-profile](presentation-profile.md) | [navigation](navigation.md) | [data-model](data-model.md)

This doc owns the **gesture-allocation** axis of the editor (three-axis interaction model, per-tool gesture maps, dispatch, state shape). The **UI rendering** of tools and selection actions — `ToolControlSurface`, `SelectionActionSurface`, `ConceptEditorSurface`, `AddContentSurface`, `GlobalChromeSurface` — lives in [editor-surfaces.md](editor-surfaces.md). The two docs are complementary: tools define what gestures *mean*; surfaces define how the user *reaches* them.

**Status — decided 2026-05-24, fully decided 2026-06-03.** Settles `to_discuss.md § 3` (active-tool framework), § 4 (per-tool gesture maps), § 6 (Eraser modes), and the `MaskEdit` portion of § 8 (`MaskNode` editing UX). All seven tools' gesture maps are now locked.

The editor is fundamentally infinite-canvas, gesture-heavy, camera-driven, stylus-oriented, and spatial. To stay coherent as tool variety grows, gesture meaning is governed by an explicit **active tool**, with **navigation** orthogonal and always available. This doc specifies the three-axis interaction model, the locked `SelectionTool` gesture map, and how the framework lands in code.

The short form:

- **`EditorMode`** answers: *"What is the user doing with the album?"* — View / Edit / Present.
- **`ActiveTool`** answers: *"What does single-finger / stylus input mean right now?"* — Edit-only.
- **Global navigation** answers: *"How does the camera move?"* — always-on, two-finger gestures.

---

## 1. The three-axis model

Three independent axes govern editor state. They don't collapse into each other.

### 1.1 `EditorMode` (always exactly one active)

| Mode | Editing | Tool axis | Default chrome | Primary gestures |
|---|---|---|---|---|
| `View` | No | n/a (no tool) | Subtle ([frame-chrome.md](frame-chrome.md)) | tap = navigate to frame / activate link; single-finger pan |
| `Edit` | Yes | Active | Full | Per active tool (see § 2) |
| `Present` | No | n/a | Hidden | Presentation playback (separate concern) |

Today's TopBar mode toggle cycles `View ↔ Edit` ([selection.md § 7](selection.md#7-mode-interaction)). `Present` is reached via a separate fullscreen / play action — *not* a third position on the same toggle, because Present is "enter from anywhere," not "switch between."

### 1.2 `ActiveTool` (Edit mode only)

`sealed interface EditorTool` — the currently-selected tool that owns single-finger / stylus interpretation. Vocabulary in § 3. Defaults to `Selection`: the safe default that never destructively alters content.

The tool axis is **meaningful only in Edit mode.** In View and Present, `activeTool` is conceptually nil — every "what does FreeDraw do in Present?" question dissolves.

### 1.3 Global navigation (always-on)

Two-or-more-finger gestures drive the camera, regardless of mode or active tool:

- two-finger pan
- pinch zoom
- two-finger rotate

This layer is owned by [`infiniteCanvasGestures` (selection.md § 5)](selection.md#5-gesture-stack) and is not part of any tool. Tools never implement their own camera control.

---

## 2. Gesture allocation rule

In **Edit mode** the rule is strict and uniform:

```
two-finger gestures     →  global navigation (camera)
single-finger / stylus  →  active tool
```

**No phone fallback for single-finger pan.** The user takes two fingers to navigate while a tool is active. Matches Procreate / Concepts / Infinite Painter / ZoomNotes ("fingers navigate, stylus edits"). The predictability is worth the muscle-memory cost — single-finger ambiguity between "pan" and "draw / marquee / erase" is the failure mode this avoids.

In **View mode** the rule relaxes — no tool claims single-finger, so the disambiguation concern evaporates:

| Gesture | Result |
|---|---|
| single-finger drag | camera pan |
| pinch | zoom |
| two-finger rotate | rotate |
| tap on frame / link | navigate / activate |

In **Present mode**, gestures are presentation-domain (advance / dismiss / pan) and not specified here. See [presentation-profile.md](presentation-profile.md) when Present surfaces ship.

---

## 3. Tool vocabulary

`EditorTool` is a **closed** sealed interface. Adding a variant is a deliberate API change — gesture dispatch, popup derivation, and tool-selector UI all branch on it.

| Tool | Primary role | Per-tool gesture map | Topbar settings (target) |
|---|---|---|---|
| `Selection` (default) | Select / move / resize / rotate / multi-select / marquee | § 4.1 (locked) | Selection mode (rect / lasso), selection rule (intersects / contained) |
| `FreeDraw` | Freehand vector strokes (stylus + finger) | § 4.2 (locked) | Brush size, color, opacity, smoothing, stabilization, pressure-width toggle |
| `Shape` | Predefined vector primitives (rect / ellipse / line / arrow / polygon / star) | § 4.3 (locked) | Primitive picker, fill, stroke, stroke width, primitive params, aspect-ratio toggle |
| `Text` | Editable text nodes | § 4.4 (locked) | Font, size, color, background, stroke, shadow, alignment |
| `VectorEdit` | Per-node / per-handle editing of vector geometry | § 4.5 (locked) | Node type (smooth / sharp / mirrored / disconnected), close/open path, simplify |
| `Eraser` | Object delete + vector partial erase (MVP); raster partial post-MVP | § 4.6 (locked) | Mode (object / vector partial); brush size (partial only) |
| `MaskEdit` | Create and edit `MaskNode`s — owns both creation and editing because no other tool produces them | § 4.7 (locked) | Mask geometry picker (Rect / Ellipse / Path / Free); aspect-ratio toggle for primitives |

`VectorEdit`, vector-partial-`Eraser`, and `MaskEdit` depend on data-model concepts not yet implemented (vector nodes, `StrokeNode`, `ShapeNode`, `MaskNode`).

`EditorTool` is **flat** — brush presets, shape primitives, eraser modes, and mask geometries are *settings of a tool*, not separate tool identities. `VectorEdit` and `MaskEdit` are **context-gated**: they exist in the type vocabulary but don't appear as permanent entries in the primary tool selector — they're entered when a valid target is selected (exact selector discoverability UX is deferred). The user-facing rendering of tool selection + tool controls is the `ToolControlSurface` — see [editor-surfaces.md § 4](editor-surfaces.md#4-toolcontrolsurface--design).

---

## 4. Per-tool gesture maps

Locks the gesture map for each of the seven `EditorTool` variants.

### 4.1 `SelectionTool`

The default tool. Gesture map fully specified for MVP.

| Gesture | Result | Action |
|---|---|---|
| Tap on a node | Replace — selection becomes `{node}` | `SelectNode(id)` |
| Tap on empty | Clear selection | `DeselectAll` |
| Drag on empty | Marquee select (additive if selection non-empty at drag start, replace otherwise) | `SelectNodesInRect(rect, additive)` |
| Drag on selected node body | Move whole selection | `MoveSelection(dx, dy)` |
| Drag on a resize / rotate handle | Resize / rotate selection | `ResizeSelection` / `RotateSelection` |
| Long-press on node | Open context menu anchored on that node | (see [context-menu.md](context-menu.md)) |
| Long-press on stacked nodes | Open context menu with inline overlap picker | (see [context-menu.md](context-menu.md)) |
| Two-finger | Global navigation (§ 1.3) | — |

**Migration note.** This is a change from today's [selection.md § 2](selection.md#2-gesture-mapping). Today's rect-select requires `long-press-on-empty + drag`. The locked model makes `drag-on-empty` directly initiate marquee select. The long-press-on-empty intent moves to an empty-canvas context menu (or no-op) — see § 8.

**Selection modes.** Two modes, both shipped under `SelectionTool`:

- **Rectangle marquee** (MVP first) — drag traces an axis-aligned rect; selects any node whose bounding box matches the selection rule.
- **Lasso** (MVP scope, later) — drag traces a freeform path; selects any node whose bounding box (MVP) or true geometry (post-MVP) intersects the path.

**Selection rule.** Per `SelectionTool` topbar setting:

- **`Intersects`** (default) — node selected if any part of its bounding box overlaps the marquee. Touch-friendly.
- **`FullyContained`** — node selected only if its entire bounding box fits inside the marquee.

Default is `Rectangle + Intersects` because `intersects` is forgiving on touch devices.

**Geometry MVP.** Both rectangle and lasso use bounding-box-vs-marquee intersection in MVP. True per-path geometric overlap for lasso (selecting nodes whose actual shape clips the lasso path, not just their bounding box) is a post-MVP refinement.

### 4.2 `FreeDraw`

Tap creates a dot; drag creates a freehand stroke. Stylus and finger draw symmetrically — pressure used when reported, defaults to 1.0 otherwise.

| Gesture | Result |
|---|---|
| Tap | Single-point stroke (a dot) at the tap location |
| Drag | Freehand stroke; samples collected at input rate |
| Long-press | Global popup (per § 5) |
| Two-finger during stroke | Finalize current stroke, switch to navigation |
| Two-finger (no active stroke) | Global navigation |

**Data model.** Strokes are a new `StrokeNode`, distinct from Photo / Video / Text / Frame. Storage: raw samples `(x, y, pressure, time)` as source of truth; a smoothed bezier render-path cache is derived at finalize and lazily updated. Raw samples preserve fidelity for replay / animation; bezier cache keeps render fast. Stroke bytes are small relative to media — no compression for MVP.

**Frame interaction.** Auto-pin: a stroke whose geometry lands inside a frame auto-joins that frame, same membership rule as media. Manual detach via long-press popup.

**Stylus vs. finger.** Symmetric — both draw with current brush. Pressure used when reported (`MotionEvent.TOOL_TYPE_STYLUS`), defaults to 1.0 otherwise. The Procreate "stylus enforces finger pan" pattern is explicitly **not** MVP — resolves the open question previously deferred in § 8.

**Topbar settings.** Brush size, color, opacity, smoothing, stabilization, pressure-affects-width toggle. Pressure-affects-opacity available as an additional toggle (off by default).

**Persistence.** Stay in FreeDraw after each stroke.

### 4.3 `Shape`

Tap drops a default-sized primitive; drag rubber-bands to size. Primitives: rectangle, ellipse, line, arrow, polygon, star.

| Gesture | Result |
|---|---|
| Tap | Drop default-sized primitive at tap point |
| Drag | Rubber-band — primitive sizes live from start to current; release finalizes |
| Long-press | Global popup |
| Two-finger | Global navigation |

**Data model.** Shapes are a new `ShapeNode`, separate from `FrameNode`. Frames remain navigation anchors with membership semantics; shapes are pure decorative content. Even though `ShapeNode.Rect` and `FrameNode` share geometry, conflating them muddies the model. `ShapeNode` carries a primitive enum (`Rectangle | Ellipse | Line | Arrow | Polygon | Star`) + primitive-specific params + standard `NodeAppearance`.

**Topbar settings.** Primitive picker (the six variants), fill, stroke, stroke width, primitive-specific params (corner radius, polygon sides, star inner radius), and an **aspect-ratio constraint toggle**. Modifier-free since ZoomAlboom has no keyboard — second-finger and long-press alternatives were rejected (second finger collides with global nav; long-press is reserved for popup).

**Persistence.** Stay in Shape after each drop.

### 4.4 `Text`

Tap creates a text box and immediately enters edit mode. Drag creates a text box with user-defined initial width (auto-height grows with content).

| Gesture | Result |
|---|---|
| Tap empty | Create text box, focus the field, raise IME |
| Tap existing text (in Text tool) | Enter edit mode on that text |
| Drag empty | Rubber-band a text box with explicit width; height auto-grows from content |
| Long-press | Global popup — does NOT enter edit mode |
| Two-finger | Global navigation |

**Data model.** `TextNode` — content + transform + standard `NodeAppearance`, participates in z-order. Likely already exists in the data model; verify at implementation.

**Editing surface.** Overlay `BasicTextField` positioned via `graphicsLayer` to follow the camera. While the field is focused, text input owns gestures and the canvas gesture stack defers. Tap outside the active text box unfocuses only — does not create a new text box on that same tap. Next tap creates.

**Box model (MVP).** Autosized — fixed width from creation or Selection-handle resize, height auto-grows from content. Fixed-height boxes, overflow scrolling, and text-on-path are future features.

**Topbar settings.** Font, size, color, background, stroke, shadow, alignment.

**Persistence.** Stay in Text after creation / edit-dismiss.

### 4.5 `VectorEdit`

Edits individual anchors and bezier handles within a single vector node. Active only when canvas selection holds exactly one vector node — entry is gated; no auto-filtering of selection on tool switch.

| Gesture | Result |
|---|---|
| Tap anchor | Replace anchor selection with `{anchor}` |
| Tap curve segment | Add new anchor at nearest point on curve |
| Tap empty | Clear anchor selection |
| Drag anchor | Move anchor (with its bezier handles) |
| Drag bezier handle | Edit curve — move handle only, anchor stays |
| Drag empty | Marquee-select anchors within rect |
| Double-tap anchor | Remove anchor; adjacent segments reconnect |
| Long-press on anchor / segment / empty | Global popup (adds new tool-targets per § 5) |
| Two-finger | Global navigation |

**State.** Hybrid. Canvas selection holds the *vector node* being edited; per-tool `VectorEditState.selectedAnchors: Set<AnchorId>` holds the anchor-level selection within it. Per-tool transient-state pattern from § 7.1.

**Hit testing.** Anchor and curve detection use a screen-space hit radius (a few px). Standard for editing tools at variable zoom.

**Node types.** `Smooth` / `Sharp` / `Mirrored` / `Disconnected`.

**Topbar settings.** Node-type selector (primary surface), path-level operations (close/open path, simplify, future boolean ops). Long-press popup mirrors node-type actions for one-handed reach.

**Handle visibility.** All anchor dots visible whenever the path is being edited; bezier handles visible only for selected anchors. Otherwise the screen turns into a sea of dots and lines for any non-trivial path.

**Other canvas nodes.** Remain visible during VectorEdit, not interactive. No special dimming for MVP.

**Exit.** Explicit tool switch only. Defensive auto-exit if the edited node is deleted (undo, etc.).

### 4.6 `Eraser`

One tool with internal mode toggle. MVP supports object erase and vector partial erase; raster partial erase is post-MVP.

**Object mode.** Delete whole canvas nodes.

| Gesture | Result |
|---|---|
| Tap on node | Delete that node |
| Drag across nodes | Scrub-delete — each crossed node, once per gesture |
| Tap empty | No-op |
| Long-press | Global popup — non-destructive bailout |
| Two-finger | Global navigation |

**Vector partial mode.** Erase segments within vector paths. The brush has width; the operation removes a *corridor* of path, not a single mathematical point.

| Gesture | Result |
|---|---|
| Drag across vector path | Remove path segment within eraser brush corridor; insert boundary anchors at cut edges; split path if necessary; recompute bounds |
| Other gestures | As Object mode |

Example: a horizontal line crossed by a vertical eraser becomes two separate path segments with new anchors at the corridor edges.

**Frame interaction.** Eraser on a frame in Object mode deletes the frame *only* — members stay in place. Cascading "delete frame with contents" is **not** a topbar toggle (a forgotten-while-active mode causes accidental destructive deletion). It lives as an explicit popup action under long-press on the frame.

**Topbar (MVP).** Mode selector (Object / Vector partial). Brush size meaningful only in partial mode.

**Topbar (post-MVP, when raster partial lands).** Partial target (Auto / Vector / Raster), hardness, opacity, pressure-affects-size toggle. Raster partial would write into `MediaAppearance.alphaMask` per `appearance.md § 12` (non-destructive). Auto mode picks Vector or Raster based on what the pointer hits.

**Undo granularity.** One continuous erase gesture = one Compound undo. Object scrub-delete N nodes → one entry. Vector partial erase stroke → one entry.

**Two-finger during active erase.** Finalize current gesture (commit deletions / segment removals), switch to navigation. Consistent with FreeDraw.

**Persistence.** Stay in Eraser after each erase.

### 4.7 `MaskEdit`

Creates and edits `MaskNode`s. `MaskNode` constraints (scope, frame relationship, z-order, composition, binding) are defined in [appearance.md § 12.10](appearance.md#1210-masknode-boundaries) — locked 2026-06-03 together with this gesture map.

**Selection-awareness, not selection-gating.** Unlike `VectorEdit`, this tool owns *both* the creation and the editing of its target node — there is no separate creator tool (the way `FreeDraw` / `Shape` produce vector nodes for `VectorEdit`). The tool's entry behavior therefore depends on the current selection:

| Selection on entry | Mode |
|---|---|
| Empty | Creation mode |
| Exactly one `MaskNode` | Edit mode |
| One non-`MaskNode`, or multi-selection | Disabled toolbar slot (matches `VectorEdit`'s greyed-out feel) |

Entering creation mode on empty + drawing a `MaskNode` immediately selects the new node and transitions the same tool session into edit mode. The user does not switch tools.

**Topbar — mask geometry picker.** Four primitive choices, mirroring `Shape`'s topbar:

| Primitive | Drawing gesture | Editing gesture |
|---|---|---|
| `Rect` | Rubber-band drag | Corner + edge handles |
| `Ellipse` | Rubber-band drag | Corner + edge handles |
| `Path` (anchored bezier) | Tap-to-place-anchors *or* freehand-then-simplify | Per-anchor + per-handle, like `VectorEdit` |
| `Free` (raw freehand) | Continuous sample stream | Resample as bezier, then per-anchor editing |

Tap-without-drag in creation mode drops a default-sized primitive at the tap point.

**Creation mode** (no `MaskNode` selected):

| Gesture | Result |
|---|---|
| Tap | Drop default-sized primitive (Rect / Ellipse) or single anchor (Path), select it, transition to edit mode |
| Drag (Rect / Ellipse) | Rubber-band the primitive from start to current point |
| Drag (Path) | Place anchors along the drag, line-segment preview between them |
| Drag (Free) | Continuous sample stream, finalize as smoothed bezier `Path` on lift |
| Release | Commit + select the new `MaskNode`; tool stays in `MaskEdit`, now in edit mode |
| Long-press | Global popup (per § 5) |
| Two-finger | Global navigation |

**Edit mode** (one `MaskNode` selected):

Primitive masks (`Rect` / `Ellipse`):

| Gesture | Result |
|---|---|
| Drag corner handle | Resize primitive |
| Drag edge handle | Resize on one axis |
| Drag the body | Move the `MaskNode` (same as Selection's body-drag) |
| Drag rotation handle | Rotate primitive |
| Long-press / two-finger | As above |

Path masks (`Path` / `Free` after simplification):

| Gesture | Result |
|---|---|
| Tap anchor | Replace anchor selection with `{anchor}` |
| Tap curve segment | Add new anchor at nearest point on curve |
| Tap empty | Clear anchor selection |
| Drag anchor | Move anchor (with its bezier handles) |
| Drag bezier handle | Edit curve — move handle only, anchor stays |
| Drag empty | Marquee-select anchors within rect |
| Double-tap anchor | Remove anchor; adjacent segments reconnect |
| Long-press on anchor / segment / empty | Global popup |
| Two-finger | Global navigation |

**Preview policy — commit-only.** Masked siblings re-clip on **gesture lift**, not continuously during drag. While an anchor / handle / corner is moving, the `MaskNode`'s own outline updates live (so the user sees what they're editing), but the photos / strokes / frames underneath stay in their pre-gesture clipped state until the gesture commits. Rationale: per-frame re-clipping during multi-anchor drags would force every masked sibling to re-render mid-gesture, which is both expensive and visually noisy. Locked 2026-06-03 (`to_discuss.md § 8` graduation, answer 4).

**Hit testing.** Screen-space hit radius for anchors and curve segments — same convention as `VectorEdit § 4.5`.

**Handle visibility.** Primitive corner / edge / rotation handles always visible while the `MaskNode` is selected. Path anchors all visible whenever the mask is in edit mode; bezier handles visible only for selected anchors (otherwise a complex mask becomes a sea of handles).

**Other canvas nodes.** Visible but non-interactive while `MaskEdit` is active — siblings still render through their pre-gesture mask state per the commit-only preview rule.

**Topbar settings (MVP).** Primitive picker (`Rect` / `Ellipse` / `Path` / `Free`); for primitives, aspect-ratio constraint toggle (matches `Shape`). Feather is a `MaskNode` attribute, not a topbar setting — it lives on the long-press popup along with future mask-source options (image / gradient / procedural masks per [appearance.md § 12.2](appearance.md#122-model) — post-MVP).

**Persistence.** Stay in `MaskEdit` after each commit (creation, anchor edit, or primitive resize). Explicit tool-switch exit only. Defensive auto-exit if the edited `MaskNode` is deleted (e.g. undo erases it).

**Undo granularity.** One continuous gesture = one Compound undo entry, matching the other vector tools.

**Z-order convention.** `MaskNode` clips siblings *below* it in z-order within the same frame/group only (per [appearance.md § 12.10](appearance.md#1210-masknode-boundaries)). The tool itself does not impose a z-position on new `MaskNode`s — they land on top of the current frame/group by default like any other freshly-added node, which is also the position where they affect the maximum number of siblings.

**Binding.** Implicit — every sibling within the same frame / group below the `MaskNode` in z is masked. The user does not pick per-sibling binding; placement + z-order *is* the binding (per `to_discuss.md § 8`, answer 2).

**Multi-mask composition.** When more than one `MaskNode` exists in the same frame/group, their shapes **union** (a sibling pixel is visible if **any** above-it `MaskNode` reveals it). No subtractive masks in MVP; adding a mask never hides more content.

---

## 5. Popup derivation

Long-press is the **global popup invocation gesture** — fired under every mode (except Present, where it is suppressed) and under every active tool, with no per-tool override. Tools influence the popup's *contents* through the derivation function below, never the gesture's *firing*. The dispatch rule is captured in § 7.2.

The popup's content is a pure function of four inputs:

```
popupContent = f(editorMode, activeTool, selection, objectTypes)
```

Examples:

| `(mode, tool, selection)` | Popup content |
|---|---|
| `(Edit, Selection, [Media])` | Edit appearance / crop / frame decoration / mask / overlays / transform / order / delete / duplicate |
| `(Edit, Selection, [Frame])` | Edit appearance / frame settings / navigation target / transform / order / delete |
| `(Edit, Selection, [Media, Media, …])` | Multi-edit popups for shared concepts ([appearance.md § 14](appearance.md#14-multi-selection)) |
| `(Edit, VectorEdit, [vector node])` | Node type / simplify path / close-or-open / boolean ops (when available) |
| `(Edit, Eraser, anything)` | Eraser size / hardness / mode (also lives in topbar) |
| `(View, n/a, [Frame])` | Navigate to frame; no edit options |
| `(Present, n/a, _)` | No popup — Present strips context surfaces |

**Implementation choice deferred.** Centralized pure function (`PopupActions.derive(...)`) vs. per-tool contribution (`EditorTool.contributePopupActions(...)`). Centralized is easier to reason about and test exhaustively; per-tool is easier to extend without touching a god function. Decide when 3+ tools exist and the pain of one direction shows up.

---

## 6. Persistence

| What | When | Reason |
|---|---|---|
| `editorMode` | Per-album (last mode the user was in) | Matches user intent on reopen |
| `activeTool` within a session | Persists across in-session tool switches | Standard editor behavior |
| `activeTool` across album-open / app-restart | **Resets to `Selection`** | Safe default; never opens an album already in `Eraser` / `FreeDraw` / `Shape` / `Text` |
| Selection across tool switches | Persists | Standard editor behavior; switching tools doesn't drop the current selection |

A "Restore last tool" preference is post-MVP and not part of the locked design.

---

## 7. Implementation surface

### 7.1 State

`CanvasState` (the actual class in `CanvasViewModel.kt`; older drafts of this doc called it `CanvasUiState`) is split into canvas / rendering state at the top level and an `EditorState` sub-object that owns gesture interpretation and overlay rendering:

```kotlin
data class CanvasState(
    // …canvas / rendering fields…
    val camera: Camera = Camera(),
    val visibleNodes: List<VisibleNode> = emptyList(),
    val totalNodeCount: Int = 0,
    val isLoading: Boolean = true,
    val profile: AlbumPresentationProfile? = null,
    val cameraAnimation: CameraAnimation? = null,
    val albumBackground: AlbumBackground? = null,

    val editor: EditorState = EditorState(),
)

data class EditorState(
    val mode: CanvasInteractionMode = CanvasInteractionMode.Edit,
    val activeTool: EditorTool = EditorTool.Selection,
    val selectedNodeIds: Set<String> = emptySet(),
    val selectionRect: BoundingBox? = null,
    val groupSelectionTransform: Transform? = null,
    val frameEditOptions: FrameEditOptions = FrameEditOptions(),
    val contextAnchorNodeId: String? = null,
)
```

Both types live under `feature/canvas/editor/` — editor interaction concepts, not persisted album content, so they do not belong in `domain/model/`.

**`EditorTool` vocabulary is grown incrementally.** Initial MVP exposes only `Selection` and `Eraser` (the two variants needed for the next implementation slice). `FreeDraw`, `Shape`, `Text`, `VectorEdit`, `MaskEdit` are added one variant at a time as each tool actually ships. Disabled toolbar slots are not pre-declared in the type.

**Three deliberate separations on `EditorState`:**

1. **Tool identity vs. tool settings vs. active interaction.** When per-tool settings or in-progress gesture state become necessary (e.g. Eraser brush size, FreeDraw current stroke), add dedicated `toolSettings` / `activeInteraction` fields on `EditorState`. Do **not** encode them inside `EditorTool` variants:
   ```kotlin
   // wrong — conflates identity, settings, and active gesture state
   data class Eraser(val size: Float, val currentlyErasedNodeIds: Set<String>) : EditorTool
   ```
2. **No speculative state.** Do not predeclare `vectorEditState`, `freeDrawState`, `eraserState`, `snappingState`, `transformState`, `toolSettings`, or `activeInteraction` until an implemented feature requires them.
3. **UI-surface state stays in `CanvasScaffold`.** Open popup / appearance-editor / bottom sheet / dialog state (`mediaApprEditing`, `frameBgEditing`, `contextMenuRequest`, `showAddSheet`, `showFrameList`, `showPanelConfig`, `showAlbumSettings`) is presentation-surface state — phone today is bottom sheets, tablet later is docked panels. Keeping it out of `EditorState` decouples editor semantics from the presentation surface used to render them.

**`contextAnchorNodeId` is an intentional exception** to (3): the popup itself is UI-surface state, but the anchor id is consumed by canvas rendering (`SelectionOverlay` halo). It belongs on `EditorState`.

**Selection sub-object deferred.** Selection fields are flat on `EditorState` today. A dedicated `SelectionState` will be extracted when `SelectionTool` accumulates substantial own state — selection mode (rectangle / lasso), selection rule (`Intersects` / `FullyContained`), in-progress lasso path, additive flag, hover state, or anchor-level selection. Until then, the cohesion gain doesn't justify the extra navigation depth at every call site.

**Earlier guidance superseded.** A previous version of this doc said "factor `EditorState` only once 3+ tools accumulate per-tool transient state." `EditorState` was extracted earlier — before any per-tool transient state existed — because the editor-session subset of `CanvasState` (mode + selection + frame-edit options + context anchor) was already coherent on its own, and the extraction stays cheaper to do now than after multiple tool slices couple to a flat shape.

### 7.2 Gesture stack

The existing three-layer stack in [selection.md § 5](selection.md#5-gesture-stack) carries the framework cleanly:

- **Layer 1** (`nodeInteractionGestures`) — selected-node interactions. Tool-agnostic today; only `Selection` populates the selected set. Future tools that need selected-node manipulation plug in here.
- **Layer 2** (`tapAndLongPressGestures`) — mixed dispatch by gesture type:
  - **Tap** is tool-dispatched: `activeTool.onTap(...)` per the per-tool maps in § 4. Each tool has its own tap behavior.
  - **Long-press** is **not** dispatched to the active tool. It is the universal popup invoker — any long-press resolves a target and opens the popup, with contents derived per § 5. The active tool influences popup *contents*, never whether long-press fires. View mode opens view-mode popups; Present mode suppresses long-press entirely. This keeps long-press predictable and avoids every tool inventing its own long-press behavior.
  - **Double-tap** is tool-aware but only used by tools that explicitly define it (currently only `VectorEdit` for remove-anchor); others no-op.

  Per-tool drag (FreeDraw stroke, Shape rubber-band, Eraser scrub, etc.) is dispatched separately — each tool's input handler owns its drag detection. Selection's drag-on-empty (marquee) and drag-on-node (move) live in Layer 1 plus the new SelectionTool drag handler (see § 7.3).
- **Layer 3** (`infiniteCanvasGestures`) — two-or-more-finger camera control. Already mode-agnostic, already tool-agnostic. **No change needed in Edit mode.** For View mode, single-finger pan needs a new handler (Layer 3 today is two-or-more-finger only).

Layer 3 is the win: the "global navigation" axis is already in code. The work is making Layer 2's tap and double-tap dispatch tool-aware (long-press stays centralized through § 5) and adding per-tool drag handlers where needed.

### 7.3 Drag-on-empty migration

Today's marquee path: long-press-on-empty + drag inside `tapAndLongPressGestures`. The locked model: drag-on-empty inside `SelectionTool`'s input handler, no long-press required.

Migration:

- Remove the long-press-then-drag path in `tapAndLongPressGestures` (or gate it on `activeTool != Selection` as a transitional step).
- Add a single-finger-drag-on-empty detector inside `SelectionTool`'s gesture handler.
- Long-press-on-empty becomes a no-op for MVP (an empty-canvas context menu can come later — see § 8).
- Update [selection.md § 2](selection.md#2-gesture-mapping) gesture table.

### 7.4 View-mode single-finger pan

A new handler distinct from Layer 3. Options:

- Extend Layer 3 to accept one finger when `editorMode == View`.
- Add a dedicated `viewModePanGestures` modifier active only in View.

The first is one branch; the second is one composable. Pick at implementation time. Either way the behavior is identical to Edit mode's two-finger pan, just with a one-finger trigger.

### 7.5 `EditorMode` toggle

Today's `Edit ↔ View` TopBar toggle becomes the `EditorMode` selector. Present remains separate — its trigger location is deferred to whoever ships the Present surface (likely a fullscreen / play action somewhere in the TopBar).

---

## 8. Open Questions

- ~~`MaskEdit` gesture map.~~ **Resolved 2026-06-03.** Locked in § 4.7. `MaskNode` constraints in [appearance.md § 12.10](appearance.md#1210-masknode-boundaries).
- **Popup derivation: centralized vs distributed.** § 5. Defer until 3+ tools are implemented and one pattern's pain shows up.
- **Long-press-on-empty intent.** No defined behavior in the locked Selection map (§ 4.1). Options: no-op (MVP), empty-canvas context menu (paste, add node, canvas settings), or "add content" picker. Defer.
- **`VectorEditTool` invalid-selection UX.** Tool requires exactly-one-vector-node selected (§ 4.5). With invalid selection: greyed-out toolbar slot (can't enter), enterable-but-disabled with hint, or auto-prompt? Defer to first-use UX feedback.
- ~~`EditorState` refactor trigger.~~ **Resolved 2026-06-02.** `EditorState` was extracted before the first per-tool slice; see § 7.1 for the layered shape and the criterion for a later `SelectionState` extraction.

---

## 9. Non-goals

- Single-finger pan fallback in Edit mode. Predictability over convenience.
- Present as a third position on the View/Edit toggle. Present is "enter from anywhere."
- Per-tool camera control. Camera always belongs to Layer 3.
- Auto-filtering selection on tool switch. Selection persists as-is; tool-specific operations check the selection and may be disabled.
- "Restore last tool" preference across app restarts. Post-MVP if at all.

---

## 10. Implementation order

The framework is largely a paper architecture until additional tools ship. The MVP work is small.

1. **Extract `EditorState`** (shipped 2026-06-02). Declare `EditorState` + `EditorTool` under `feature/canvas/editor/`; nest `mode`, `selectedNodeIds`, `selectionRect`, `groupSelectionTransform`, `frameEditOptions`, `contextAnchorNodeId`, and the new `activeTool` under `CanvasState.editor`. Behavior-preserving. See § 7.1 for the shape.
2. **Grow `EditorTool` vocabulary as tools ship.** Starts with `Selection` and `Eraser`. Add `FreeDraw`, `Shape`, `Text`, `VectorEdit`, `MaskEdit` one variant at a time when each tool actually lands — do not predeclare disabled toolbar slots. `CanvasAction.SetActiveTool(tool)` + handler land alongside the extraction (no UI exposure until the second tool ships). Selection persists across tool switches by construction (no per-switch clear).
3. **Make Layer 2 tool-aware.** `tapAndLongPressGestures` consults `activeTool` for tap / long-press dispatch. For MVP, only `Selection` has logic; other tools are no-ops.
4. **Migrate rect-select to drag-on-empty** (§ 7.3). Inside `SelectionTool`'s gesture handler, single-finger drag on empty (no long-press required) initiates `SelectNodesInRect`. Update [selection.md § 2](selection.md#2-gesture-mapping).
5. **View-mode single-finger pan** (§ 7.4). Either extend Layer 3 or add a `viewModePanGestures` modifier active only in View.
6. **Confirm `editorMode` plumbing.** Today's `CanvasInteractionMode` covers View / Edit / Presentation in the type but only View ↔ Edit in the UI toggle. Confirm wiring; rename to `EditorMode` only if it reduces confusion.
7. **`ToolControlBar` (renders `ToolControlSurface`)** — lazy. The bar doesn't render while only `Selection` is implemented and has no editable user-facing controls. Trigger to ship is either a second functional tool (expected first case: Object-mode `Eraser`) or `Selection` gaining real settings. See [editor-surfaces.md § 4](editor-surfaces.md#4-toolcontrolsurface--design).
8. **Per-tool implementations** land one tool at a time, each gated on the data-model concept it depends on:
   - `FreeDraw` (§ 4.2) depends on `StrokeNode` data-model + sample-stream input pipeline + bezier render-path cache.
   - `Shape` (§ 4.3) depends on `ShapeNode` data-model + rubber-band drag input.
   - `Text` (§ 4.4) depends on `TextNode` (verify exists) + overlay `BasicTextField` integration.
   - `VectorEdit` (§ 4.5) depends on vector-node existence (from `FreeDraw` / `Shape`) + `VectorEditState` per-tool state.
   - `Eraser` (§ 4.6) — object mode is trivial after step 3; vector partial mode depends on `VectorEdit`'s path-splitting math.
   - `MaskEdit` (§ 4.7) depends on `MaskNode` data-model (per [appearance.md § 12.10](appearance.md#1210-masknode-boundaries)) — gesture map locked, ships once `MaskNode` lands per `appearance.md § 12.8`.

Steps 4 and 5 are the only behavior-changing items in the framework batch. Steps 1-3 are additive type / field declarations that activate Selection-as-tool without removing anything.
