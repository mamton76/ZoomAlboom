# ZoomAlboom — Open Design Discussions

> This file is for **unresolved** design questions only. Reconciled topics live in `docs/architecture/`.
>
> **Numbering:** section numbers are never reused. Graduated topics keep their original `§` number in the "Recently graduated" trailer (currently `§ 1`–`§ 11`, `§ 13`, `§ 15`, `§ 17`–`§ 19`, `§ 28`), so the remaining open topics are `§ 12`, `§ 14`, `§ 16`, the **Media Appearance Presets cluster `§ 20`–`§ 27`** (added 2026-06-21), `§ 30`–`§ 31` (added 2026-06-28: stickers, groups), and `§ 32` (added 2026-06-29: preset library convenience — direction decided, impl pending). `§ 28` (decoration flicker) was **resolved 2026-06-29** → moved to the "Recently graduated" trailer; `§ 29` (editor audit) was **run 2026-06-29** → results in [`todo.md § 29`](todo.md), kept inline with a DONE marker. New open topics start above `§ 32`.
>
> **The `§ 17`–`§ 27` cluster** captures the post-video / post-frame-decoration product direction: getting to a usable personal-album tool fast. **Decided 2026-06-21:** `§ 17` preset model + `§ 18` preset UI → `docs/architecture/media-presets.md`; `§ 19` content-model refactor (contentMask / rectangular opening / decorations list / drop openingMaskUri) → `docs/architecture/media-appearance.md` — all implementation pending. The rest are preserved so the near-term work is designed in the right direction. Suggested sequencing is in `§ 27`.

---

## § 12 Eraser long-press popup contents

**Status:** open. Surfaced 2026-06-05 during the Object-mode Eraser ship review.

