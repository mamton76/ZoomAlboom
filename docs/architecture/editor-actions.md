# Editor Action Catalog

> Related: [context-menu.md](context-menu.md) | [z-order.md](z-order.md) | [selection.md](selection.md)

How selection-scoped actions (Delete, Duplicate, z-order, Pin/Detach, Edit appearance, …) are modelled, dispatched, and rendered. The catalog replaces the previous stringly-typed `onAction(label: String)` dispatch and is consumed by the long-press context menu today; future surfaces (toolbar settings panel, keyboard shortcuts, accessibility actions) can read from the same catalog.

---

## 1. Why a catalog

Earlier code dispatched selection-scoped actions via `(String) -> Unit` callbacks:

- The bar (`ContextualActionBar`, now removed) defined `ActionItem(icon, label)` lists and called `onAction(label)` per tap.
- The host (`CanvasScaffold`) matched on `when (label) { "Delete" -> …; "ToFront" -> … }` to translate the magic string into a `CanvasAction`.
- The long-press popup (`buildEditContextMenuItems`) built a parallel vocabulary along a separate path.

That shape cost: magic strings (typos passed compile), divergence between every surface that exposed actions, fan-out for adding a single action (definition + visibility flag + when branch + plumbing), and a hard cap on growth before category grouping landed.

The catalog collapses every action into a single declarative object that knows its own visibility, label, icon, dispatch effect, and category.

---

## 2. Model

`feature/canvas/actions/EditorAction.kt`:

```kotlin
sealed interface EditorAction {
    val id: String                                // stable identifier; used as list keys
    val icon: String?                             // glyph for inline rows; null = text-only
    val category: ActionCategory

    fun label(ctx: SelectionContext): String      // contextual ("Duplicate" vs. "Duplicate selection")
    fun isVisible(ctx: SelectionContext): Boolean = true
    fun isEnabled(ctx: SelectionContext): Boolean = true
    fun effect(ctx: SelectionContext): EditorActionEffect?
}

enum class ActionCategory {
    Edit, Navigation, Transform, ZOrder, Membership, SelectionMeta, Lifecycle,
}

sealed interface EditorActionEffect {
    data class Dispatch(val action: CanvasAction) : EditorActionEffect
    data class FrameMembership(val intent: FrameMembershipIntent) : EditorActionEffect
    data object OpenMediaAppearance : EditorActionEffect
    data object OpenFrameBackground : EditorActionEffect
    data object OpenAddSheet : EditorActionEffect
}

data class SelectionContext(
    val selectedNodeIds: Set<String>,
    val anchorNodeId: String? = null,
    val singleSelectedFrame: CanvasNode.Frame? = null,
    val singleSelectedMedia: CanvasNode.Media? = null,
    val selectedFramesInOrder: List<CanvasNode.Frame> = emptyList(),
    val pinDetachEnabled: Boolean = false,
    val anyOverrideExists: Boolean = false,
)
```

Each concrete action is a `data object` implementing the interface. Visibility / enable / effect / label are co-located with the definition.

The registry `EditorActionCatalog.all` lists every catalog-driven action; consumers iterate or filter:

```kotlin
object EditorActionCatalog {
    val all: List<EditorAction> = listOf(/* … */)

    fun visibleActions(ctx: SelectionContext): List<EditorAction> =
        all.filter { it.isVisible(ctx) }

    fun visibleByCategory(ctx: SelectionContext): Map<ActionCategory, List<EditorAction>> =
        visibleActions(ctx).groupBy { it.category }
}
```

---

## 3. Why an effect type, not a plain `CanvasAction`

A naive shape would have `dispatch(ctx) -> CanvasAction?` and let the host call `viewModel.onAction`. That doesn't cover every action today:

- **Pin / Detach / Auto** need to flow through `dispatchFrameMembership`, which either fires the `CanvasAction` directly (single target frame) or stashes a `FrameMembershipIntent` and opens `FrameTargetPickerDialog` (multi-frame).
- **`Edit appearance` / `Edit frame appearance`** mutate local UI state (`mediaApprEditing`, `frameBgEditing`) in the host; they don't dispatch a `CanvasAction` at all.
- **`Add…`** opens `AddContentBottomSheet` via `showAddSheet = true`.

So `effect(ctx)` returns a sealed `EditorActionEffect`. The host runs each effect via a single dispatcher:

