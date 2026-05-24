# ZoomAlboom — Open Design Discussions

> This file is for **unresolved** design questions only. Reconciled topics live in `docs/architecture/`.

---

## 1. Disable "Add" (FAB / etc.) while a selection or popup is active?

The `+` FAB in `CanvasScaffold` is always visible; tapping it opens `AddContentBottomSheet` (Photo / Frame). Question: should we hide or disable it under either of these states?

| State | Argument for disabling Add | Argument against |
|---|---|---|
| **A selection exists** (one or more nodes selected) | The user is in an "edit existing content" mental mode; adding new content mid-edit can feel intrusive. The ContextualActionBar takes over the bottom strip when there's a selection — having two competing affordances (action bar + FAB) is busy. | Legitimate workflow: select some photos, decide you want one more, hit Add. Disabling forces a deselect-add-reselect dance. |
| **Context menu popup is open** | The popup is modal-in-spirit. Other canvas chrome is also suppressed. The FAB sitting on top of the popup looks visually noisy. | The popup is dismissed by tap-outside (per `context-menu.md § 3` dismissal rules), so the FAB is already effectively unreachable without dismissing first. Disabling is belt-and-braces. |
| **Both** (selection + popup) | Same as above, combined. | Same as above, combined. |

Open sub-questions:

- **Disable vs. hide.** Disable (grayed FAB) is more discoverable — user can see "Add exists but not now"; hide is cleaner visually but less obvious about *why* it's gone. iOS/Material conventions usually disable; Figma usually hides during modal interactions.
- **Just the FAB, or also other top-level chrome?** Top bar's "Frame List" button, mode toggle, undo/redo — should any of those be disabled while popup is open? They're currently always available.
- **What about the inverse?** When the FAB is tapped (sheet opens), should the popup close? (Probably yes — but worth confirming.)

My instinct: **don't disable the FAB on selection alone** (legitimate workflow), **dismiss the popup if the user taps the FAB while it's open** (treat as outside-tap), **leave FAB visible during popup** (already unreachable without dismissing).

But it's a UX call — flagging for explicit decision.

---

## 2. Migrate remaining `ContextualActionBar` actions into the popup

The popup currently exposes a subset of what `ContextualActionBar` does. The bar still uniquely owns:

- **Frame membership** — `Pin` / `Detach` / `Auto` (when selection ≥ 1 frame + ≥ 1 other node, or ≥ 2 frames).
- **Z-order** (single selection only) — `Bring to Front` / `Bring Forward` / `Send Backward` / `Send to Back`.
- **Per-type quick edit** — `Background` (frame) and `Appearance` (media) — partially already in the popup as `Edit frame appearance` / `Edit appearance`, so this is the duplication.

Open questions:

- **Does the popup get *all* of these, or only the ones that fit a "long-press context menu" pattern?**
  - Z-order feels right in a context menu (per-node operation, infrequent enough to live in a menu rather than a persistent bar).
  - Pin/Detach/Auto is more nuanced — these require a *target frame* and operate on the relationship between the selection and that frame. The current bar invocation triggers the `FrameTargetPickerDialog` for multi-frame cases. Wiring this through the menu means the menu item launches the same dialog. Doable but adds inter-modal navigation.
  - Background / Appearance — once both surfaces exist, the bar's buttons become redundant. We can drop them from the bar.
- **Does the action bar stay, get shrunk, or get removed entirely?**
  - Per `todo.md § 5c.3`: "Once 5c.1 ships, the ContextualActionBar shrinks back to Delete / Duplicate / Open Properties." That was written before the context-menu work. Update?
  - With the popup providing the full action set, the bar could shrink to a *very* small set (e.g. just Delete + Undo/Redo) — or be removed entirely if the popup is reliably reachable (long-press-on-anything).
  - Counter: the bar is *persistent on selection* and one-tap-away; the popup is two gestures (long-press → tap an item). For frequent operations like Delete on a selected node, the bar is faster.
- **Z-order ordering convention.** Bar uses ToFront / Forward / Backward / ToBack. In a menu, what order — front-first or back-first? Material's text menus tend to read "Bring to front, Bring forward, Send backward, Send to back" top-to-bottom.
- **Menu sub-grouping.** With z-order added, the single-node menu grows. Does it need section headers (Edit / Order / Membership / Lifecycle) or should it stay flat with dividers?

Implementation note: most actions already exist as `CanvasAction` variants (`BringToFront`, `BringForward`, `SendBackward`, `SendToBack`, `PinToFrame`, `DetachFromFrame`, `ClearFrameOverrides`). Wiring is straightforward; the design questions are *what to include* and *how to organize*.