Long-press in Eraser mode currently opens the **same** context menu as Selection mode (Edit appearance / Duplicate / Delete / z-order / membership). Per [`editor-tools.md § 5`](architecture/editor-tools.md#5-popup-derivation) the popup is supposed to be a function of `(editorMode, activeTool, selection, objectTypes)` — Eraser-specific contents (size / mode / hardness — non-destructive bailout actions) aren't wired yet.

**Mitigation already shipped:** `LongPressRoute.ResolveAnchor.extendsSelection` is `false` for non-Selection tools, so long-press in Eraser no longer silently grows the selection. The "non-destructive bailout" property of long-press ([`editor-tools.md § 4.6`](architecture/editor-tools.md#46-eraser)) is preserved at the gesture level; only the menu *contents* are still Selection-shaped.

**Open questions:**
1. **Popup-derivation strategy** — centralized `PopupActions.derive(...)` vs. per-tool `EditorTool.contributePopupActions(...)`. Per `editor-tools.md § 8` this is deferred until 3+ tools exist and the pain of one direction shows up. Today we have 2 tools — not yet enough signal.
2. **Eraser-specific popup contents** — what actually goes in the menu when `(Edit, Eraser, selection)` fires? Mirror the topbar (mode chip, brush size) + a "Cancel" / "Delete this anchor" row? What about `(Edit, Eraser, empty selection)` — empty menu or "Add..."?
3. **Anchor-not-in-selection UX** — current menu items operate on `selectedNodeIds`; the anchor is decoration. For tools that don't extend selection on long-press (everything except Selection), the anchor is effectively orphaned for menu purposes. Should anchor get its own action row, or should the menu derive its scope from `selectedNodeIds.ifEmpty { setOf(anchor) }`?

**Tracking:** resolution unblocks the `editor-tools.md § 8` "popup derivation" open question. PR descriptions for tools that ship before this lands should call out the Selection-shaped popup as a known follow-up.

---

## § 14 Media Library — model and UI direction (must not block § 13)

**Status:** open foundation. Important long-term, but explicitly **non-blocking** for the Video MVP.

The Video MVP must move us *toward* this layer with a minimal bridge, not build it in full. Captured here so the bridge in § 13 is designed in the right direction.

**Target model (future):**
```
MediaAsset(id, albumId, sourceUri, mediaType, status,
           widthPx, heightPx, durationMs?, takenAt?, geo?, poster/thumbnail info)
CanvasNode.Media(id, mediaRefId, transform, appearance)
```

**Statuses (technical preparation, not artistic editing):** `available`, `missing`, `importing`, `processing`, `ready` — covering metadata extraction, thumbnail generation, duration read, file-availability check. A missing-file placeholder is required.

**Metadata vs. tags (open principle to lock):** date and geolocation should be stored as metadata and exposed as **filters**, *not* auto-promoted to tags — auto-tagging would flood the tag system. Tags stay semantic/user-facing (`Fedor`, `Budapest`, `summer 2022`, `school`, `recipe`).

**Two-level UI (open shape):**
- *Canvas-side panel* — compact, available during editing: thumbnails, type/status indicators, simple filters, drag-to-canvas, quick access without leaving the canvas.
- *Full media manager* — heavier screen/large panel: batch import, delete/manage, advanced + date/geo filters, duplicate detection, find-all-usages, tags, variants/derivatives, metadata inspection.

Do **not** squeeze full media management into the small panel.

**Open questions:**
1. Exact minimal `MediaAsset` field set for the first bridge vs. fields deferred to the full library.
2. Where `MediaLibrary`/`MediaAsset` lives relative to the existing data model and whether a migration is needed at all for the video bridge.
3. Filter model for date/geo metadata (how filters are expressed without becoming tags).

---

## § 16 Frame navigation panel — Edit-vs-View behavior (background)

**Status:** open, not the immediate next item. Recorded so § 13/presentation work keeps it in mind.

The frame panel should behave differently per mode:
- *Edit mode* — structural editor: frame list, hierarchy / nested frames, reorder, rename, grouping; appearance/colour and presentation order later.
- *View/Present mode* — navigation: compact list or thumbnails, current frame highlighted, tap to navigate, minimal UI, possibly hidden by default.

Nested frames are a core storytelling model (parent = broad scene, children = details inside it) supporting the Prezi-like "zoom into life moments" experience.

**Note:** hover-like behaviour exists for stylus/mouse only, never finger — base UX must work with tap; hover may *additionally* reveal controls/previews for stylus/mouse later.

---

## § 20 Overlay editor improvements

**Status:** open — overlay-editing UX (renderer already supports the overlay stack).

**List redesign:** overlay editor as a list of layers; per overlay: thumbnail, **visibility toggle**, opacity, blend mode, delete, reorder (later).

**Hide vs. delete:** removing an overlay shouldn't immediately destroy it — a visibility checkbox lets the user compare an overlay's contribution without losing it. Explicit delete still exists.

**Combined preview (open):** show each overlay separately, the combined stack, or both? Likely both eventually — small per-overlay preview + combined-stack preview at the top. Shares the preview infrastructure with the preset previews (`media-presets.md § 10`).

Also relevant to decorations: the decoration-list editor (`media-appearance.md` content-model refactor) should follow this same list pattern (thumbnail, visibility toggle, reorder, hide-vs-delete).

---

## § 21 Animated media appearance presets

**Status:** open — **next** after static presets; the emotional differentiator. Do **not** build a timeline editor early.

**Core idea:** a preset can be static or animated. Animated = a transition between two appearance states (A → B), e.g. "Memory → Now", "Film → Reality", "Vintage → Alive": an object starts as a faded memory and, on interaction, becomes vivid/alive.

**Model (simplest):** Start appearance + End appearance + transition settings. Two states only; interpolate between them. Same `MediaAppearance` applies to images and videos.

**Triggering (key product call):** animation is a **reaction, not permanent motion**. Avoid constant autoplay on the canvas. Candidate triggers: tap / focus / entering viewport / explicit replay. Looping is fine in the preset preview/editor, not on the canvas.

**Transition design:** naive simultaneous crossfade produces a "naked image" moment mid-transition (all effects weak at 50%). Overlap is better — effects appear/disappear at different timing ranges (blur clears early, grain a bit later, colour returns later, frame fades with overlap).

**Transition UI — two approaches debated:**
- *A — intermediate states*: user defines mid "states" with sections on/off. Risk: drifts into a timeline editor; accidental overlap.
- *B — per-effect timing* (preferred): Start on the left, End on the right; below, each changed section gets timing (when it starts / finishes changing). First version may use coarse presets (Early / Middle / Late) instead of full sliders.

**Video + animated appearance (conflict):** video already moves; animated appearance adds a second motion layer, and playback gestures vs. animation gestures could collide. **MVP rule:** images first; video + animated appearance is a later topic.

**User awareness (unresolved):** does an animated appearance need a visible affordance? A video-style play icon may confuse (it isn't video); subtle hints may be better. Needs design.

**Candidate first presets** (first one should be emotionally strong + technically simple):
- **Memory → Now / Film → Reality** (most promising): sepia + low saturation + grain + slight blur + old frame → clean colour, sharper, grain/frame reduced.
- Color Bloom (B&W → vivid), Dust Off (dust fades, image cleans), Light Leak Reveal, Frame Break (photo escapes its frame), Soft Memory (blurred glow → sharp).

---

## § 22 Video poster / preview frame

**Status:** open — small but high-value for video polish.

Today the poster is a fixed/first frame (zero-storage Coil extraction per `video.md`). Desired: the user **picks** which frame is the poster — shown when idle, styled through the same `MediaAppearance` pipeline, and used as the playback starting point.

**Key insight:** if poster time ≠ playback-start time, play visibly jumps. Current leaning: **poster frame time = playback start time** (or at least an explicit distinction between the two).

**Smooth poster → video:** start the player at the poster/start time, keep the poster visible until the first decoded frame is ready, then short fade poster → live video (no jump). Connects to the player-pool work in `video.md`.

---

## § 23 Video scrub / rewind

**Status:** open — **preserve, not a priority.** Enables `§ 22` (pick poster/start frame) and lightweight playback control without becoming a video editor. Possible UI: a thin scrub bar in video settings; drag to choose a frame; current frame becomes poster/start. Explicitly not for the immediate preset work.

---

## § 24 Batch media operations

**Status:** open — needed soon for practical album building.

**Batch import:** select multiple photos/videos and add together; if a frame is selected, distribute inside it; otherwise place in the current viewport.

**Auto-layout:** don't stack at one point — grid placement, preserve aspect ratios, padding/gap, optionally fit inside the selected frame.

**Multi-select operations:** align left/center/right, top/middle/bottom; distribute horizontally/vertically; match size/spacing later.

**Appearance on multiple objects:** verify preset apply to one vs. several; object-level overrides under multi-selection; mixed-value display (ties to `§ 17` overrides + `appearance.md § 14` "Mixed").

---

## § 25 Android share intents

**Status:** open — practical on-ramp for getting media in.

Share photos/videos from Gallery / Google Photos / other apps into ZoomAlboom. Flows: new album/project from shared media · add to existing project · add to the currently open project. Support `ACTION_SEND` + `ACTION_SEND_MULTIPLE`, content URIs, persistable permissions where possible, copy/import into app storage when needed.

**Google Photos caveat:** may hand back content URIs that are remote-backed or temporary — the import path must robustly **copy media into app-controlled storage** rather than assume a stable local URI. Connects to `§ 14` Media Library but can start without it.

---

## § 26 Editor / View / preset-preview modes — animation behavior per mode

**Status:** open — extends the Edit-vs-View thinking in `§ 16` to animated appearance (`§ 21`).

Keep three contexts distinct: **Edit** (canvas authoring), **View / Present**, **Preset preview**. Proposed animation behavior: preset editor loops or replays on demand; Edit canvas previews explicitly; View/Present triggers on interaction/focus/viewport, never constant autoplay. Needs more UX design.

---

## § 27 Scope guardrails + suggested sequencing (cross-cutting)

**Status:** meta — shared decision context for the `§ 17`–`§ 26` cluster.

**Avoid right now:** full timeline editor · complex keyframes · full video editor · multiple masks per decoration (decided against — `media-appearance.md` content-model refactor) · whole-node masking · persistent animation state on every object unless needed · heavy Media Library before basic presets work · constant autoplay on the canvas.

**Suggested sequencing:**
- *Near-term:* **slice 0 — content-model refactor** (`§ 19` → [`media-appearance.md`](architecture/media-appearance.md): contentMask rename, rectangular `opening`, `decorations` list + placement, drop `openingMaskUri`; **refactor-first**) → **slice 1 — static appearance presets** (model + UI decided, `§ 17` + `§ 18` → [`media-presets.md`](architecture/media-presets.md)): resolution + binding + library + apply/save/duplicate/delete (bake-then-unlink), whole-section overrides → then per-field overrides section-by-section → per-section preview polish (with `§ 20`).
- *Next:* animated preset MVP Start→End (`§ 21`) → one strong preset ("Memory → Now") → basic trigger/preview behavior (`§ 26`) → video poster-time = start-time model (`§ 22`).
- *Later:* video scrub (`§ 23`) → share intents (`§ 25`) → batch import + grid placement (`§ 24`) → multi-select align/distribute (`§ 24`) → Media Library (`§ 14`) → advanced animated-timing UI (`§ 21`).

---

## § 29 Audit all `MediaAppearance` editors — DONE 2026-06-29 → [`todo.md § 29`](todo.md)

**Status:** ✅ **audit run 2026-06-29**, captured as a per-section table + cross-cutting findings + prioritized next steps in [`todo.md § 29`](todo.md). Headline results: every section has an editor + context-menu action + session-compound undo + override section-marking; the real gaps are **render coverage** (`colorAdjustments` ⚠️ not applied — only 6/11 fields even editable; `caption` 🚧 not rendered), **Mixed/multi-edit** (only Opacity + CornerRadius are Mixed-aware; the rest edit from the first selected node), **in-editor preview** (none; only preset cards preview), and the **inherited/overridden visual language + per-section reset** (= `§ 20.3 Slice 1c — remaining`). Top recommended next step: wire `colorAdjustments` into the renderer. Original ask preserved below.

**Ask:** classify each `MediaAppearance` editor as one of — *fully implemented* · *UI exists but doesn't apply correctly* · *renderer support exists, editor missing* · *editor exists, preview missing* · *pure stub* · *planned, not started*.

**Sections to inspect:** opacity · cornerRadius · border · shadow · overlays · contentMask · crop · colorAdjustments · opening · decorations · caption · presets / preset binding / overridden sections.

**Suspected gaps to verify:**
- Overlay editor may be missing/incomplete (ties to `§ 20`).
- Mask editor likely needs a real preview.
- Color adjustments may exist in model+editor but **not actually affect rendering** — verify the renderer applies `colorAdjustments`.
- Decorations editor likely needs a better preview (and relates to `§ 28`).
- Preset editor needs cleanup, especially section-override marking.
- **Multi-edit / `MixedValue`** handling per section (`appearance.md § 14`).
- **Undo/redo** for every editor action.
- Bound-preset editing must mark edited sections as **overridden** (`media-presets.md`).

**Desired output:** a concrete checklist per editor — editor name · model field · UI status · renderer status · undo/redo status · preset-override behavior · missing work · recommended next step. Captured as a working doc (likely `todo.md` once the audit runs).

---

## § 30 Sticker representation + implementation

**Status:** open — needs a model decision before building.

**Open question:** is a sticker a `CanvasNode.Media` with `MediaType.STICKER` · a separate `CanvasNode.Sticker` · a preset/decoration type · or a vector/image asset that behaves like media but with different default appearance + library handling?

**Considerations:** stickers should be independently movable/scalable/rotatable; need transparent PNG / SVG support; typically no crop/opening by default; may still want opacity/shadow/border and maybe presets; participate in z-order + selection like other nodes; frame-membership participation TBD; may later ship as themed sticker packs.

**Suggested MVP:** start with `CanvasNode.Media` + `MediaType.STICKER` to reuse transform, z-order, selection, rendering, undo/redo, media import/storage, and appearance basics — with explicit defaults: transparent-image support · no default crop · no opening · no photo-style frame decoration by default · object-level appearance still allowed. (Note: per `video.md`, `MediaType` already carries `VIDEO` with no migration; a `STICKER` variant follows the same no-migration bridge pattern.)

---

## § 31 Object groups (real groups, not just multi-selection)

**Status:** open — distinct from transient multi-selection; needs a scene-graph decision.

**Questions:** persistent `Group` object in the scene graph at all? `CanvasNode.Group` vs. metadata/relation between nodes? How do group transforms work — do children keep **absolute world transforms** or become **relative** to the group? Undo/redo for group/ungroup? Nested groups? How does a frame transforming its members (temporary group) relate to real groups — same mechanism or separate? Interaction with frame membership; with z-order; selection (group-as-whole vs. child-inside-group); does a group carry its own appearance? Exportable/reusable as components later?

**Suggested MVP:** persistent group **metadata with a stable group id**; child nodes keep **absolute world transforms**; group operations apply transforms to all children; the group itself renders nothing initially; **no nested groups** v1; selection can target group-as-whole or individual nodes (refined later). Relationship to frame "temporary group" behavior should be reconciled with `frame-membership.md`.

---

## § 32 Preset library/editor convenience (app-level management)

**Status:** direction **decided 2026-06-29**; implementation pending (slices → `todo.md § 20.3 Slice 2`). Builds on the *already-shipped* MVP preset stack — `MediaPresetStore` (app-level, SharedPreferences, `@Singleton`), `PresetLibrarySheet` (save / apply / duplicate / delete / unlink + apply-over-overrides prompt + 64dp card previews), `PresetDefinitionEditor` (per-section governs-checkbox + concept editors + rename). The model is settled in [`media-presets.md`](architecture/media-presets.md); this section is purely about **management UX convenience**, not the model.

**Problem (today):** the library opens **only** from the context-menu `Presets…` on an all-media selection — you can't manage presets without selecting a photo; it's a cramped `Dialog` (preview + name + four text buttons per card); and `Save` blindly captures whatever sections are non-default with no chance to choose.

**Decided 2026-06-29:**
1. **Two entry points (global + contextual).** Add a **global preset manager** reachable with **no selection** (add / edit / rename / duplicate / delete the app-level library) — exact home TBD (album menu / top-bar overflow / Add sheet). Keep the existing **context-menu `Apply preset`** when media is selected. So *managing* the library and *applying* to objects are separate doors into the same store.
2. **Full bottom sheet, big cards + per-card overflow.** Promote from the `Dialog` to a full-width bottom sheet (or dedicated screen) with large preview cards; **Apply** is the primary action, **Edit / Duplicate / Rename / Delete** move into a per-card overflow (`⋮`) menu so the row isn't crowded. Scales as the library grows.
3. **Quick-save, then refine.** `Name + Save` creates the preset immediately capturing the styled (non-default) sections — today's speed — but a confirm/peek offers **Edit** to adjust exactly which sections it governs. Fast by default, controllable when wanted.

**Open sub-details (not blocking the direction):**
- Exact home of the global entry point.
- **Preview subject when nothing is selected** (global manager has no selection) — use a **bundled sample node** per [`media-presets.md § 10`](architecture/media-presets.md) ("bundled sample only when nothing selected").
- Card layout specifics (preview size, metadata shown — section chips vs. count).
- **Organization** — flat list for MVP; search / sort / folders deferred (revisit when libraries get large).
- Rename surfaced both in-card (overflow) and inside the editor — keep consistent.

**Related (shipped 2026-06-29):** the **in-editor live preview** (`ConceptPreview` in `ConceptEditorSheet`, hold-to-peek compare) closes the audit's `§ 29` gap #4 and shares the `MediaPresetPreview` / `CanvasNodeRenderer` infra these cards use.

---

> Recently graduated out of this file:
> - **§ 28 Decorated media flickers while zooming (content paints first, frame later)** **resolved 2026-06-29** → fixed in code, slices tracked in [`todo.md § 28`](todo.md). Surfaced 2026-06-28 on a tablet: zooming/panning made decorated media flicker — content painted first, the decoration layer popped in a beat later — so media + decoration weren't perceived as one atomic object. Grounding pass found the dominant cause was **async bitmap arrival + LOD remount**, *not* intra-frame phase desync: decoration/mask/overlay bitmaps loaded on a slower suspending `SingletonImageLoader.execute(...)` + `ARGB_8888`-copy path keyed *inside* the `RenderDetail.Full` composable, so they reset on every `Full↔Simplified` cross while the content painter reused Coil's memory cache. **Fix = residency, not compositing:** **28.1** cache-warm/copy-free `loadAppearanceBitmap` (same memory-cached path as content) + **28.2** `AppearanceAssetCache` hoisted to `CanvasScaffold` above the LOD switch, **keyed by asset identity** (one bitmap per asset, shared across nodes), with an LRU cold-tail cap; video poster folded in as `AppearanceAssetKind.VideoPoster`. Verified on-device — flicker gone for decorated images, masked/overlay media, and video posters. **28.3 (atomic offscreen composite — extend `needsLayer` beyond `contentMask` to decorations/opening/overlays) was deferred**, not built: residency alone removed the perceived flicker, so the extra offscreen buffer's memory/GPU cost isn't justified; kept in `todo.md § 28.3` as a future lever only if intra-frame phase desync is later observed on real media. (Separately, the playing-**video** freeze-on-zoom flicker was solved via always-offscreen `VideoSurfaceChrome` + authoritative `VideoPlaybackController.setFrozen` during gestures — `todo.md` video section.)
> - **§ 19 Content-model refactor (decoration stack + mask/opening dedup)** **decided 2026-06-21** → `docs/architecture/media-appearance.md` ("content-model refactor" block); implementation pending, **refactor-first (before preset slice 1)**. Resolutions: five stages — node rect → **`opening`** (rectangular content-area slot / resize, no mask) → **`crop`** (fit media in the area) → **`contentMask`** (arbitrary-shape clip of *content only*) → **`decorations`** (visual layers around/above/below, not clipped). Model: `alphaMask` → **`contentMask`** (rename, keep `@SerialName("alphaMask")` → no data migration; clips content not node); `frameDecoration: MediaFrameDecoration?` → **`decorations: List<MediaDecoration>`** (pure visual layer: `id` + `assetUri` + `opacity` + `mode` + `slice*` + `placement: Above|Below`); new rectangular **`opening: MediaOpening?`**; **remove `openingMaskUri`** (redundant with `contentMask`). **No whole-node mask** (clip content, keep decorations whole — scrapbook behavior). **Single** contentMask + opening; decorations carry neither. Render order: Below → content(opening+crop+contentMask) → overlays → Above → border. Migration: legacy `frameDecoration` → one-item list + `opening`; `openingMaskUri` → `contentMask`. Item-level stack overrides deferred (stable `id`s make them feasible later; a *local additive layer* ≠ a scalar override — `media-presets.md § 6`). Decoration-list editor tracks the overlay editor (§ 20).
> - **§ 18 Preset library + detail/editor UI** **decided 2026-06-21** → `docs/architecture/media-presets.md § 10` (implementation pending). UI companion to § 17. Resolution: **three surfaces reusing the existing per-concept `ConceptEditorSheet` composables** — (1) object editing stays the per-concept sheets, made preset-aware (inherited/overridden + reset per concept; editing creates the override); (2) preset editing = a new **aggregate preset-editor sheet** (sections + governs-checkbox + expandable concept editors), entered explicitly from a library card so edit-preset and edit-object are **separate surfaces** (kills the apply-vs-edit ambiguity); (3) **library** = card sheet from a new `Apply Preset` context-menu entry. A bound object surfaces its link via a context-menu **`Preset: <name>` section** (Apply other · Edit · Unlink · Reset all); `Save as preset` opens the preset editor pre-checked with the object's non-default sections. **Apply over existing overrides prompts** `Replace look` (clear) vs `Keep my changes`; fresh apply doesn't prompt. **Preview subject:** single → that media; multi → first selected; bundled **sample only when nothing selected**; cards use the same subject. Inherited = muted + preset chip, overridden = active + reset ↺, mixed = "Mixed" (`appearance.md § 14`); slice-1 whole-section, per-field later. Remaining (card layout, per-section preview rendering, empty-state) is implementation detail.
> - **§ 17 Media Appearance Presets** **design decided 2026-06-21** → `docs/architecture/media-presets.md` (new source of truth); implementation pending. Resolution: **live link + overrides** (a node binds to a preset via `PresetBinding(presetId, overridden)`; effective appearance resolves preset ∪ per-node overrides; editing a preset updates linked nodes unless overridden) — chosen over the simpler stamp/copy model. Presets are **sectioned/partial** (`MediaStylePreset.sections` governs whole sections; Apply overwrites only those). Override **target is per-field/leaf-path** (`AppearancePath`), with a staged **hybrid boundary**: per-field for Opacity/CornerRadius/ColorAdjustments/Border/Shadow, whole-unit for Crop/AlphaMask, **whole-stack** for Overlays + FrameDecoration (both are layer stacks; item-level overrides deferred until layer items have stable IDs — § 19). **Slice 1 ships whole-section override UI only**; per-field UI added section-by-section (ColorAdjustments first). **Deletion = bake-then-unlink** (stamp resolved values into bound nodes, clear binding; no visual break). **Undo:** canvas stack covers binding + node overrides; preset *definition* edits are app-global and stay off the canvas undo stack. **Renderer** receives a resolved concrete `MediaAppearance` (resolution happens before the render boundary; editors additionally read binding/override metadata). **Single appearance-level media mask** — decoration layers don't each clip the media (§ 5 of the doc, § 19 here). App-level storage first; asset-ref portability ties to § 14. UI decided in § 18 (→ `media-presets.md § 10`); related open topics: § 19 (decoration stack), § 20 (overlay editor). Implementation staging in `media-presets.md § 7`; actions tracked in `todo.md § 20.3`.
> - **§ 13 Video MVP — Edit-vs-View playback** **design decided 2026-06-17** → `docs/architecture/video.md` (new source of truth); implementation pending. Resolution: video is **playable "living media" on the canvas, not a video editor**; a video node behaves exactly like an image node for transform/selection. Specific resolutions: (Model bridge) **no migration** — `CanvasNode.Media` already carries `mediaRefId` (a raw URI today) + `mediaType` (with `VIDEO` already in the enum); MVP activates the `VIDEO` path and keeps `mediaRefId` a raw URI, no `MediaAsset` indirection, no new field — pointed toward the future Media Library (§ 14) without building it. (Poster) **zero-storage** — lazy frame extraction via Coil's `coil-video` decoder on the existing Coil path; no import step, no stored poster, no model field; custom poster deferred. (Edit vs. View) View/Present taps anywhere → play/pause; **Edit taps select**, playback comes from a **node-local play button on the selected node's poster** that yields to transform handles and never extends selection. (Concurrency) **simultaneous playback via a bounded player pool** built now — LOD bounds candidates to `RenderDetail.Full`, a pool of *K* players (K from a device decoder-capability probe, since hardware `MediaCodec` decoders are capped ~4–8) with an eviction policy and poster fallback when exhausted; a deliberate scope expansion past "keep the first slice small." (Playback host) `AndroidView`-hosted Media3 `ExoPlayer`, mounted only at `RenderDetail.Full` for pool-assigned playing nodes; playback + pool state in a `CanvasScaffold`-level holder keyed by `nodeId`, never in domain models (per § 11). New deps: Media3 ExoPlayer + `coil-video`. Deferred (not first slice): loop/mute/start-position, custom poster, inline controls, autoplay-on-frame-entry, pause-on-leave, `AlbumVideoDefaults`. Next deliverable is the implementation plan; slices in `todo.md § 27`.
> - **§ 15 CropEdit stabilization — invariant + cancel/undo** **decided 2026-06-17** → `docs/architecture/editor-tools.md § 4.8` (**Persistence + invariant**, **Undo granularity**, **Cancel**); implementation pending. Grounding pass against `CanvasViewModel.kt` found the invariant guard partially wired and a live cancel↔undo inconsistency (each crop gesture pushed its own `Compound` entry while `CancelCropEdit` restored out-of-band, leaving orphan entries on the undo stack). Resolutions: (Invariant) strengthened from "exactly one media selected" to **"the selection is exactly the node in `entrySnapshot`"**; `enforceCropEditInvariant()` must also fire on **View/Present switch** (`SetMode`), on **selection → a different single media** (`selectedMediaId != entrySnapshot.nodeId`), and after **Undo/Redo** — three gaps to fix (the already-wired cases: empty/multi/frame-selected, delete, tool-switch). Re-anchoring to a newly-selected node is a deferred future UX, not part of stabilization. (Undo) **session-compound** — per-gesture history is suppressed while `CropEdit` is active; the whole session is **one `Compound` entry pushed on Apply/exit**; **Cancel pushes nothing** and leaves the stack clean; in-session ops are not individually undoable. Consistent with the per-popup-session compound-undo convention. Implementation slice in `todo.md § 20.9`.
> - **§ 5 Album storage & cloud sync** **fully decided 2026-06-03** → `docs/architecture/cloud-sync.md` (new source of truth). Resolution: **local-first automatic snapshot sync with conflict-safe editing** — not manual-only backup, not real-time collaboration. Specific resolutions: (Open flow) cloud-connected albums open the local copy immediately in View mode but keep Edit mode disabled until the remote head-revision check completes; offline editing requires an explicit `Edit offline anyway` confirmation. (Conflict policy) divergence preserves the local branch as a separate conflict-copy album (`"Italy Trip" / "Italy Trip — local conflict copy"`) and restores the primary album from the remote head — never overwrite, never auto-merge. (Sync triggers) open-time revision check + post-commit automatic sync at the `FinishInteraction` boundary + retry on network return + manual `Sync now`; debounced/coalesced, background lifecycle is best-effort only. (Model direction) **rejected `storageMode = Local | GoogleDrive`** on `Album`; cloud connection is a separate optional `RemoteBinding` (sealed, per-provider) keyed by stable `AlbumId`. Conflict detection uses **revision lineage**, not timestamps — `(headRevisionId, parentRevisionId)` on each stable commit, with the local `FinishInteraction` boundary as the commit boundary. Per-album = per-Drive-folder. (Future encryption) architecture must remain compatible with end-to-end / zero-knowledge encryption: remote stores opaque blobs only; sync MUST NOT depend on the provider inspecting or merging plaintext — which is exactly why conflict-copy preservation is chosen over auto-merge. Deferred to the implementation slice (not blocking this decision): OAuth flow, concrete `RemoteBinding` field set, debounce window, conflict-copy naming beyond the suffix example, quota/chunking, multi-device advisory hints, encryption key management UX. Documentation-only change; no Google Drive code lands in the current `EditorState` / `ActiveTool` / `Eraser` work. Implementation slices listed (deferred) in `todo.md § 26`.
> - **§ 9 Tool-based UI layout** **fully decided 2026-06-03** → `docs/architecture/editor-surfaces.md` (new source of truth). Replaces the fixed-zone framing (`left toolbar = tools / topbar = settings / right panel = properties`) with **five logical editor surfaces** that have responsibilities, not placements: `GlobalChromeSurface`, `ToolControlSurface`, `SelectionActionSurface`, `ConceptEditorSurface`, `AddContentSurface`. One baseline layout for both phone and tablet — no separate tablet editor architecture. Future wide-screen / configurable workspaces are alternative *placements* of the same logical contributions, not a second editor. Specific resolutions: (Q1) merges with `todo.md § 5d` — § 5d demoted from "tablet MVP architecture" to "deferred future placement for `ConceptEditorSurface`." (Q2) phone uses a horizontal `ToolControlBar` (active-tool selector + primary controls on one line) instead of a floating switcher; bar is lazy — doesn't render until a second functional tool or real `Selection` settings exist; expected trigger is Object-mode `Eraser`. (Q3) `EditorTool` stays flat — brush variants / shape primitives / eraser modes are settings, not separate tools; `VectorEdit` and `MaskEdit` are context-gated, exact discoverability UX deferred. Three contribution categories deliberately separated: `EditorActionCatalog` (discrete actions, already shipped) ≠ `ToolControl` (retained editable values, future surface-agnostic model — boundary recorded, abstraction deferred until Eraser ships) ≠ concept-editor content composables (reusable across surfaces). Documentation-only change; no code in this slice. Implementation gates: `ToolControlBar` deferred to second-tool slice.
> - **§ 8 `MaskNode` editing UX + `MaskEdit` gesture map** **fully decided 2026-06-03** → `docs/architecture/editor-tools.md § 4.7` (full gesture map) + `docs/architecture/appearance.md § 12.10` (locked constraints). Resolutions: (1) **Own UX**: dedicated `MaskEdit` tool that owns *both* creation and editing — selection-aware entry (empty → creation mode rubber-band; one `MaskNode` → edit mode; other → disabled slot). Geometry picker mirrors `Shape`: `Rect` / `Ellipse` / `Path` (anchored bezier) / `Free` (raw freehand, simplified on lift). Primitives edit via corner / edge handles; path masks edit per-anchor / per-handle like `VectorEdit`. (2) **Binding**: implicit — every sibling within the same frame/group below the `MaskNode` in z-order is masked; no per-sibling selection UI; placement + z-order *is* the binding. (3) **Z-order tiebreaker**: a `MaskNode` at z=N clips siblings at z<N only (locked over the "all in group" alternative). (4) **Multi-mask composition**: union — a sibling pixel is visible wherever any above-it `MaskNode` reveals it; no subtractive masks in MVP. (5) **Preview policy**: commit-only — masked siblings re-clip on gesture lift, not continuously during drag (the `MaskNode`'s own outline does update live). Stay-in-tool persistence; long-press always opens the global popup; two-finger always navigates. Topbar = primitive picker + aspect-ratio toggle (primitives only); feather + future mask-source options (image / gradient / procedural per § 12.2) live on the popup. Implementation depends on `MaskNode` data-model landing per `appearance.md § 12.8`.
> - **§ 11 `EditorState` container** **decided 2026-06-02; extraction shipped same day** → `docs/architecture/editor-tools.md § 7.1`. The earlier flat grab-bag proposal (`activeTool` + `selectedObjects` + `activeAppearanceEditor` + per-tool transient states + snapping + transform handles, all siblings) is **superseded**. The decision split editor concerns three ways:
>   1. **Editor-session state** → flat `EditorState` under `CanvasState.editor`. Owns `mode`, `activeTool`, `selectedNodeIds`, `selectionRect`, `groupSelectionTransform`, `frameEditOptions`, `contextAnchorNodeId`. Read by canvas + overlays + action catalog.
>   2. **Tool settings + transient interaction state** → added on demand when a tool actually requires them, via dedicated `toolSettings` / `activeInteraction` fields on `EditorState`. Do NOT encode them inside `EditorTool` variants (which conflates identity, settings, and active-gesture state). Do NOT predeclare speculative state for tools that don't yet exist.
>   3. **UI-surface state (popups / bottom sheets / dialogs)** → stays local to `CanvasScaffold` as `remember` cells (`mediaApprEditing`, `frameBgEditing`, `contextMenuRequest`, `showAddSheet`, etc.). The same editor operation may later surface as a phone bottom sheet or a tablet docked panel; coupling that decision to editor-session state would leak presentation concerns into the model. **Exception:** `contextAnchorNodeId` belongs on `EditorState` because the canvas (`SelectionOverlay` halo) reads it, even though the popup itself stays in `CanvasScaffold`.
>
>   Selection is flat on `EditorState` for now (`selectedNodeIds` + `selectionRect` + `groupSelectionTransform`). A dedicated `SelectionState` extraction is deferred until `SelectionTool` accumulates substantial own state — Rectangle / Lasso modes, `Intersects` / `FullyContained` rules, in-progress lasso path, hover, additive flag, anchor-level selection. Two-level nesting (`canvasState.editor.selection.selectedNodeIds`) was rejected today because the cohesion gain didn't yet justify the call-site cost. Types live under `feature/canvas/editor/`, not `domain/model/`. The earlier "wait until 3+ tools accumulate transient state" trigger in `editor-tools.md § 7.1` is also superseded — extraction landed before any per-tool transient state existed, because the editor-session subset of `CanvasState` was already coherent on its own.
>
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
