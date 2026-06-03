# Editor Surfaces

> Related: [editor-tools](editor-tools.md) | [context-menu](context-menu.md) | [appearance](appearance.md) | [overview](overview.md)

**Status — decided 2026-06-03.** Settles `to_discuss.md § 9` (tool-based UI layout) and supersedes the older "tablet docked panel as separate MVP architecture" framing in `todo.md § 5c` / § 5d. One baseline editor layout and interaction model for both phone and tablet; future wide-screen / configurable workspaces re-place the same logical contributions, they don't introduce a second editor architecture.

The earlier framing was structural — "left toolbar / topbar / right panel" — and forced phone/tablet to diverge architecturally. This doc replaces that with **logical editor surfaces**: each surface has a responsibility, and the layout decides how it's rendered (bar, popup, sheet, dialog, future panel). Editor behavior must not depend on placement.

---

## 1. Baseline layout (phone + tablet, same)

```
┌────────────────────────────────────────────────────┐
│ Global bar: album / undo / mode / navigation        │
├────────────────────────────────────────────────────┤
│ ToolControlBar: active tool ▾ + primary controls    │
├────────────────────────────────────────────────────┤
│                                                    │
│                       Canvas                       │
│                                                    │
│                                          [+ Add]   │
└────────────────────────────────────────────────────┘
```

Tablets get the same layout with more horizontal room (more `ToolControlBar` settings visible without a `More…` fallback). No structural divergence.

---

## 2. Logical surfaces

The editor exposes its capabilities through five logical surfaces. Each has a responsibility; none has a fixed placement.

| Logical surface | Responsibility |
|---|---|
| `GlobalChromeSurface` | Back, album identity, Undo/Redo, Edit/View toggle, frame navigation, album settings |
| `ToolControlSurface` | Active tool selection and primary settings/controls of that tool |
| `SelectionActionSurface` | Discrete actions over the selected / target object — Delete, Duplicate, z-order, frame membership, navigation, opening an editor |
| `ConceptEditorSurface` | Detailed editing of one concept — media appearance, frame appearance / background, crop, mask, text properties, etc. |
| `AddContentSurface` | Creating / importing content — Photo, Frame, Text, Shape, … |

**Rendering independence is the invariant.** A surface's behavior — what actions it offers, what state it reflects, what undo grouping it produces — must not depend on whether it appears as a bar, a popup, a bottom sheet, a dialog, or a future docked panel.

---

## 3. Baseline mapping (phone + tablet)