---

## 5 Album Storage & Cloud Sync — Discussion Notes / Future Direction

### Context of the discussion

This note comes from a product/architecture discussion about future album storage options.

The current project already has local album persistence: albums are edited and saved locally. While discussing the album settings/properties UI, we noticed that storage location and sync behavior probably belong there too.

The key product question was:

> Should an album be only local, or should the user be able to connect it to Google Drive later?

The conclusion was not “implement Google Drive sync now”. The conclusion was:

> Keep local editing as the default and source of immediate interaction, but design the album model/settings so optional cloud sync can be added safely later.

We also discussed that cloud sync should not mean “the album lives only in the cloud”. A safer mental model is:

> The app edits a local working copy. Google Drive is an optional backup/sync target.

This led to several related design questions:

- where the user chooses storage mode;
- whether each album should correspond to a separate Google Drive folder;
- how sync should happen: manually, on open/close, or automatically;
- how to detect conflicts if local and cloud versions both changed;
- whether timestamps are enough, given device clocks may be wrong;
- whether we need revision IDs, device IDs, or a change journal;
- how to avoid silent data loss during overwrite;
- whether automatic merge is needed now or can be deferred.

The current decision is intentionally conservative:

> For the first version, do not implement complex automatic merge or real-time sync. Prefer explicit, safe sync with conflict detection, user choice, and backups before overwriting anything.

---

## 7. Appearance layer expansion (border / feather / glow / shadow / overlay anchoring)

Realization: most "masking-looking" visual effects can be achieved with a richer appearance layer rather than true clipping. Masks are expensive (offscreen composition, alpha pipelines); appearance effects are cheap and compose well.

Extends `docs/architecture/appearance.md` and `docs/architecture/media-appearance.md`. Today's `MediaAppearance` already has crop / color / frame decoration / overlays. Proposal: treat borders, feathering, glow/shadow as first-class appearance fields, not as decorative-frame variants.

### Border as a rendering layer

Border is not just a line — it's a configurable layer. Capabilities: rounded corners, gradients, glow, shadow, feathering, overlays, decorative frames, opacity, per-side settings. Sources: purely visual, procedural, raster-based, vector-based.

### Decorative frames

Compatible with all other effects (shadow / glow / feathering / overlays / rounded corners still apply). Examples: film frame, polaroid, torn paper, notebook edge, watercolor edge, contact sheet.

### Feathering / soft edges

Soft-edge rendering gives mask-like visual results without true masking.

- per-side feathering (e.g. soft top, hard bottom)
- asymmetric strength per edge
- types: transparency fade, color fade, blur fade
- properties: size, opacity, curve, asymmetry

### Glow and shadow

Same rendering model — only sign of luminance differs. Shared params: color, blur radius, spread, opacity, offset, blend mode. Future: inner shadow / inner glow.

### Overlay anchoring modes

Overlays already exist as `NodeAppearance.overlays` (unified 2026-05-19). New question: anchoring. Examples — film scratches, dust, paper texture, grain, halftone, vignette, light leaks, reflections, lens dirt.

Properties: opacity, blend mode, tiling, procedural or raster source.

Four anchoring modes proposed:

- **object-locked** — moves / rotates with the node (paper texture, lens dirt on a photo)
- **frame-locked** — moves with the frame (vignette inside a polaroid)
- **world-locked** — fixed to world coords (light leak over a canvas region)
- **camera-locked** — fixed to the viewport (film grain across the whole screen regardless of pan/zoom)

Open questions:

- Per-overlay anchoring or per-overlay-type default?
- Camera-locked overlays live *above* the canvas, not in it. Render-pass order vs. the existing overlay model?
- Today's `NodeAppearance.overlays` has no anchoring field. Additive (default `OBJECT`) or breaking?

---

## 8. Masks as a first-class concept — and a future `MaskNode`

Already partially discussed in `docs/architecture/appearance.md § 12` (`clip: ClipShape` + `alphaMask: AlphaMask?` as appearance fields — status: proposal). This section extends that with a *separate* MaskNode concept.

### Why masks remain necessary even with rich appearance

Masks fundamentally:

- clip geometry (vs. fade it)
- limit visible content
- can be animated
- may require offscreen composition

Types: rectangle, rounded rect, ellipse, polygon, vector path, image, alpha, gradient.

Cost: clipping, alpha compositing, offscreen render targets, nested mask composition. Animated and soft masks are especially expensive.

