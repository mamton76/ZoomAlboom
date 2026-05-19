# ZoomAlboom — Open Design Discussions

> This file is for **unresolved** design questions only. Reconciled topics live in `docs/architecture/`. No open topics at the moment.
>
> Recently graduated out of this file:
> - Presentation profiles → `docs/architecture/presentation-profile.md` (per-frame multi-profile variants captured in § 9 / § 11 Deferred).
> - Long-press context menu + selection rules → `docs/architecture/context-menu.md` (status: proposal, not yet implemented).
> - Appearance / overlays / frame decoration separation → `docs/architecture/appearance.md` + `docs/architecture/media-appearance.md`.
> - Mask as a first-class concept distinct from crop → `docs/architecture/appearance.md § 12` (status: proposal, not yet implemented). Resolves to `clip: ClipShape` + `alphaMask: AlphaMask?` as separate composable fields; image / gradient / procedural mask sources.
> - Long-press context-menu proposal *committed* → `docs/todo.md § 15` (implementation scheduled, including the `AddNodeToSelection` gesture-rule rewrite as the first slice in § 15.4).
> - Tablet vs. phone editor split **decided 2026-05-19** → one codebase, popup-first for both device classes for MVP; tablet-specific docked panels deferred. Decision and remaining `todo.md` pointer at `todo.md § 5c` (panel rework note) + `todo.md § 5d` (tablet panels — deferred placeholder). Per-concept editor popup design captured in `docs/architecture/appearance.md § 12.7` and `docs/architecture/context-menu.md` (committed). Settled design points: modal popups, compound undo per popup session, nesting allowed within a single editor, cross-editor switching closes, same content composables wrapped per surface.
> - Multi-selection appearance editing **decided 2026-05-19** → captured in `docs/architecture/appearance.md § 14`. No "Edit common appearance" umbrella; per-concept popups handle multi-edit natively; Figma-style "Mixed" label for indeterminate fields; type-specific popups gated by homogeneous selection; preset application is type-scoped per `MediaStylePreset` / future `FrameStylePreset`.
> - Overlay-field unification (`MediaAppearance.overlays` + `FrameAppearance.contentOverlays` → `NodeAppearance.overlays`) **shipped 2026-05-19** in commit `d17efcb`. Captured in `docs/architecture/appearance.md §§ 1, 4` (current model + per-type rationale) with a brief design-history note in § 13. Behavior-preserving rename; serializer reads legacy `contentOverlays` JSON on a frame and lifts it into the unified field.
