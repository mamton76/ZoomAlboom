# Editor Tools

> Related: [editor-surfaces](editor-surfaces.md) | [selection](selection.md) | [overview](overview.md) | [context-menu](context-menu.md) | [presentation-profile](presentation-profile.md) | [navigation](navigation.md) | [data-model](data-model.md)

This doc owns the **gesture-allocation** axis of the editor (three-axis interaction model, per-tool gesture maps, dispatch, state shape). The **UI rendering** of tools and selection actions — `ToolControlSurface`, `SelectionActionSurface`, `ConceptEditorSurface`, `AddContentSurface`, `GlobalChromeSurface` — lives in [editor-surfaces.md](editor-surfaces.md). The two docs are complementary: tools define what gestures *mean*; surfaces define how the user *reaches* them.

**Status — decided 2026-05-24, fully decided 2026-06-03; `CropEdit` locked 2026-06-06 and shipped 2026-06-07; `CropEdit` invariant + undo semantics revised 2026-06-17 (settles `to_discuss.md § 15`).** Settles `to_discuss.md § 3` (active-tool framework), § 4 (per-tool gesture maps), § 6 (Eraser modes), the `MaskEdit` portion of § 8 (`MaskNode` editing UX), and the in-canvas crop-handle UX previously deferred in [media-appearance.md § Implementation status](media-appearance.md#implementation-status). All eight tools' gesture maps are now locked; `CropEdit` is the first context-gated tool to ship end-to-end (renderer + handles + topbar + pinch + cancel + Selection-tool resize compensation). The `CropEdit` stabilization decisions (snapshot-bound invariant, session-compound undo) are in § 4.8 **Persistence + invariant** and **Undo granularity**; code is tracked in [todo.md § 20.9](../todo.md#209-cropedit-stabilization-invariant--session-compound-undo).

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
| `CropEdit` | Pan / zoom source pixels behind a media node's viewport (the node's world rect) and resize the viewport | § 4.8 (locked) | Aspect-ratio lock toggle; source-zoom slider; reset |

`VectorEdit`, vector-partial-`Eraser`, and `MaskEdit` depend on data-model concepts not yet implemented (vector nodes, `StrokeNode`, `ShapeNode`, `MaskNode`). `CropEdit` depends only on the `CropMode.Manual` renderer landing in the same slice — no new data-model concept.

