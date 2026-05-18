# ZoomAlboom — Open Design Discussions

> This file is for **unresolved** design questions only. Reconciled topics live in `docs/architecture/`.
>
> Recently graduated out of this file:
> - Presentation profiles → `docs/architecture/presentation-profile.md` (per-frame multi-profile variants captured in § 9 / § 11 Deferred).
> - Long-press context menu + selection rules → `docs/architecture/context-menu.md` (status: proposal, not yet implemented).
> - Appearance / overlays / frame decoration separation → `docs/architecture/appearance.md` + `docs/architecture/media-appearance.md`.
> - Mask as a first-class concept distinct from crop → `docs/architecture/appearance.md § 12` (status: proposal, not yet implemented). Resolves to `clip: ClipShape` + `alphaMask: AlphaMask?` as separate composable fields; image / gradient / procedural mask sources.
> - Long-press context-menu proposal *committed* → `docs/todo.md § 15` (implementation scheduled, including the `AddNodeToSelection` gesture-rule rewrite as the first slice in § 15.4).

---

## 1. Tablet vs. phone editor split

Currently the editor relies almost entirely on bottom sheets. That ceiling is fine for viewing and quick edits but not for serious composition. The likely answer is two different surfaces with shared content composables underneath, plus a refactor of the existing monolithic `MediaAppearanceBottomSheet` into per-concept editors.

### 1.1 Tablet-first full editor

Direction: IDE / Figma / Miro-style workspace, canvas central, tools and inspectors around it.

Candidate panels:

- Media Library
- Frame Navigator
- Properties Panel
- History / Undo–Redo
- Appearance Editor
- Selection Tools
- Background Editor
- Frame Settings
- Layers (post §13)

Panel mechanics to decide:

- docked vs. floating vs. collapsible vs. tabbed groups;
- whether layout is customizable per user;
- which panels are open by default per device class.

### 1.2 Phone-compatible quick editor

Direction: lightweight, contextual, no large permanent panels.

Surfaces:

- floating action buttons;
- contextual popups near the finger (covered by `context-menu.md`);
- compact menus;
- bottom sheets reserved for deeper editing forms only;
- clean full-screen viewing mode.

Conclusion: bottom sheets should not be the only editing mechanism. Primary object actions should live in the context menu (`context-menu.md`), with bottom sheets only for forms that need real estate (media editor with picker UI).

### 1.3 Per-concept editor popups

The current `MediaAppearanceBottomSheet` is a single sheet covering every field (opacity / cornerRadius / crop / color adjustments / border / shadow / overlays / frame decoration / caption). The proposed direction is to split it into ~9 per-concept content composables — one per concept — wrappable as:

- **Popup** (phone): launched from the context menu, modal, opened near the touch point.
- **Panel section** (tablet): stacked inside a docked Properties Panel; all visible at once.
- **Sheet section** (legacy): the existing bottom sheet stays as a "show everything" wrapper if a power-user surface is wanted.

Settled design points (confirmed 2026-05-18):

- **Modal popups.** Canvas underneath is non-interactive while a popup is open. Simpler implementation, no concurrent gesture handling.
- **Compound undo per popup session.** Open a `commandSessionId` when the popup opens, dispatch live actions tagged with it, finalize as one `Compound` undo entry on close. Lines up with how brush-stroke / drag commands group today.
- **Nested popups within a single editor.** A border popup can open a color picker which can open an asset picker — drilling down within the same editor is fine.
- **Cross-editor switching closes.** Clicking "Edit shadow" from the context menu while "Edit border" is open closes border, opens shadow — popups do not stack across editors.
- **Same content composable, different wrappers.** Tablet Properties Panel shows the editor stacked vertically; phone popup shows the same editor in single-focus mode. Same MVVM, different layout wrapper.

### 1.4 Open questions

- Do we ship the phone editor and tablet editor as one codebase with a `WindowSizeClass` switch in the IDE shell, or as two feature modules sharing `feature/canvas` + domain?
- What is the minimal phone editor scope for MVP — viewing + add media + context-menu actions, with no panel surface at all?
- When the tablet panel system lands, does it replace the current `IdeUiState.panels` model or extend it?
- File layout — does `feature/<name>/ui/popups/` become a new directory parallel to `content/panels/sheets/`?

---

## 2. Multi-selection appearance editing

`appearance.md` describes the single-object case clearly but does not specify what `Edit common appearance` means on a multi-selection (`context-menu.md § 4.4`).

Open questions:

- For a homogeneous selection (all media, or all frames), shared fields (border, shadow, clip, opacity, alphaMask) should edit as a group with **indeterminate** state for fields that differ across selected nodes. What's the editor's representation of "indeterminate"?
- For a mixed selection (media + frames), do we (a) show only the intersection of editable fields (`opacity`, `clip`, `alphaMask`, `border`, `shadow` — the `NodeAppearance` base), (b) split into a media tab + frame tab, or (c) disallow the menu item and require homogeneous selection?
- Type-specific fields — `MediaAppearance.overlays` vs. `FrameAppearance.contentOverlays` — are they editable in multi-edit at all? The semantics differ (per-object vs. container-level), so a unified "add overlay to all" is conceptually wrong for mixed selections.
- How do appearance presets (post-MVP, `media-appearance.md`) interact with multi-edit? Apply same preset to all? Apply per-type presets?

---

