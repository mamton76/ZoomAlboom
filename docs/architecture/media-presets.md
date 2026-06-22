# Media Appearance Presets

> **Status:** design **decided 2026-06-21**, implementation pending. Graduated from `to_discuss.md § 17`. Source of truth for the preset model, binding/resolution, and the implementation staging. Open *UI* sub-questions remain in `to_discuss.md § 18` (preset detail panel + per-section preview) and the related stack/editor topics `§ 19` (decoration stack), `§ 20` (overlay editor).

A **Media Appearance Preset** is a reusable, named visual look applied to media nodes (photos + videos). The immediate practical focus after video + media frame decoration — see `to_discuss.md § 27` for sequencing within the cluster.

Builds on the existing `MediaStylePreset(id, name, appearance)` stub in `domain/model/MediaAppearance.kt` (currently wired to nothing) and the action list in `todo.md § 20.3`.

---

## 1. Binding model — live link + overrides

**Decision (2026-06-21):** an object **links** to its preset (it does not merely copy from it). Editing a preset updates all linked objects, *unless* the object overrides the relevant value. Resolution composes preset values with per-node overrides.

```kotlin
@Serializable
data class MediaStylePreset(
    val id: String,
    val name: String,
    val sections: Set<AppearanceSection>,  // which sections this preset GOVERNS (sets)
    val appearance: MediaAppearance,       // values; only `sections` are ever read
)

@Serializable
data class PresetBinding(                   // NEW nullable field on CanvasNode.Media
    val presetId: String,
    val overridden: Set<AppearancePath> = emptySet(),  // per-leaf override target (see § 3)
)
```

- `CanvasNode.Media.appearance` is unchanged; it holds the node's **own** values — used for ungoverned sections and for overridden leaves. A node with `presetBinding == null` behaves exactly as today → **no migration**.
- A preset **governs at section granularity** (it sets whole sections). A node **detaches individual leaves** within a governed section (per-field target; see § 3).

**Resolution** (per leaf `L` in section `S`):

```
effective.L = if (S ∈ preset.sections && L ∉ binding.overridden) preset.L
              else node.appearance.L
```