`EditorTool` is **flat** — brush presets, shape primitives, eraser modes, and mask geometries are *settings of a tool*, not separate tool identities. `VectorEdit`, `MaskEdit`, and `CropEdit` are **context-gated**: they exist in the type vocabulary but don't appear as permanent entries in the primary tool selector — they're entered when a valid target is selected (exact selector discoverability UX is deferred). The user-facing rendering of tool selection + tool controls is the `ToolControlSurface` — see [editor-surfaces.md § 4](editor-surfaces.md#4-toolcontrolsurface--design).

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

**Object mode — shipped 2026-06-04.** Delete whole canvas nodes.

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

**Undo granularity.** Per-deletion. Object mode commits one undo entry per crossed node — symmetric with tap-on-node Eraser (each tap = one entry), and consistent with how the rest of the editor treats discrete deletes. An N-node scrub produces N undo steps. (Earlier drafts of this section called for "one continuous gesture = one Compound undo"; that was relaxed when Object-mode shipped to avoid the begin / per-node-buffer / commit ceremony on the ViewModel. Vector partial erase will revisit when it ships — it's a single continuous-path edit, not N discrete deletes, so a Compound entry is the natural fit there.)

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

### 4.8 `CropEdit`

Edits the manual crop placement of a single media node — pans / zooms the source pixels behind the media's world rect (the "viewport"), and resizes the viewport itself via corner / edge handles. Mutates `MediaAppearance.crop.{offsetX, offsetY, zoom}` and the media node's `Transform`.

**Conceptual split from `Selection`.** Both tools resize via corner handles, but with different intents:

- **`Selection` resize** = "make this whole object bigger/smaller, same content visible." For media in Manual crop, `ResizeSelection`'s handler scales `crop.{offsetX, offsetY}` by the same `factor` so the visible source portion stays identical — just rendered larger or smaller. `crop.zoom` is unchanged (`fillScale` auto-scales by `factor` when the rect scales uniformly).
- **`CropEdit` resize** = "edit the crop window, reveal more / less of the source." `applyMediaCornerEdgeResize` compensates `crop` so the source draw rect stays anchored in *world* coords; the rect grows around stationary source pixels, so a bigger rect uncovers more image.

Same underlying `Transform` mutation; different `MediaAppearance.crop` compensation. The user opts into the `CropEdit` semantic by entering the tool. Rotation is **not** a `CropEdit` concern (see below).

**Model A — viewport + source pan/zoom, no new `cropRect` field.** ZoomAlboom's crop model treats the media node's world rect as a fixed-aperture viewport; the source pans / zooms behind it via the existing `CropSettings.offsetX / offsetY / zoom`. `CropEdit` drives those values plus the viewport's `Transform`. No source-space `cropRect` is introduced.

**Selection-awareness.** Entered only when exactly one media node is selected; auto-exits to `Selection` if the selection becomes empty, multi-select, non-media, or if the selected media is deleted (e.g. via undo). Selection itself persists across the auto-exit per § 6.

| Selection on entry | Result |
|---|---|
| Exactly one `Media` | Edit mode |
| Anything else | Tool entry rejected; defensive auto-exit if shape changes mid-session |

**Discoverability — context-menu only for MVP.** `✂ Edit crop` on the long-press popup for a single-media selection dispatches `CanvasAction.SetActiveTool(CropEdit)` (replacing the prior `EditorActionEffect.OpenCropEditor → sheet` path; the sheet wiring is preserved unwired for a future "Crop mode…" popup item that re-exposes Fit / Fill / Stretch picking). Not exposed in the primary tool selector; the `CropEdit` slot in `EditorTool` is type-only at the toolbar level. The Fit / Fill / Stretch picker has no surface in v1 — once a node enters Manual via `CropEdit`, it stays in Manual until that follow-up ships.

**Entry — mode coupling and visual continuity.**

Entering `CropEdit` snaps `crop.mode` to `Manual` if it isn't already. **`offsetX / offsetY / zoom` are not rewritten on entry** — whatever the node already carries is preserved, including the defaults `(0, 0, 1)` for nodes that have never been in Manual.

`zoom = 1` is defined as **the Fill scale** (source scaled to cover the viewport on its longer axis). With this convention:

| Pre-entry `mode` | Behavior at entry | Continuity |
|---|---|---|
| `Manual` | Values already authoritative; nothing rewritten | Lossless |
| `Fill` (default focal point) | Default Manual values `(0, 0, 1)` render identically to Fill | Lossless |
| `Fill` (focal point ≠ centered) | Default Manual values render Fill **centered** — focal-point shift is lost in v1 | Approximate; one-frame snap to centered Fill |
| `Fit` | Default Manual values render as Fill (filled, not letterboxed) — one-frame snap, user re-tunes via the source-zoom slider to recover Fit framing | Approximate; one-frame snap |
| `Stretch` | Cannot be exactly preserved (Manual keeps source aspect, Stretch ignores it). Default Manual values render as Fill (centered, aspect-preserved) | Approximate; one-frame snap |

The pre-entry mode is not recorded. Exiting `CropEdit` (tool-switch back to `Selection`) leaves the node in `Manual`; the user re-picks Fit / Fill / Stretch from the appearance sheet if they want a different framing rule.

> **Seed-math note.** A precise per-mode seed (focal-aware Fill, Fit zoom = `min/max(rw/srcw, rh/srch)`) is possible but requires the source asset's intrinsic size at action-handler time. That cache is post-v1; the simpler "snap mode, keep values" rule above is what ships. Document any tighter seeding in the slice that adds an intrinsic-size cache.

**Gesture map.**

| Gesture | Result | Primary mutation |
|---|---|---|
| Drag inside the viewport (the media rect, off any handle) | Pan source under the viewport | `offsetX`, `offsetY` |
| Drag a corner handle | Resize the viewport from that corner | Media node `Transform.{w, h, cx, cy}` + crop compensation so the source draw rect stays anchored in world coords |
| Drag an edge handle | Resize the viewport on one axis | Media node `Transform.{w, h, cx, cy}` (one axis) + crop compensation |
| Two-finger pinch | Centroid-anchored source pan + zoom — `crop.zoom *= zoomFactor`, offset adjusted so the source pixel under the centroid stays under the centroid, then offset shifted by the screen pan delta | `offsetX`, `offsetY`, `zoom` |
| Tap on empty | No-op | — |
| Tap on the viewport (without a drag) | No-op | — |
| Long-press | Global popup (per § 5) | — |

**Aspect-ratio lock.** Topbar toggle. Defaults **on**. Implemented by projecting the world-space drag delta onto the dragged corner's diagonal in node-local coords before dispatch — the same underlying resize action (`ResizeMediaFreeCorner`) runs in both locked and free modes, so the source-stability compensation always fires. Edge handles ignore the toggle (one-axis). Matches `Shape` and `MaskEdit`'s aspect-lock UX.

**No rotation handle in v1.** The media node's `Transform.rotation` is untouched by `CropEdit`. To rotate the whole node, the user switches back to `Selection`. Source-pixel rotation independent of the viewport (a hypothetical `crop.rotation` or source-orientation handle) is out of scope.

**Two-finger pinch in `CropEdit` overrides the global-nav rule.** The locked two-finger = global navigation rule (§ 1.3) is suspended *only* while `CropEdit` is the active tool: pinch drives source pan/zoom around the centroid, not camera. Rotation component is dropped (source rotation out of scope). Camera nav resumes when the user leaves the tool.

**Source zoom — pinch + topbar slider.** Two-finger pinch is the primary in-canvas zoom gesture. The topbar carries an additional slider for `crop.zoom`, range `[MIN_MANUAL_ZOOM, MAX_MANUAL_ZOOM]` (same bounds as the legacy sheet slider), for users who prefer slider control.

**Reset.** Topbar button. Sets `offsetX = offsetY = 0`, `zoom = 1` — the default Manual values, which render as centered Fill under the v1 convention. The deeper "reset to Fit-equivalent" requires source intrinsic size and lands together with the intrinsic-size cache.

**Cancel.** Topbar button. Restores the edited media's `Transform` + `MediaAppearance` to the snapshot captured at `CropEdit` entry, then exits to `Selection`. Snapshot lives in `EditorState.cropEdit.entrySnapshot` (session-only, not persisted; cleared on tool change or auto-exit). Under the session-compound undo model (see **Undo granularity** below), Cancel restores the entry snapshot and pushes **nothing** — the session never committed an entry, so the undo stack is left untouched (no orphan entries). *(Revised 2026-06-17, superseding the earlier v1 caveat where per-gesture entries were left on the stack after Cancel.)*

**Apply / Leave.** Topbar button. Exits to `Selection` without altering current state — opposite of Cancel.

**Topbar settings (MVP).** Aspect-ratio lock toggle, source-zoom slider, Reset, Cancel, Apply. Aspect-ratio preset chips (1:1, 4:3, 16:9) are deferred unless trivial in the host topbar component.

**Preview policy — live.** The selected media re-renders continuously during pan / resize / slider drag. Only one node redraws per frame, no offscreen layers required — unlike `MaskEdit § 4.7`'s commit-only rule (which exists because re-clipping N siblings per frame would be expensive). No cross-node cost here.

**Composition.** Manual crop slots into the existing per-media pipeline at the source-placement step ([media-appearance.md § Rendering Pipeline (per media node)](media-appearance.md#rendering-pipeline-per-media-node)):

1. Source pixels are placed at `offsetX / offsetY` and scaled by `zoom`, clipped to the viewport (the media rect).
2. `colorAdjustments` (when shipped) apply to the placed pixels.
3. `overlays` composite above, bounded by the viewport.
4. `frameDecoration` draws on top.
5. `cornerRadius`, `border`, `shadow`, surface `opacity` apply unchanged.
6. `appearance.alphaMask` (when shipped per [appearance.md § 12](appearance.md#12-proposed-evolution--appearance-layers)) clips to the rendered output the same way it does today.

Rounded corners, alpha masks, borders, and shadows clip the rendered viewport — not the source. Manual crop slides source pixels within a fixed clip region.

**Hit testing.** Screen-space hit radius for corner and edge handles — same convention as `VectorEdit § 4.5` and `MaskEdit § 4.7`. Any pointer-down inside the viewport that isn't on a handle initiates a pan.

**Handle visibility.** Eight handles (four corners + four edges) drawn whenever `CropEdit` is the active tool. Screen-space-sized (constant pixel size at all zooms), same as `Selection`'s. No rotation handle.

**Other canvas nodes.** Visible but non-interactive while `CropEdit` is active. Tap / drag on a non-selected node does nothing; the user switches back to `Selection` (or `EditorMode = View`) to interact with them.

**Persistence + invariant (revised 2026-06-17).** Stay in `CropEdit` after each commit; explicit tool-switch exit only. The invariant is stronger than "exactly one media is selected" — `CropEdit` is bound to **the specific node captured in `entrySnapshot`**:

```
CropEdit can be active only when the selection is exactly the one media node
recorded in EditorState.cropEdit.entrySnapshot.
```

`enforceCropEditInvariant()` exits to `Selection` and drops the snapshot whenever this breaks. It must run on every transition that can break it:

- **selection empties / goes multi / a frame is selected** — already wired (`DeselectAll`, `recomputeGroupTransform`);
- **the edited media is deleted** — already wired (`deleteNodesById`);
- **selection moves to a *different* single media** — exit when `selectedMediaId != entrySnapshot.nodeId` (otherwise Cancel would restore the wrong node, and the new node has no snapshot). *Re-anchoring* — committing the current session and re-capturing a snapshot for the new node — is a deliberate future UX, not part of this invariant;
- **`EditorMode` switches to View / Present** — `SetMode` must run the invariant (and tool resets to `Selection` on Edit re-entry);
- **Undo / Redo** changes selection or removes the edited media — run the invariant after the command applies.

**Undo granularity — session-compound (revised 2026-06-17).** A whole `CropEdit` session is **one** `Compound` undo entry, consistent with the per-popup-session compound-undo convention ([appearance.md § 14.6](appearance.md#146-action-dispatch-and-undo)):

- Per-gesture history is **suppressed** while `CropEdit` is active — individual pan / handle-drag / pinch / slider / Reset operations do **not** each push an entry.
- The single entry is pushed on **Apply / exit** (entry-snapshot → final state).
- **Cancel pushes nothing** — it restores the entry snapshot and leaves the stack clean.
- In-session crop operations are therefore **not** individually undoable; they collapse into the session command.

*(This supersedes the earlier model where each gesture committed its own entry and Cancel left those intermediate entries on the stack.)*

**Data-model dependency.** None new. Uses `MediaAppearance.crop: CropSettings` as it stands. Ships together with the `CropMode.Manual` renderer — see [media-appearance.md § Implementation status](media-appearance.md#implementation-status) and [todo.md § 20.8](../todo.md#208-cropedit-slice--manual-renderer--in-canvas-handles).

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
| `(Edit, CropEdit, [Media])` | Reset crop / Cancel / Apply. No mode picker here — Fit / Fill / Stretch picking has no surface in v1; the future "Crop mode…" popup item is the planned landing spot. |
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

### 7.2 Gesture stack and the `GestureRouter`

Two layers, kept deliberately separate:

1. **Pointer-level detectors** — `nodeInteractionGestures`, `tapAndLongPressGestures`, `infiniteCanvasGestures`. They own touch slop, multi-finger arbitration, and event-pass consumption. They do **not** decide what a gesture *means*.
2. **Semantic routing** — `GestureRouter` (in `feature/canvas/editor/gestures/`). All recognized gestures pass through it; it returns a typed route the caller translates into a `CanvasAction` dispatch / overlay update. Pure / testable, no Compose or ViewModel coupling.

```
pointer detectors  →  recognized semantic gesture  →  GestureRouter  →  action/effect owner
```

The router is the single place mode / tool / popup-open / selection rules are encoded. Per-tool `when` branches never live in `CanvasScreen` or in detector callbacks — that lock-in would scale poorly as the tool vocabulary grows.

#### Routing policy

| Recognized gesture | Routed via | Notes |
|---|---|---|
| Tap | `GestureRouter.routeTap(ctx, hitNodeId)` | Tool-dispatched in Edit; mode-dispatched in View / Presentation. |
| Double-tap | `GestureRouter.routeDoubleTap(ctx)` | Tool-aware in Edit; mode-level reset-camera affordance in View / Presentation. No MVP tool currently claims double-tap (`VectorEdit` will, when it lands). |
| Long-press (press + lift) | `GestureRouter.routeLongPress(ctx, hitIds)` + `routeLongPressLift(ctx, pressRoute)` | **Owned by global popup policy.** Long-press is never delegated to the active tool: the universal popup invocation gesture in Edit (and, when the view-mode popup ships per § 5 + § 8 open question, in View as well). Active tool influences derived popup *contents*, never whether long-press fires. Suppressed in Presentation. Today's router also suppresses View as a behavior-preserving step; see § 8. |
| Selected-node transform start (move / resize / rotate) | `GestureRouter.routeSelectedNodeTransformStart(ctx)` | Allowed only in `Edit + Selection`. Selection persists across tool switches, so a stored selection alone is not sufficient permission — Eraser-active and View / Presentation must block. |
| Camera multi-finger transform | `GestureRouter.routeCameraTransformStart(ctx)` | Always allowed (global navigation, § 1.3). Returns `DismissContextMenuAndProceed` when the popup is open so the camera intent isn't lost on dismiss. |

#### Per-detector mapping

- **`nodeInteractionGestures`** — pointer-level detector for selected-node body / resize-handle / rotation-handle drag. Stays unchanged. Its `selectedNodeIds` input is fed by the routing layer: when `routeSelectedNodeTransformStart` returns `Block`, the call site passes `emptySet()` so the detector short-circuits at its `pointerInput(selectedNodeIds)` keying and events flow through to lower layers untouched. Selection state is unaffected — only the transform's interactivity is gated.
- **`tapAndLongPressGestures`** — pointer-level detector for tap / double-tap / long-press (and the transitional long-press-drag rect-select path). Each callback consults the router and translates the typed route into a `CanvasAction`. The detector itself contains no mode or tool checks.
- **`infiniteCanvasGestures`** — pointer-level detector for two-or-more-finger camera transform. Already mode-agnostic, already tool-agnostic in Edit. View-mode single-finger pan needs a new handler (Layer 3 today is two-or-more-finger only) — see § 7.4.

#### Per-tool drag

Per-tool drag (FreeDraw stroke, Shape rubber-band, Eraser scrub, etc.) is dispatched separately — each tool's input handler owns its drag detection and reaches the router only at gesture-start to confirm ownership. Selection's drag-on-empty (marquee) and drag-on-node (move) live inside `nodeInteractionGestures` plus the SelectionTool drag handler that lands with § 7.3.

### 7.3 Drag-on-empty migration — shipped 2026-06-04

Marquee selection moved off `tapAndLongPressGestures` (long-press-then-drag) onto a dedicated `selectionMarqueeGestures` modifier that recognises drag-on-empty directly. `LongPressRoute.FallThroughToRectSelectDrag` was retired; long-press-on-empty is now `Suppress` in every Edit tool. Marquee state is stored in **screen coordinates** so the rectangle stays axis-aligned to the screen under camera rotation — intersection uses `TransformUtils.toScreenBoundingBox` per node. [selection.md § 2](selection.md#2-gesture-mapping) and § 5 reflect the gesture-stack changes. Empty-canvas context menus remain future work (§ 8).

### 7.4 View-mode single-finger pan — shipped 2026-06-05

Resolved with a dedicated `viewModePanGestures` modifier (Layer 2d in the gesture stack) rather than extending Layer 3, matching the per-mode/per-tool detector pattern already used by `selectionMarqueeGestures` (Layer 2a) and `eraserScrubGestures` (Layer 2c). Each detector is single-purpose and gated via `enabled = (router.route...(ctx) != Suppress)`; `infiniteCanvasGestures` (Layer 3) stays multi-finger-only and mode-agnostic.

Router: `routeViewPanStart(ctx) → Allow | DismissContextMenuAndProceed | Suppress`. `Allow` in View + popup closed; `DismissContextMenuAndProceed` in View + popup open (same continuous-gesture dismissal rule used by Layer 3 camera nav); `Suppress` in Edit (single-finger reserved for active tool per § 2) and Presentation (separate gesture vocabulary).

The pan behaves identically to Edit's two-finger pan — pure translation, no zoom, no rotation — just with a one-finger trigger. Camera dispatches `viewModel.onGesture(centroid = Offset.Zero, pan, zoom = 1, rotation = 0)` per movement event.

### 7.5 `EditorMode` toggle

Today's `Edit ↔ View` TopBar toggle becomes the `EditorMode` selector. Present remains separate — its trigger location is deferred to whoever ships the Present surface (likely a fullscreen / play action somewhere in the TopBar).

---

## 8. Open Questions

- ~~`MaskEdit` gesture map.~~ **Resolved 2026-06-03.** Locked in § 4.7. `MaskNode` constraints in [appearance.md § 12.10](appearance.md#1210-masknode-boundaries).
- ~~`CropEdit` gesture map + in-canvas crop handle.~~ **Resolved 2026-06-06.** Locked in § 4.8. Model A (viewport + source pan/zoom, no new source-space `cropRect` field). Ships in one slice together with the `CropMode.Manual` renderer per [todo.md § 20.8](../todo.md#208-cropedit-slice--manual-renderer--in-canvas-handles).
- **Popup derivation: centralized vs distributed.** § 5. Defer until 3+ tools are implemented and one pattern's pain shows up.
- **Long-press-on-empty intent.** No defined behavior in the locked Selection map (§ 4.1). Options: no-op (MVP), empty-canvas context menu (paste, add node, canvas settings), or "add content" picker. Defer.
- **View-mode long-press popup is designed but unwired.** § 5 lists `(View, n/a, [Frame])` popup contents (Navigate to frame; no edit options). `GestureRouter` currently routes all View-mode long-presses to `Suppress` to preserve pre-router behavior — combining the routing extraction with a UX behavior change was explicitly avoided. The view-mode popup ships when the `(mode, tool, selection) → popupContent` derivation in § 5 lands; until then View users reach frames via tap-to-focus and the View top bar.
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
3. **Introduce `GestureRouter` and route all recognized gestures through it** (§ 7.2). One semantic routing layer for tap / double-tap / long-press (press + lift) / selected-node transform start / camera transform. Behavior-preserving for the currently shipped behavior; adds mode-aware blocking of selected-node transform under non-Selection tools and non-Edit modes (required correctness before Eraser ships). After this lands, `tapAndLongPressGestures` and `nodeInteractionGestures` callbacks consult only the router — no inline mode / tool branching. Long-press is owned by global popup policy and never delegated to the active tool; tools influence popup *contents* via § 5 only.

   Then **apply the per-tool tap / double-tap semantics from § 4**. For MVP, `Selection` keeps current select / deselect; `Eraser` tap deletes the hit node (Object-mode); double-tap is no-op in Edit (per the locked per-tool maps) and stays as the reset-camera affordance in View / Presentation.
4. **Migrate rect-select to drag-on-empty** (§ 7.3). Inside `SelectionTool`'s gesture handler, single-finger drag on empty (no long-press required) initiates `SelectNodesInRect`. Update [selection.md § 2](selection.md#2-gesture-mapping).
5. **View-mode single-finger pan** (§ 7.4). Either extend Layer 3 or add a `viewModePanGestures` modifier active only in View.
6. **Confirm `editorMode` plumbing.** Today's `CanvasInteractionMode` covers View / Edit / Presentation in the type but only View ↔ Edit in the UI toggle. Confirm wiring; rename to `EditorMode` only if it reduces confusion.
7. **`ToolControlBar` (renders `ToolControlSurface`)** — lazy. The bar doesn't render while only `Selection` is implemented and has no editable user-facing controls. Trigger to ship is either a second functional tool (expected first case: Object-mode `Eraser`) or `Selection` gaining real settings. See [editor-surfaces.md § 4](editor-surfaces.md#4-toolcontrolsurface--design).
8. **Per-tool implementations** land one tool at a time, each gated on the data-model concept it depends on:
   - `FreeDraw` (§ 4.2) depends on `StrokeNode` data-model + sample-stream input pipeline + bezier render-path cache.
   - `Shape` (§ 4.3) depends on `ShapeNode` data-model + rubber-band drag input.
   - `Text` (§ 4.4) depends on `TextNode` (verify exists) + overlay `BasicTextField` integration.
   - `VectorEdit` (§ 4.5) depends on vector-node existence (from `FreeDraw` / `Shape`) + `VectorEditState` per-tool state.
   - `Eraser` (§ 4.6) — object mode **shipped 2026-06-04**: `TapRoute.EraserDeleteNode` + `routeEraserScrubStart`, dedicated `eraserScrubGestures` detector (Layer 2c), `CanvasAction.DeleteNodes(ids)` with selection-pruning semantics, one `CanvasCommand` per gesture. Vector partial mode depends on `VectorEdit`'s path-splitting math.
   - `MaskEdit` (§ 4.7) depends on `MaskNode` data-model (per [appearance.md § 12.10](appearance.md#1210-masknode-boundaries)) — gesture map locked, ships once `MaskNode` lands per `appearance.md § 12.8`.
   - `CropEdit` (§ 4.8) depends only on the `CropMode.Manual` renderer landing; no new data-model. Bundled with the renderer as one slice — see [todo.md § 20.8](../todo.md#208-cropedit-slice--manual-renderer--in-canvas-handles). Replaces the `EditorActionEffect.OpenCropEditor → sheet` flow on the popup's `✂ Edit crop` item with `CanvasAction.SetActiveTool(CropEdit)`.

Steps 4 and 5 are the only behavior-changing items in the framework batch. Steps 1-3 are additive type / field declarations that activate Selection-as-tool without removing anything.