| Logical surface | Baseline rendering |
|---|---|
| `GlobalChromeSurface` | Global top bar (`CanvasTopBar`) |
| `ToolControlSurface` | Horizontal `ToolControlBar` below the global top bar; visible in `Edit` only and only when it has real utility (see § 4) |
| `SelectionActionSurface` | Long-press context popup; consumes the existing `EditorActionCatalog` (see [context-menu.md](context-menu.md)) |
| `ConceptEditorSurface` | Modal popup / bottom sheet / large dialog built from reusable content composables (see [appearance.md § 12.7](appearance.md#127-editor-ux)) |
| `AddContentSurface` | Current `AddContentBottomSheet` from the FAB; future radial FAB (`todo.md § 5b`) is a re-render of the same surface |

---

## 4. `ToolControlSurface` — design

Baseline rendering: a horizontal `ToolControlBar` sitting below the `GlobalChromeSurface`.

```
[ ↖ Selection ▾ ]    Rectangle | Lasso    Intersects | Contained
[ 🧽 Eraser ▾ ]       Object | Vector Partial    Size ━━━━━
[ ✏ Free Draw ▾ ]     Brush ▾    Size ━━━    Color ●    More…
```

**Composition.**
- Left: active tool selector (dropdown / segmented chooser).
- Remainder: primary controls of the active tool.
- Optional `More…` entry: opens detailed controls when the bar can't fit everything.

**Behavior rules.**
- Whenever `ToolControlBar` is visible, the current active tool **must** be clearly visible. Critical for destructive / input-changing tools (`Eraser`, `FreeDraw`, `Text`, `Shape`) — the user must never wonder what their finger is about to do.
- Show only implemented and currently-available tools. Don't pre-declare disabled future slots in the selector.
- Hide `ToolControlSurface` in `View` and `Present` — editing tools don't apply. Selection may persist across `Edit ↔ View` switches within a session, but on returning to a destructive tool the active-tool indicator must read as obviously active.
- Returning to a destructive tool after a `View` interlude must be visually distinct from "fresh into the editor on `Selection`."

**When the bar first ships.** Don't render a useless `ToolControlBar` while only `Selection` exists and has no editable user-facing controls. Implement / show it when:

- a second functional tool ships (expected first case: Object-mode `Eraser`), or
- `Selection` itself gains real user-facing settings (e.g., lasso mode toggle).

At the first Eraser slice the bar may be minimal:

```
[ ↖ Selection ▾ ]
[ 🧽 Eraser ▾ ]       Object
```

Vector-partial Eraser later adds the mode selector and brush size.

---

## 5. Flat tool identity

`EditorTool` variants are leaf-level interaction modes:

```
Selection · FreeDraw · Shape · Text · Eraser · VectorEdit · MaskEdit
```

Nested Procreate-style tool groups are not introduced from day one. The following are **settings or modes** of a tool, not separate `EditorTool` identities:

| Tool | Settings / modes (not separate tools) |
|---|---|
| `FreeDraw` | Pencil / Marker / Watercolor presets, brush size, opacity, smoothing |
| `Shape` | Rectangle / Ellipse / Line / Arrow / Polygon / Star primitives |
| `Eraser` | Object / Vector Partial modes, brush size |
| `MaskEdit` | Mask geometry (Rect / Ellipse / Path / Free), feather, scope |

This aligns with the `EditorState` rule:

```
tool identity ≠ tool settings ≠ active interaction
```

(see [editor-tools.md § 7.1](editor-tools.md#71-state)).

A brush preset, shape primitive, or eraser mode does not become a toolbar-level tool unless a future tested UX need justifies it.

---

## 6. Context-gated tools

`VectorEdit` and `MaskEdit` exist in the `EditorTool` vocabulary, but they shouldn't appear as permanent normal entries in the primary tool selector. They're contextual:

- `VectorEdit` is useful only when an editable vector node is selected (see [editor-tools.md § 4.5](editor-tools.md#45-vectoredit)).
- `MaskEdit` is useful only when a valid mask target exists (see [editor-tools.md § 4.7](editor-tools.md#47-maskedit)).

Discoverability UX is deferred. Possible future patterns:

- shown only when available;
- shown disabled with an explanation;
- entered through an action such as `Edit path` / `Edit mask` from the long-press popup.

Decide once the underlying node types and tools actually exist. Near-term selector exposes only functioning tools (`Selection`, plus `Eraser` once Object mode ships).

---

## 7. Separation: actions, tool controls, concept editors

Three different contribution categories. Don't confuse them — and don't try to model all three through one mechanism.

### 7.1 `EditorActionCatalog` — discrete actions / effects

Already shipped (see `feature/canvas/actions/`). Use for commands:

```
Delete · Duplicate
Bring to Front / Forward / Backward / To Back
Pin / Detach / Auto
Navigate to Frame
Open Media Appearance Editor · Open Frame Appearance Editor
```

The same actions can later be rendered in different hosts without changing the catalog: long-press popup (today), future context toolbar, future inspector panel, keyboard shortcuts, accessibility actions.

### 7.2 Tool controls — current values and editable settings

Tool controls are **not** discrete actions. They reflect and edit retained values; many are continuous (sliders / colors):

```
Eraser mode             Eraser brush size
FreeDraw brush preset   FreeDraw size / opacity
Shape primitive         Shape fill / stroke
Selection mode / rule
```

Future shape might be a surface-independent `ToolControlModel`:

```kotlin
sealed interface ToolControl {
    data class SegmentedChoice(...) : ToolControl
    data class Slider(...) : ToolControl
    data class Toggle(...) : ToolControl
    data class ColorPicker(...) : ToolControl
    data class MenuChoice(...) : ToolControl
    data class MoreSettings(...) : ToolControl
}
```

**Do not implement this abstraction now.** When the first tool with real settings ships (Object-mode `Eraser` is the expected trigger), the right shape will be obvious. The point recorded here is the **boundary** — don't shoehorn tool settings into `EditorActionCatalog` when that time comes.

### 7.3 Concept editors — reusable detailed editor content

Per-concept content composables (`BorderEditorContent`, `ShadowEditorContent`, `ClipShapeEditorContent`, `AlphaMaskEditorContent`, `OverlayListEditorContent`, `ColorAdjustmentsEditorContent`, `CropEditorContent`, etc. — see [appearance.md § 12.7](appearance.md#127-editor-ux)) are presentation-surface-agnostic.

```
Baseline phone/tablet layout  → modal sheet / dialog
Future wide-screen workspace  → docked inspector panel
```

The composables don't change because the host changes. Don't duplicate editor logic per placement.

---

## 8. Future wide-screen / configurable workspace

When wide-screen or user-configurable workspaces ship (post-MVP), each logical surface can re-render in a different host. None of this requires re-architecting; all of it is **placement, not behavior**.

| Logical surface | Possible future alternative rendering |
|---|---|
| `GlobalChromeSurface` | Same global top bar |
| `ToolControlSurface` | Wider bar with more settings visible, or an optional configurable tool panel |
| `SelectionActionSurface` | Popup remains; some actions may *additionally* render in an inspector / context toolbar |
| `ConceptEditorSurface` | Optional docked right inspector panel rather than (or alongside) modal presentation |
| `AddContentSurface` | Optional media / content library panel |

The deferred `ObjectPropertiesPanel` placeholder in [todo.md § 5d](../todo.md#5d-tablet-properties-panel-deferred) is one possible future host for `ConceptEditorSurface` content — *not* a separate MVP tablet architecture.

---

## 9. Relationship to `EditorState`

Stays consistent with the [`EditorState` decision](editor-tools.md#71-state):

- `activeTool` lives on `EditorState`. Read by the canvas + the `ToolControlSurface` renderer.
- Future tool-setting values (eraser size, brush preset, …) live on `EditorState.toolSettings` when they're introduced — *not* inside `EditorTool` variants, *not* in `CanvasScaffold` `remember` cells.
- Popup / sheet / panel open-state stays presentation-specific UI state outside `EditorState`. The single exception remains `contextAnchorNodeId`, which the canvas itself renders (anchor halo).
- The UI surface that renders a command or control does not determine where the state for that command / control belongs.

---

## 10. Non-goals

- Tablet-specific docked panels as a separate MVP architecture. Tablet uses the baseline layout.
- Configurable panel placement by the user.
- The full `ToolControlModel` abstraction up front.
- Rendering `ToolControlBar` before a functioning tool with settings makes it useful.
- `VectorEdit` / `MaskEdit` selector discoverability behavior — deferred to first-use UX feedback.
- A selection / property inspector for MVP.
- Any change to existing context-menu behavior (popup remains the baseline `SelectionActionSurface`).