Overriding a leaf = write the value into `node.appearance` **and** add the leaf to `overridden`. Reset-to-preset = remove the leaf from `overridden` (the node's own value for it becomes ignored).

---

## 2. Sectioned / partial presets

A preset declares **which sections it governs**; Apply overwrites only those, leaving the rest of the target untouched. This makes looks **composable** (a "Film grain" preset touches overlays + color, not the user's crop) and backs the `§ 18` checkbox-per-section UI.

`AppearanceSection` (the unit a preset governs and the `§ 18` checkboxes):

```
Opacity · CornerRadius · Opening · Crop · ColorAdjustments · Overlays ·
ContentMask · Decorations · Border · Shadow      // Caption later
```

(Naming follows the 2026-06-21 content-model refactor in [`media-appearance.md`](media-appearance.md): `alphaMask`→`contentMask`, `frameDecoration`→`decorations: List<MediaDecoration>`, rectangular `opening`, no `openingMaskUri` — the **refactor lands before this slice**, "refactor-first".)

---

## 3. Override granularity — per-field target, hybrid boundary

**Decision (2026-06-21):** the **target** is **per-field / leaf-path** overrides (e.g. inherit border color, override border width). Overrides are a leaf-path set (`AppearancePath`), not a section set.

The hybrid boundary below is a **staged UI/implementation boundary, not a permanent conceptual limitation** — it can be extended later.

| Section | Override unit | Why |
|---|---|---|
| Opacity, CornerRadius | the value (section == field) | single scalar |
| **ColorAdjustments** | **per slider** (brightness, contrast, saturation, …) | prime per-field case |
| **Border** | **per field** (color, width) | the motivating example |
| **Shadow** | **per field** (color, radius, dx, dy) | same shape as border |
| Opening | whole unit | rectangular content-area slot — 4 insets move together |
| Crop | whole unit | mode + focal + offset + zoom move as one intent |
| **Overlays** | **whole stack** (slice 1) | a layer *stack*; item-level later (§ 6) |
| ContentMask | whole unit | sealed source; sub-fields not independently inheritable |
| **Decorations** | **whole stack** (slice 1) | a layer *stack*; item-level later (§ 6) |

So `AppearancePath` is a finite enum: the scalar sliders of Color/Border/Shadow as individual leaves; everything else as one entry. **Slice 1 exposes whole-section override UI only** (§ 7); the per-field architecture must not be blocked by it.

---

## 4. Overlays and decorations are layer *stacks*

Both **overlays** and **decorations** are **stacks of layers** (`overlays: List<Overlay>`, `decorations: List<MediaDecoration>`). Decorations allow multiple layers (main frame + tape + bow + sticker + label + torn/burnt edge + ticket stub). The decoration-stack + naming refactor is **decided** and lands **before** this slice ([`media-appearance.md` § "content-model refactor"](media-appearance.md); `to_discuss.md § 19`).

- **Slice 1:** override the entire overlay stack and the entire decoration stack as **whole units**.
- **Later:** item-level stack overrides (add a local layer over the inherited stack; hide/show/reorder/opacity one inherited layer) — feasible because `MediaDecoration` carries a stable `id`. A *local additive layer* is a distinct override kind from a scalar override (§ 6).

---

## 5. Single content mask + opening (decorations do not clip; no whole-node mask)

Even as a stack, decorations must **not** each carry their own media clip. Multiple independent decoration masks are confusing and expensive.

**Constraint:** exactly one **`contentMask`** (arbitrary shape) + one rectangular **`opening`** (content-area slot), both appearance-level, control the visible content shape. Decoration layers are **visual layers drawn around/above/below** the content; they may use transparent pixels visually but never clip the content. **No whole-node mask** — the mask clips *content* only, leaving frame/tape/sticker/label decorations whole (the desired scrapbook behavior). See the [`media-appearance.md` content-model refactor](media-appearance.md).

---

## 6. Future override kinds (do not build now)

Once overlay/decoration layer items have **stable IDs**, item-level stack overrides become possible. These are distinct override *kinds*:

1. **scalar value override** (a leaf — § 3);
2. **whole-section / whole-stack override** (slice 1);
3. **local additive layer on top of the inherited stack** — closer to an additive override than a leaf override; needs its own representation;
4. **reset / unlink**.

Item-level examples (later): add a local layer above the inherited stack; hide/show an inherited layer; override one layer's opacity; reorder/remove a specific layer. **Do not build these now** — the current design must simply not block them.

---

## 7. Implementation staging

Build the per-field architecture but ship in slices so first usable presets aren't blocked on the full override UI.

1. **Resolution + binding + library — Slice 1 core, implemented (build + unit tests green 2026-06-21).** `MediaPresetStore` (SharedPreferences + JSON, Hilt `@Singleton`), `MediaStylePreset.sections`, `PresetBinding`, resolution at the render boundary (`CanvasViewModel.recalculateVisibleNodes` → `resolvedForRender`), library actions apply / save-as / duplicate / delete (bake-then-unlink). **Override granularity = whole-section**; editing a concept on a bound node marks the section overridden (`dispatchMediaConcept` section arg). All preset UI is folded into one `PresetLibrarySheet` (a new `Presets…` context-menu action) — card previews + inherited/overridden styling + the dedicated preset-definition editor are **Slice 1b** (§ 10 describes the target UI).
2. **Per-field overrides, section by section** — start with **ColorAdjustments**, then Border, then Shadow. Each adds the tri-state (inherited / overridden) UI + per-leaf reset + detach-on-edit for that section.
3. **Multi-select "Mixed"** tri-state polish across the override UI (per `appearance.md § 14`).

---

## 8. Lifecycle, undo, rendering

**Deletion — bake then unlink (decided).** Deleting a preset stamps its current resolved values into every bound node, then clears the binding. No dangling refs, no visual change. "Unlink" uses the same bake path.

```
delete(preset):
  for node in boundTo(preset):
    node.appearance = resolve(node)   // current effective values, baked in
    node.presetBinding = null
```

**Undo boundary (decided).** Canvas (snapshot) undo covers: applying/unlinking a `presetBinding`, and node-level override changes. Preset *definition* edits are **app-global** (can affect nodes across albums) and do **not** go on the canvas undo stack — the `PresetStore` may get its own undo later, or none for slice 1.

**Renderer boundary (decided).** Renderers / video chrome / export receive a **resolved concrete `MediaAppearance`**. Resolution happens **before** the render boundary (eagerly in the state/domain layer against the `PresetStore`); the renderer stays pure. Editors additionally read the `binding` / `overridden` metadata to display inherited-vs-overridden state.

---

## 9. Storage, scope, asset references

- **Storage:** app-level presets first (cross-album), serialized via kotlinx-serialization (Room or a JSON file in app storage — TBD in slice 1). Later: project-level, import/export packs, downloadable libraries.
- **Type scope:** media only (`MediaStylePreset`). A future `FrameStylePreset` follows the same model. Preset application stays type-scoped per `appearance.md § 14`.
- **Asset references constraint:** presets that reference overlay/decoration/mask **images** depend on those URIs staying valid app-wide. SAF persistable permissions are app-wide, so raw content URIs work today; this is the seam that ties presets to the **Media Library** (`to_discuss.md § 14`) — when assets are later copied into managed storage, preset asset refs must point at app-global copies. Do **not** block preset work on the Media Library.

---

## 10. User interface (decided 2026-06-21)

Grounding fact: appearance editing is **per-concept** today — each concept (crop, color, border, shadow, overlays, mask, decoration, caption) opens its own `ConceptEditorSheet<T>` from a context-menu action (`CanvasScaffold.kt`), and `editor-surfaces.md` makes those concept editors **reusable content composables**. Presets **reuse** them; they do not introduce a parallel editor.

**Three surfaces:**

1. **Object editing = the existing per-concept sheets, made preset-aware.** They always edit the *object*. When the node is bound, each sheet shows inherited-vs-overridden state + reset for its concept; editing creates the override. No new surface, no apply-vs-edit ambiguity.
2. **Preset editing = a new aggregate "preset editor" sheet** (also the detail/preview panel): a list of sections, each with a **governs checkbox** (the `MediaStylePreset.sections` set, § 2) + an expandable editor that **reuses the same concept composables**. Entered *explicitly* from a library card → "Edit", so editing-the-preset and editing-the-object are physically different surfaces. Buttons: `Save` / `Cancel`.
3. **Preset library = a card sheet** (look thumbnails), opened from a new `Apply Preset` context-menu entry. Card actions: apply · new · edit · duplicate · delete.

**Entry points (context menu, single or homogeneous media):**
- `Apply Preset` → opens the library.
- A **`Preset: <name>` section** when the node is bound — `Apply other · Edit · Unlink · Reset all` (sits beside the existing `✦ Edit appearance` concept entries; this is where a bound object surfaces its link, since there's no aggregate object sheet).
- `Save as preset` → opens the preset editor pre-checked with the object's **non-default** sections; the user adjusts the governs set and saves.

**Apply-vs-edit (the ambiguity guard):** the two intents live on **separate surfaces** — object overrides happen in the per-concept sheets (`Apply Appearance` semantics, live), preset-definition edits happen only in the explicitly-entered preset editor (`Save Preset`). They are never the same screen.

**Apply over existing overrides:** applying a preset to a node that **already has overrides** prompts **`Replace look`** (clear prior overrides) vs **`Keep my changes`** (retain the overridden leaf set against the new preset). A fresh apply (no prior binding/overrides) applies with no prompt.

**Inherited / overridden visual language** (slice 1 = whole-section granularity, § 7):
- *inherited* — muted / lighter, with a small preset chip;
- *overridden* — normal active colour + a reset (↺) affordance;
- *mixed* (multi-select, indeterminate) — Figma-style "Mixed" label per [`appearance.md § 14`](appearance.md#14-multi-selection-appearance-editing);
- reset-to-preset removes the override (§ 3).

**Preview:** two levels — *global* (whole appearance composited) + *per-section* (what that section contributes: decoration with slice/cut guides, overlay individual + combined-stack, mask silhouette, color before/after; shares infra with the overlay editor, `to_discuss.md § 20`). **Preview subject:** single selection → that object's image / video poster; multi-selection → the first selected item; a **bundled sample** only when nothing is selected. Library cards use the same subject.

---

## 11. Open (deferred) — tracked elsewhere

- Preset **UI is decided** (§ 10). Remaining UI polish (card grid layout, exact per-section preview rendering, library empty-state) is implementation detail, not an open design question.
- **Decoration stack** model (multiple decoration layers, naming) → `to_discuss.md § 19`.
- **Overlay editor** (list, hide-vs-delete, combined preview) → `to_discuss.md § 20`.
- **Animated** presets (Start→End) → `to_discuss.md § 21`.
