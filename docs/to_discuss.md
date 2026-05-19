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

## 3. Can z-order actions be applied to a multi-selection?

Today the `ContextualActionBar` gates z-order on `selectedNodeIds.size == 1` (`showZOrderActions = selectedNodeIds.size == 1` in `CanvasScaffold`). The action wiring also passes only one id (`selectedNodeIds.firstOrNull()`). The intuition: yes, multi-selection z-order should work — but the semantics need a deliberate choice.

Possible semantics:

- **(a) Independent per-node step** — every selected node moves `+1` (Forward) or `-1` (Backward), no awareness of the others. Simplest to implement. Risk: nodes in the selection cross each other unpredictably; users expect the group to *stay grouped* visually.
- **(b) Block move preserving internal order** — treat the selection as a contiguous block in z-order. Forward = find the next non-selected node above the highest selected, swap the block past it. Backward = mirror. Internal relative order is preserved. Most consistent with Figma / Sketch.
- **(c) Extreme-only multi-select** — `Bring to Front` and `Send to Back` are well-defined for groups (put all selected at top / bottom, preserve internal order). `Bring Forward` / `Send Backward` are ambiguous on a sparse group; hide them when the selection isn't a contiguous block. Less complete but safer.
- **(d) Compound undo, regardless** — whichever semantic, the multi-selection action produces **one** `Compound` undo entry, not N step entries. Matches `DuplicateSelection` / `DeleteSelection` precedent.

Open sub-questions:

- **What does "Bring Forward" mean when the selection is non-contiguous in z?** E.g. selection = {A at z=2, C at z=5}, with B at z=3 and D at z=4 unselected. Does forward swap A past B (z=3) while C stays? Then A and C are no longer "a group" — they're independent moves. The block-move semantic (b) would collapse the group into a contiguous block first, but that *reorders unselected nodes* which is surprising.
- **Should the action bar's z-order buttons enable for multi-selection?** If yes, the four buttons (ToFront / Forward / Backward / ToBack) might need to grey out per-state — e.g. "Bring Forward" disabled when the selection is already at the front, or when (c) excludes the gappy case.
- **Pure function in `core/math/`?** Group z-order operations are good candidates for the same pattern as `Align` / `Distribute` / `FrameAroundSelection` — pure function taking a list of transforms + selection + operation, returning new z-indices. Easier to test in isolation.
- **Does the action also rerun frame-membership recompute?** Z-order doesn't change geometry, so probably not — but worth confirming, since frame membership depends on geometry, not z-order, today.

My intuition: **(b) block-move semantics** for `Forward` / `Backward`, **simple "put at extreme, preserve internal order"** for `ToFront` / `ToBack`. Single Compound undo per call. Enable in both ActionBar and popup once decided.

But it's a real design call — flagging for explicit decision.

---

> Recently graduated out of this file:
> - Presentation profiles → `docs/architecture/presentation-profile.md` (per-frame multi-profile variants captured in § 9 / § 11 Deferred).
> - Long-press context menu + selection rules → `docs/architecture/context-menu.md` (status: proposal, not yet implemented).
> - Appearance / overlays / frame decoration separation → `docs/architecture/appearance.md` + `docs/architecture/media-appearance.md`.
> - Mask as a first-class concept distinct from crop → `docs/architecture/appearance.md § 12` (status: proposal, not yet implemented). Resolves to `clip: ClipShape` + `alphaMask: AlphaMask?` as separate composable fields; image / gradient / procedural mask sources.
> - Long-press context-menu proposal *committed* → `docs/todo.md § 15` (implementation scheduled, including the `AddNodeToSelection` gesture-rule rewrite as the first slice in § 15.4).
> - Tablet vs. phone editor split **decided 2026-05-19** → one codebase, popup-first for both device classes for MVP; tablet-specific docked panels deferred. Decision and remaining `todo.md` pointer at `todo.md § 5c` (panel rework note) + `todo.md § 5d` (tablet panels — deferred placeholder). Per-concept editor popup design captured in `docs/architecture/appearance.md § 12.7` and `docs/architecture/context-menu.md` (committed). Settled design points: modal popups, compound undo per popup session, nesting allowed within a single editor, cross-editor switching closes, same content composables wrapped per surface.
> - Multi-selection appearance editing **decided 2026-05-19** → captured in `docs/architecture/appearance.md § 14`. No "Edit common appearance" umbrella; per-concept popups handle multi-edit natively; Figma-style "Mixed" label for indeterminate fields; type-specific popups gated by homogeneous selection; preset application is type-scoped per `MediaStylePreset` / future `FrameStylePreset`.
> - Overlay-field unification (`MediaAppearance.overlays` + `FrameAppearance.contentOverlays` → `NodeAppearance.overlays`) **shipped 2026-05-19** in commit `d17efcb`. Captured in `docs/architecture/appearance.md §§ 1, 4` (current model + per-type rationale) with a brief design-history note in § 13. Behavior-preserving rename; serializer reads legacy `contentOverlays` JSON on a frame and lifts it into the unified field.
