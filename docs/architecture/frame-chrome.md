# Frame Chrome

> Related: [presentation-profile](presentation-profile.md) | [appearance](appearance.md) | [selection](selection.md) | [navigation](navigation.md) | [rendering](rendering.md)

**Status — proposal, decided 2026-05-23, not yet implemented.** Settles `to_discuss.md § 4`. Implementation order in § 10.

Frames are navigation anchors, not just visual containers. **Frame chrome** is the editor / viewer hinting layer that tells the user *where the frames are* without altering what the album itself looks like. Chrome is separate from [`FrameAppearance`](appearance.md): appearance is **album content** (background, border, overlays — what gets exported, what survives mode changes); chrome is **editor/viewer hint** (mode-dependent, possibly transient, never serialized into appearance).

The short form:

- *Appearance* answers: "What does the album look like?"
- *Chrome* answers: "What does the editor/viewer tell me about frames right now?"

Appearance is always visible. Chrome may be hidden depending on mode, panel state, or transient interaction.

---

## 1. The bright line: where chrome may paint

Chrome is **additive overlay only**, never inside the frame's content area. The renderer never composes chrome with the frame's background / member pixels.

Allowed paint regions:

- The frame's **edge** (border-aligned outline at any thickness / style)
- **Outside** the edge (glow that bleeds out, corner ticks)
- **Above** the frame as a small label or tab attached to one edge

Forbidden paint regions:

- Inside the frame's content rect (would conflict with `FrameAppearance.background` / member pixels — *that's appearance*)
- Below member nodes within the frame (would conflict with frame's own backgrounds)

If a chrome style appears to need "inside" paint (e.g. tint), it belongs in `FrameAppearance` or as a per-frame `overlays` entry, not in chrome. `TintedArea` was deliberately dropped from the MVP vocabulary for this reason.

---

## 2. Vocabulary (MVP)

`FrameChromeStyle` is a **closed enum**. Pick-one resolution per frame (see § 5). Adding a value is a deliberate API change.

| Style | Paint summary | Typical producer |
|---|---|---|
| `Hidden` | Nothing — no chrome drawn for this frame. | Present mode default; explicit override to suppress hints. |
| `CornersOnly` | Short corner ticks at the four corners of the frame rect. | View mode default; subtle "there's a frame here." |
| `SubtleOutline` | Thin outline aligned with frame edges, low opacity. | View mode alternative; selected-frame hint in panel-driven highlight. |
| `SoftGlow` | Outer glow bleeding outside the edge, no inline stroke. | Transient: navigation transition flash, frame-list hover highlight. |
| `LabelTab` | Small label attached to one edge (typically top), shows `Frame.label`. | Edit mode optional; minimap / frame-list "you are here" indicator. |
| `FullOutline` | Solid edge stroke, opaque, full width. | Edit mode default; debug. |
| `DebugBounds` | High-contrast stroke + corner markers + (optional) coordinate text. | Developer toggle only; never reaches end users. |

Adding a value is a deliberate API change — every renderer in the paint loop must handle it, and a Figma reference must exist before merge.

---

## 3. Data model

### 3.1 Album-level defaults

Chrome defaults live nested inside [`AlbumPresentationProfile`](presentation-profile.md#2-domain-model). The profile already owns mode-dependent presentation behavior (camera fit, outside-frame mode, transitions); chrome defaults are the same kind of fact.

```kotlin
@Serializable
data class AlbumPresentationProfile(
    // …existing camera / fit / aspect / motion fields…
    val frameChrome: FrameChromeDefaults = FrameChromeDefaults(),
)

@Serializable
data class FrameChromeDefaults(
    /**
     * Per-mode default chrome style applied to every frame when no session
     * override matches. `null` for an absent mode entry falls back to
     * [defaultPerMode] below.
     */
    val perMode: Map<CanvasInteractionMode, FrameChromeStyle> = defaultPerMode,
) {
    companion object {
        val defaultPerMode: Map<CanvasInteractionMode, FrameChromeStyle> = mapOf(
            CanvasInteractionMode.Edit to FrameChromeStyle.FullOutline,
            CanvasInteractionMode.View to FrameChromeStyle.CornersOnly,
            CanvasInteractionMode.Presentation to FrameChromeStyle.Hidden,
        )
    }
}
```

Nested under `AlbumPresentationProfile` rather than a sibling `AlbumFrameChromeProfile` to avoid a second top-level config to wire through. If chrome grows past one nested field (e.g. per-style customization, per-frame overrides), extract a sibling at that point.

### 3.2 Session-level overrides

Held in `CanvasState.editor` (the editor-session state object — see [editor-tools.md § 7.1](editor-tools.md#71-state)), **never serialized**. Multiple overrides may be active concurrently. Like `contextAnchorNodeId`, chrome overrides drive canvas rendering, so they belong in editor-session state rather than `CanvasScaffold` UI-surface state.

```kotlin
data class FrameChromeOverride(
    val target: ChromeOverrideTarget,
    val style: FrameChromeStyle,
    val lifetime: ChromeOverrideLifetime,
    /**
     * Diagnostic only — labels the producer for logs / debug overlays.
     * The resolver MUST NOT branch on this value. New producers append
     * to this enum without changing resolver behavior.
     */
    val reason: FrameOverrideReason? = null,
)

/** MVP target set. HOVERED, RELATED, NAV_TARGET deferred — see § 8. */
enum class ChromeOverrideTarget {
    ALL,        // every frame in the album
    SELECTED,   // every frame in CanvasState.selectedNodeIds
    CURRENT,    // the camera's currently-focused frame (View / Present mode)
}

sealed class ChromeOverrideLifetime {
    data class Timed(val durationMillis: Long) : ChromeOverrideLifetime()
    data object WhilePanelOpen : ChromeOverrideLifetime()
    data object WhileGestureActive : ChromeOverrideLifetime()
    data object UntilCancelled : ChromeOverrideLifetime()
}
```

`reason` is intentionally typed (not a free-form string) so it's grep-able for diagnostics, but it's load-bearing **nowhere** — the resolver, the renderer, and the override-lifetime tracker all ignore it. New producers append; no migration.

---

## 4. Producers (MVP)

The set of UI surfaces that push session overrides today.

| Producer | Target | Style | Lifetime |
|---|---|---|---|
| Frame-list panel highlights row hover (mouse/stylus only, post-MVP) | `SELECTED` | `SubtleOutline` | `WhilePanelOpen` |
| User taps "Show frame bounds" debug toggle | `ALL` | `DebugBounds` | `UntilCancelled` (toggle) |
| Long-press-and-hold "show all frames" gesture | `ALL` | `SubtleOutline` | `WhileGestureActive` |
| FocusNode animation completes in View/Present | `CURRENT` | `SoftGlow` | `Timed(~600ms)` |

This table is informative, not exhaustive — new producers add rows.

---

## 5. The resolver

Pure function. Same inputs → same output. No history dependence beyond the override stack itself, no `reason` branching, no special-casing per mode.

```
resolve(
    frame: CanvasNode.Frame,
    mode: CanvasInteractionMode,
    selection: Set<NodeId>,
    currentFrameId: NodeId?,
    profile: AlbumPresentationProfile,
    overrides: List<FrameChromeOverride>,   // push-ordered (oldest first)
): FrameChromeStyle
```

Resolution rule:

1. **Filter** `overrides` to those whose `target` matches `frame` for this `(selection, currentFrameId)`.
2. **Group** filtered overrides by target specificity. MVP specificity order: `CURRENT > SELECTED > ALL`.
3. **Pick** the highest-specificity bucket. If empty, use the mode default from `profile.frameChrome.perMode[mode] ?: FrameChromeDefaults.defaultPerMode[mode]`.
4. **Within the highest-specificity bucket, most-recent-pushed wins.** This is the secondary tiebreaker. Implementation note: since `overrides` is push-ordered, this is `last()` of the bucket.

This rule has one property that matters: **given the current `overrides` list, `resolve` is decidable without replaying event history.** Most-recent only applies as a tiebreaker within one specificity bucket — it can't make a less-specific override beat a more-specific one.

### 5.1 Mode default is the implicit base

There is no "default vs. override" branching in the resolver. The mode default is conceptually the lowest-priority entry — equivalent to a permanent `(ALL, mode-default-style, UntilCancelled)` override at the bottom of the stack. Step 3 above is the only place this appears; everything else treats the stack uniformly.

### 5.2 No numeric priority

Producers do not assign numeric weights. Specificity is fixed by target type; tiebreaks are fixed by push order. This is intentional MVP simplicity — if a producer needs to override-the-override, the right answer is usually "push later" (most-recent wins) rather than "claim higher number."

---

## 6. Render layer

Chrome paints in a **separate pass** above all `FramePaintEvent`s and above `SelectionOverlay`'s selection handles, world-locked (inside the camera `graphicsLayer`). Strokes scale with `1/camera.scale` so visual thickness is screen-constant (same pattern as guidelines / selection handles — see [rendering.md § 7](rendering.md#7-node-creation)).

Order (top to bottom = drawn last to drawn first):

```
FrameChromeOverlay         ← resolved per frame, this doc
SelectionOverlay handles   ← resize / rotate handles; per-node, universal
LayeredFrameOverlay        ← FrameAppearance.overlays paint, clipped to frame
NodePass / member nodes    ← scene graph content (members + media + frames)
LayeredFrameSurface        ← FrameAppearance.background paint
```

Chrome sits above appearance-layer overlays so chrome hints are never visually swallowed by a heavy frame overlay. Chrome sits above selection handles so the chrome's outline is visible even when the frame is selected (you see both: chrome's outline + the eight handles).

Chrome paint is **always clipped to a thin band around the frame's edge** (see § 1's bright line) — even `SoftGlow` only bleeds outside, never inside. Enforcement in the renderer, not the data — a chrome renderer that paints inside the frame rect is a bug.

---

## 7. Interaction with existing surfaces

### 7.1 `Frame.color` outline (legacy)

`Frame.color` today drives a default outline drawn by `FullFrameRenderer` / `SimplifiedFrameRenderer`. Once chrome ships, that outline is **chrome's job**, not the frame renderer's:

- `FullFrameRenderer` stops drawing the outline.
- `FrameChromeOverlay` reads `frame.color` as the chrome paint color when the resolved style is `FullOutline` / `SubtleOutline` / `CornersOnly` / `SoftGlow` / `LabelTab`. (`Hidden` and `DebugBounds` ignore `frame.color`.)
- This is a behavior-preserving migration if Edit mode's default chrome resolves to `FullOutline` — every frame still gets its colored outline in Edit, just from a different paint pass.

### 7.2 `SelectionOverlay`

`SelectionOverlay` paints resize / rotate handles for any selected node and an anchor halo for the context-menu anchor ([context-menu.md § 2](context-menu.md#2-menu-model)). Handles and halo are **universal across node types** (work on media too) and are not chrome — they remain in `SelectionOverlay`.

For a selected frame, both render: the chrome's resolved style (e.g. `SubtleOutline`) and the eight selection handles. They don't visually double-up because handles sit on the frame's corner / midpoint *points* and chrome paints the *edge*.

### 7.3 `FrameAppearance.border`

`FrameAppearance.border` is **appearance** — it survives mode changes and is part of the album. If a frame has a thick `FrameAppearance.border`, the chrome's `SubtleOutline` is still drawn on top of it (chrome sits above appearance in the paint stack). This is by design: chrome is a hint *in addition to* whatever the album visually says about the frame. A user who wants "no chrome ever for this frame" sets a per-frame override (deferred — see § 8).

---

## 8. Open Questions

These are deliberately deferred from MVP. None block the implementation order in § 10.

- **Per-frame override.** A `FrameChromePerFrameOverride` on `CanvasNode.Frame` mirroring [`FramePresentationOverride`](presentation-profile.md#2-domain-model), so a specific frame can always render `LabelTab` regardless of mode. Worth it once the user-facing need shows up; until then, the global resolver suffices.
- **`NAV_TARGET` target.** Reserved for the post-navigation flash described in [navigation.md § Animated Frame Focus](navigation.md#animated-frame-focus). MVP has no flash producer; add the target when the flash ships. Specificity-wise it would tie with `CURRENT`, so insertion order would decide — confirm at that time.
- **`HOVERED` target.** Touch has no hover producer. Add later for stylus / mouse / external input modalities. Until then, leaving the enum value in is dead code.
- **`RELATED` target.** Ambiguous: the chrome target is frames, but the natural "related" relationship (`FrameMembershipUseCase.effectiveMembers`) is mostly media. Define when a concrete producer needs it — e.g. inverse membership ("frames that contain the selected media node"), explicit navigation edges, or frame-graph proximity. Pick a definition that maps to one of these producers, not a hypothetical relationship.
- **Edit-mode default override-ability.** § 3.1 makes Edit mode just another entry in `perMode`, so the album setting overrides it. Confirm this matches product intent — alternative is hardcoding `Edit` to always show `FullOutline` regardless of album setting (and not exposing it in the UI). Hardcoding makes the album setting a lie ("per-mode defaults — except this one"); leaving it overridable is more honest but lets a careless setting hide all edit-mode chrome. Lean toward overridable + good defaults.
- **Animation ownership.** Per-style (each `FrameChromeStyle` owns its appear / disappear animation) vs. carried by the override (animation curve as a field on `FrameChromeOverride`). Per-style is deterministic for the resolver and simpler; defer the override-carried version until a producer needs an animation different from the style's default.
- **Restoration after process death.** Session overrides are not persisted, but if the user had the frame-list panel open when the process was killed, restoring the panel on relaunch should re-create its associated override. Pattern: each producer is responsible for re-pushing on resume; the session state owns no recovery logic of its own. Confirm before implementing panel state restoration.
- **`LabelTab` placement and collision.** Multiple `LabelTab`-styled frames may overlap visually if their tabs all attach to the same edge. Pick a placement rule (bottom-edge default, switch to top when bottom is occluded?) and decide whether tabs cull when illegible at the current zoom.
- **`DebugBounds` UX surface.** A developer toggle, but where does it live? TopBar HUD menu, panel config, or a build-variant flag? Probably a debug-only menu item; confirm before wiring.

---

## 9. Non-goals

- Per-frame chrome animation editor.
- Chrome as published-album output (chrome never reaches the exported album — that's appearance's job).
- Chrome that paints inside the frame content area (see § 1's bright line; that's appearance).
- Mode-of-presentation-aware chrome merging (pick-one is the rule, not merge — see `to_discuss.md § 4`'s settled decision).

---

## 10. Implementation order

Depends on [`AlbumPresentationProfile`](presentation-profile.md) being threaded through `SceneGraph` (already done).

1. **Add `FrameChromeStyle` enum + `FrameChromeDefaults` data class** in `domain/model/`. Wire `frameChrome` field into `AlbumPresentationProfile`; default empty map → falls back to `defaultPerMode`. Serializer migration: missing key is fine, no rewrite. Add round-trip test.
2. **Add `FrameChromeOverride` + targets + lifetimes** in a new `domain/model/FrameChromeOverride.kt`. Held in `CanvasState.editor` as `chromeOverrides: List<FrameChromeOverride>` (push-ordered).
3. **Add the resolver** as a pure function in `domain/usecase/FrameChromeResolverUseCase.kt`. Unit tests cover: empty stack → mode default; `ALL` vs `SELECTED` vs `CURRENT` specificity; tiebreaker via push order; missing mode → fallback default.
4. **Render layer.** New composable `FrameChromeOverlay` in `feature/canvas/view/`. Draws per-frame chrome above `LayeredFrameOverlay` and above `SelectionOverlay`, inside the camera `graphicsLayer`. Strokes scale with `1/camera.scale`. Implements all seven `FrameChromeStyle` cases.
5. **Migrate the `Frame.color` outline** out of `FullFrameRenderer` / `SimplifiedFrameRenderer` into `FrameChromeOverlay`. Confirm Edit mode is behavior-preserving via screenshot diff (every existing frame should still render its colored outline).
6. **Lifetime tracker** — a small coroutine in `CanvasViewModel` that decrements `Timed` overrides, listens to gesture-end / panel-close events, and prunes expired overrides from `CanvasState.editor.chromeOverrides`. Producers push; tracker removes.
7. **Album-level settings UI.** Extend whatever album-settings surface owns `AlbumPresentationProfile` to expose `frameChrome.perMode` as a per-mode picker. Defer if the album-settings surface itself hasn't shipped — chrome works without UI, just at the defaults.
8. **First producer:** "Show frame bounds" debug toggle (TopBar HUD menu) → pushes `(ALL, DebugBounds, UntilCancelled)`. Trivial; validates the full path end-to-end.
9. Additional producers (frame-list highlights, FocusNode-completion glow) land as their host UIs ship.