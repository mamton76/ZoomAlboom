# ZoomAlboom — Open Design Discussions

> This file is for **unresolved** design questions only. Reconciled topics live in `docs/architecture/`.

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

## 8. `MaskNode` editing UX + `MaskEdit` gesture map

The data-model and constraints for `MaskNode` were decided 2026-06-02 and captured in `docs/architecture/appearance.md § 12.10`. Recap of the constraints:

- A `MaskNode` masks **siblings within the same frame / group** only (not "everything below in global z-order").
- A `MaskNode` **can live inside a frame** and **can be pinned**; it is **not** a navigation target.
- Inline `appearance.alphaMask` and `MaskNode` cohabit (per-node convenience vs. shared/group-scoped).

What remains open (blocks the `MaskEdit` tool gesture map in `editor-tools.md`):

- **Editing UX.** `MaskNode` almost certainly needs its own tool / popup. Shape editing (path/anchor edits), shared-mask binding (which sibling nodes are affected), preview while editing. Connects to § 10 (context-menu grouping: "Mask" category).
- **Z-order tiebreaker within siblings.** "Masks siblings *below* it in z-order" is one rule; "masks all siblings in the same group" is another. Decide once we know the typical authoring flow.
- **Multi-mask composition.** When two `MaskNode`s overlap on the same sibling set, do their masks compose (intersection?), or does the higher-z mask override? Defer until a real use case emerges.

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
> - **§ 2 ContextualActionBar removal + § 10 context-menu grouping** **fully decided 2026-06-02** → `docs/architecture/context-menu.md § 4` (rendering convention + per-selection-type menus) and `§ 6` (bar removal). Locked: bar disappears entirely; popup is the single surface for selection-scoped actions. MVP rendering uses dividers between groups, no section headers, no drill-down submenus. Two compact inline action rows defined: **z-order row** (`⏫ ↑ ↓ ⏬`, Material order) and **frame-membership row** (`📌 Pin · 🔓 Detach · 🔄 Auto`). Pin / Detach / Auto direct-dispatch when target is unambiguous; open existing `FrameTargetPickerDialog` when multiple frames in selection. One-tap Delete regression accepted (Delete is rare; one-tap Undo limits the cost). § 10's category list (Transform / Appearance / Mask / Vector / Frame) graduated as a **deferred-but-committed** future grouping — applied as section headers when any selection type accumulates ~15+ items. Implementation tracked in `todo.md § 15` (existing context-menu section grows the bar-removal subsection).
> - **§ 7 Appearance layer expansion + inline-mask portion of § 8** **fully decided 2026-06-02** → `docs/architecture/appearance.md § 12` (expanded into the full layered evolution). Locked: three-layer separation (`NodeShape` owns boundary + feathering; `BorderStyle` owns parametric stroke; `effects: List<LuminanceEffect>` owns shadow/glow/inner-*); `NodeShape` sealed (intro now with `Rect` variant carrying `CornerRadii` + `feather`; future `Ellipse`/`Polygon`/`Path`); shadow + glow unified into `effects` list (multiple effects allowed); inline `alphaMask: AlphaMask?` on base; overlay anchoring via `OverlayStyle.anchoring: OverlayAnchoring` (Object default, plus Frame/World/Camera; Camera renders in a separate top-level pass); base promotion of `colorAdjustments` and `caption` from `MediaAppearance` (frames pick them up); rename `frameDecoration` → `decoration` (eliminates the "frame" name collision with `CanvasNode.Frame`); `crop` stays media-only. `MaskNode` data-model constraints also locked (siblings-within-frame/group only; can live in frames + pinnable; not a nav-target). Implementation order in `§ 12.8`. `MaskNode` editing UX + `MaskEdit` gesture map remain open in § 8 above.
> - **§ 1 FAB / chrome gating around the context menu** **fully decided 2026-06-01** → wired in `CanvasScaffold.kt` via a `dismissPopupAnd { ... }` / `dismissPopupAndAccept { ... }` helper applied uniformly to the FAB, top-bar handlers (Undo, Redo, Back, Frame List, Panel Config, Album Settings, mode toggle), and `ContextualActionBar.onAction`. Three resolutions: (1) **FAB stays visible and enabled at all times**, including when a selection exists — the select-then-add-more workflow is real; ContextualActionBar is going away anyway (see § 2). (2) **FAB tap with popup open dismisses the popup, then opens the Add sheet** (outside-tap semantics; selection untouched). (3) **All top-level chrome dismisses the popup on tap**, with one exception: `FrameEditOptionsBar` toggles keep the popup open because they're selection-scoped gesture modifiers — the popup remains contextual to the same selection.
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