```kotlin
fun runEditorActionEffect(effect: EditorActionEffect) {
    when (effect) {
        is EditorActionEffect.Dispatch          -> canvasViewModel.onAction(effect.action)
        is EditorActionEffect.FrameMembership   -> dispatchFrameMembership(effect.intent)
        EditorActionEffect.OpenMediaAppearance  -> singleSelectedMedia?.let { mediaApprEditing = it }
        EditorActionEffect.OpenFrameBackground  -> singleSelectedFrame?.let { frameBgEditing = it }
        EditorActionEffect.OpenAddSheet         -> showAddSheet = true
    }
}
```

Adding a new effect kind is a one-place change. New `CanvasAction`s don't need a new effect — they reuse `Dispatch`.

---

## 4. Context-aware labels

`label` is a function of `SelectionContext`, not a property, because some actions read differently across single- and multi-selection contexts:

- `Duplicate` ↔ `Duplicate selection` (single vs. group)
- `Delete` ↔ `Delete selection`

Most actions ignore the parameter; the function shape is uniform regardless.

---

## 5. Consumers

The long-press context menu (`buildEditContextMenuItems`) is the only consumer today. It iterates `EditorActionCatalog.visibleByCategory(ctx)` and renders categories into the menu shape documented in [context-menu.md § 4](context-menu.md#4-menu-content-by-selection-type):

- Text rows for `Edit`, `Navigation`, `Lifecycle` (Duplicate at top, Delete alone at bottom), `SelectionMeta`.
- Inline icon-button rows for `ZOrder` and `Membership` — short, related sets that benefit from compact rendering.
- Anchor-scoped items (`Edit this only`, `Remove this from selection`) are appended outside the catalog; see § 6 below.

Future consumers (right-side properties panel on tablet, keyboard shortcuts, accessibility actions) will read the same catalog. Adding a surface means adding a renderer, not adding a parallel action vocabulary.

---

## 6. What lives outside the catalog

Two anchor-scoped items don't fit the `(ctx) -> Effect` shape:

- **`Remove this from selection`** uses `keepOpenOnClick = true` and a custom `onAnchorRemoved` callback — the popup stays open so the user can keep adjusting the selection, and the host clears the anchor.
- **`Edit this only`** is anchor-id-parameterized at render time and only meaningful when `anchorNodeId in selection && selection.size >= 2`.

Both are special-cased inline in `buildEditContextMenuItems` because their semantics don't generalize. If a future requirement makes either pattern common, the catalog interface can grow a `keepOpenOnClick` / `anchorScoped` predicate — but a single special case isn't worth the abstraction.

---

## 7. Categories

| Category | Members | Where it renders |
|---|---|---|
| `Edit` | `Edit appearance`, `Edit frame appearance` (+ future per-concept editors per [appearance.md § 14](appearance.md#14-multi-selection-editing)) | Header text rows |
| `Navigation` | `Navigate to frame` | Header text rows |
| `Transform` | (future) Align, Distribute, Rotate-by-X° | Header text rows |
| `ZOrder` | Bring to Front, Bring Forward, Send Backward, Send to Back | Inline button row |
| `Membership` | Pin, Detach, Auto | Inline button row |
| `Lifecycle` | Duplicate, Delete | Duplicate in header, Delete alone at bottom |
| `SelectionMeta` | Clear selection | Very bottom, group only |

The split between text rows and inline rows is the consumer's call, not the catalog's. The menu uses inline rows for `ZOrder` / `Membership`; a future toolbar might render every category as buttons.

---

## 8. Adding a new action

1. Add a `data object` in `feature/canvas/actions/EditorActionCatalog.kt` implementing `EditorAction`.
2. Register it in `EditorActionCatalog.all`.
3. If it needs a new kind of side-effect, add a variant to `EditorActionEffect` (sealed interface — compiler enforces the host's `when` covers it).
4. No menu wiring needed if the existing category renders it correctly. If a brand-new rendering shape is required, extend `buildEditContextMenuItems`.

Visibility, enable, label, icon, dispatch all live in the one `data object`. Grep finds every usage by class name.

---

## 9. Multi-selection support

Several catalog actions today restrict themselves to single selection (z-order: `selectedNodeIds.size == 1`). Multi-selection z-order ships under [z-order.md § 3](z-order.md#3-multi-selection-semantics) (block-extreme for `ToFront` / `ToBack`, independent-with-skip for `Forward` / `Backward`). When that ships, the actions' `isVisible` predicates relax to `selectedNodeIds.isNotEmpty()` and the popup's inline row appears for groups automatically — no extra plumbing.

The same pattern (loosen visibility, let the catalog do the rest) covers any other "currently single-only" action that grows multi-selection semantics later.