### `MaskNode` (post-MVP) — distinct from a navigation frame

Important: a MaskNode is **not** a frame. Frames are navigation anchors; MaskNode is a purely clipping object.

```kotlin
MaskNode
```

Scope variants: object mask, group mask, scene / camera mask.

Use cases: cinematic vignette, spotlight, comic-panel layouts, transition masks.

Open questions:

- **Relationship to appearance-level mask.** `appearance.md § 12` puts mask *inside* a node's appearance. MaskNode is a separate node that masks *other* nodes. Both? Or pick one?
- **Z-order semantics.** Where does a MaskNode sit in z-order — does it mask everything below it, everything in its group, or everything it overlaps?
- **Interaction with frames.** Can a MaskNode live inside a frame? Be pinned? Be a navigation target?
- **Editing UX.** Almost certainly needs its own tool / popup — see § 10.

---

## 9. Tool-based UI layout (left toolbar / topbar / right panel)

Long-term editor UI direction, inspired by Figma / Illustrator / Procreate Dreams / Concepts / Infinite Painter:

```
Left toolbar  = tools (Selection, Free Draw, Shape, Vector Edit, Eraser, Text, …)
Topbar        = active tool settings
Right panel   = contextual properties of selection
```

Expandable / collapsible groups likely needed.

Open questions:

- **Tablet vs. phone.** Tablet/phone editor split was decided 2026-05-19 (one codebase, popup-first for MVP, tablet docked panels deferred — `todo.md § 5d`). Does this section *become* the deferred tablet-panel design, or stays separate?
- **Phone story.** Phones can't host left/right panels. Does the left toolbar collapse to a floating tool switcher, and the right panel collapse to the popup? If so, the popup design from `context-menu.md` is already doing the right-panel job.
- **Tool grouping.** Procreate-style nested drawers (e.g. "drawing tools" expands to free draw / pencil / marker / watercolor) vs. a flat single-level toolbar?

---

## 10. Context-menu grouping (Transform / Appearance / Mask / Vector / Frame)

As the popup grows (per § 2 above + new tool actions), a flat menu won't scale. Proposed top-level categories:

- **Transform** — move, rotate, align, distribute
- **Appearance** — border, glow, shadow, overlays, opacity
- **Mask** — create, edit, release
- **Vector** — edit nodes, simplify path, boolean operations
- **Frame** — navigation, presentation settings, camera behavior

Open questions:

- **Section headers vs. submenu drilldown vs. dividers-only.** Three UI patterns; depends on how many items each category holds. (Also raised in § 2.)
- **Visibility depends on selection type.** Vector group only for vector nodes; Frame group only for frames; Mask group: "create mask" when nothing's masked, "edit mask" / "release" when one exists. Standard contextual-menu pattern, worth confirming.

---

## 11. `EditorState` container

Implementation-level. As tools, vector-edit state, eraser state, snapping, and transform handles all become first-class, `CanvasUiState` becomes a grab-bag. Possible factoring:

```kotlin
EditorState
 ├── activeTool
 ├── selectedObjects
 ├── activeAppearanceEditor
 ├── vectorEditState
 ├── eraserState
 ├── snappingState
 └── transformState
```

Open questions:

- **Split now or grow `CanvasUiState` and split later?** Premature factoring vs. landing tool work into a state object that's already past its limit.
- **Where does the popup / appearance-editor open-state live?** Today probably in `CanvasUiState`; would move to `activeAppearanceEditor`.
- **Persistence boundary.** Which of these survive process death? `activeTool` probably yes; `eraserState` (mid-stroke) probably no.

---

> Recently graduated out of this file:
> - Per-tool gesture maps (`FreeDraw`, `Shape`, `Text`, `VectorEdit`, `Eraser`) + Eraser modes **decided 2026-05-24 (late)** → `docs/architecture/editor-tools.md § 4.2–4.6`. Settles the old § 4 (per-tool maps) and old § 6 (Eraser modes). Per-tool highlights: `StrokeNode` raw-samples-plus-bezier-cache; `ShapeNode` separate from `FrameNode` with topbar primitive picker + aspect-ratio toggle; `TextNode` autosized (fixed width, auto height) with overlay `BasicTextField`; `VectorEdit` hybrid selection state (canvas node + per-tool anchor set), explicit-switch-only exit; `Eraser` one tool with Object + Vector-partial modes (raster partial post-MVP via future `MediaAppearance.alphaMask`), frame-delete-without-contents default, one-gesture-equals-one-Compound-undo, two-finger finalizes-and-pans. All six tools: stay-in-tool persistence; long-press always opens global popup (no per-tool override). `MaskEdit` remains deferred — gesture map blocked on `MaskNode` design in § 8.
> - Active-tool framework + `SelectionTool` gesture map **decided 2026-05-24** → `docs/architecture/editor-tools.md` (status: proposal, partially implementable). Settles the old § 3 (three-axis model: `EditorMode` × `ActiveTool` × `GlobalNav`; strict 2-finger nav in Edit; View-mode 1-finger pan exception; tool axis Edit-only; Present as separate fullscreen action) and the `SelectionTool` portion of the old § 4 (tap clears, drag-empty marquees, rect MVP + lasso later, default rule `Intersects`, selection persists across tool switches, `VectorEditTool` enabled only for exactly-one-vector-node). Drag-on-empty migration changes today's [`selection.md § 2`](architecture/selection.md#2-gesture-mapping) rect-select gesture.
> - Presentation profiles → `docs/architecture/presentation-profile.md` (per-frame multi-profile variants captured in § 9 / § 11 Deferred).
> - Long-press context menu + selection rules → `docs/architecture/context-menu.md` (status: proposal, not yet implemented).
> - Appearance / overlays / frame decoration separation → `docs/architecture/appearance.md` + `docs/architecture/media-appearance.md`.
> - Mask as a first-class concept distinct from crop → `docs/architecture/appearance.md § 12` (status: proposal, not yet implemented). Resolves to `clip: ClipShape` + `alphaMask: AlphaMask?` as separate composable fields; image / gradient / procedural mask sources.
> - Long-press context-menu proposal *committed* → `docs/todo.md § 15` (implementation scheduled, including the `AddNodeToSelection` gesture-rule rewrite as the first slice in § 15.4).
> - Tablet vs. phone editor split **decided 2026-05-19** → one codebase, popup-first for both device classes for MVP; tablet-specific docked panels deferred. Decision and remaining `todo.md` pointer at `todo.md § 5c` (panel rework note) + `todo.md § 5d` (tablet panels — deferred placeholder). Per-concept editor popup design captured in `docs/architecture/appearance.md § 12.7` and `docs/architecture/context-menu.md` (committed). Settled design points: modal popups, compound undo per popup session, nesting allowed within a single editor, cross-editor switching closes, same content composables wrapped per surface.
> - Multi-selection appearance editing **decided 2026-05-19** → captured in `docs/architecture/appearance.md § 14`. No "Edit common appearance" umbrella; per-concept popups handle multi-edit natively; Figma-style "Mixed" label for indeterminate fields; type-specific popups gated by homogeneous selection; preset application is type-scoped per `MediaStylePreset` / future `FrameStylePreset`.
> - Overlay-field unification (`MediaAppearance.overlays` + `FrameAppearance.contentOverlays` → `NodeAppearance.overlays`) **shipped 2026-05-19** in commit `d17efcb`. Captured in `docs/architecture/appearance.md §§ 1, 4` (current model + per-type rationale) with a brief design-history note in § 13. Behavior-preserving rename; serializer reads legacy `contentOverlays` JSON on a frame and lifts it into the unified field.
> - Album-level frame chrome settings + temporary session overrides **decided 2026-05-23** → `docs/architecture/frame-chrome.md` (status: proposal, not yet implemented; implementation scheduled in `docs/todo.md § 23`). Registered in `data-model.md` and `decisions.md § 9`. Resolves to: closed `FrameChromeStyle` enum, pick-one resolver, most-specific-target-wins with most-recent tiebreaker, chrome paints edge/outside/label only (never inside frame content), defaults nested under `AlbumPresentationProfile.frameChrome`, session overrides in `CanvasUiState`, MVP targets `ALL`/`SELECTED`/`CURRENT` (HOVERED, RELATED, NAV_TARGET deferred), `reason` field is diagnostic-only.
> - Multi-selection z-order semantics **decided 2026-05-24** → `docs/architecture/z-order.md` (status: proposal, single-selection ships today; multi-selection implementation extended in `docs/todo.md § 13.5`). Figma-aligned: `BringToFront` / `SendToBack` use block-extreme (selection moves to extreme as a contiguous block, internal order preserved); `BringForward` / `SendBackward` use independent-with-skip (each selected node moves one step, treating other selected nodes as transparent). Pure functions in `core/math/ZOrder.kt`; one Compound undo per command; no frame-membership recompute needed; no-op-at-extreme acceptable for MVP (greyed-out state is a follow-up).
