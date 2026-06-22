package com.mamton.zoomalbum.domain.model

/**
 * Pure preset resolution — combines a node's own [MediaAppearance] with its bound
 * [MediaStylePreset] per section. See `docs/architecture/media-presets.md`.
 *
 * Applied at the render boundary (renderers keep taking a concrete
 * `MediaAppearance`); editors read the raw node + binding for override state.
 */

/** Returns a copy of this appearance with [section] taken from [from]. */
fun MediaAppearance.withSection(section: AppearanceSection, from: MediaAppearance): MediaAppearance =
    when (section) {
        AppearanceSection.Opacity -> copy(opacity = from.opacity)
        AppearanceSection.CornerRadius -> copy(cornerRadius = from.cornerRadius)
        AppearanceSection.Crop -> copy(crop = from.crop)
        AppearanceSection.ColorAdjustments -> copy(colorAdjustments = from.colorAdjustments)
        AppearanceSection.Overlays -> copy(overlays = from.overlays)
        AppearanceSection.ContentMask -> copy(contentMask = from.contentMask)
        AppearanceSection.Opening -> copy(opening = from.opening)
        AppearanceSection.Decorations -> copy(decorations = from.decorations)
        AppearanceSection.Border -> copy(border = from.border)
        AppearanceSection.Shadow -> copy(shadow = from.shadow)
        AppearanceSection.Caption -> copy(caption = from.caption)
    }

/** Returns a copy with each section in [sections] taken from [from]. */
fun MediaAppearance.withSections(
    sections: Set<AppearanceSection>,
    from: MediaAppearance,
): MediaAppearance = sections.fold(this) { acc, s -> acc.withSection(s, from) }

/** True when [section]'s value is equal between the two appearances. */
fun MediaAppearance.sectionEquals(section: AppearanceSection, other: MediaAppearance): Boolean =
    when (section) {
        AppearanceSection.Opacity -> opacity == other.opacity
        AppearanceSection.CornerRadius -> cornerRadius == other.cornerRadius
        AppearanceSection.Crop -> crop == other.crop
        AppearanceSection.ColorAdjustments -> colorAdjustments == other.colorAdjustments
        AppearanceSection.Overlays -> overlays == other.overlays
        AppearanceSection.ContentMask -> contentMask == other.contentMask
        AppearanceSection.Opening -> opening == other.opening
        AppearanceSection.Decorations -> decorations == other.decorations
        AppearanceSection.Border -> border == other.border
        AppearanceSection.Shadow -> shadow == other.shadow
        AppearanceSection.Caption -> caption == other.caption
    }

/** Sections whose value differs from a default [MediaAppearance] — used by "Save as preset". */
fun MediaAppearance.nonDefaultSections(): Set<AppearanceSection> {
    val default = MediaAppearance()
    return AppearanceSection.entries.filterTo(mutableSetOf()) { !sectionEquals(it, default) }
}

/**
 * The effective appearance for a media node: its own [MediaAppearance] with every
 * preset-governed, non-overridden section replaced by the preset's value.
 *
 * Returns the node's own appearance unchanged when unbound or when the bound
 * preset id is missing (dangling binding → graceful fallback to the node's own
 * values, which `apply` stamps with the last resolved look).
 */
fun resolveMediaAppearance(
    node: CanvasNode.Media,
    presetsById: Map<String, MediaStylePreset>,
): MediaAppearance? {
    val binding = node.presetBinding ?: return node.appearance
    val preset = presetsById[binding.presetId] ?: return node.appearance
    val own = node.appearance ?: MediaAppearance()
    val governed = preset.sections - binding.overridden
    return own.withSections(governed, preset.appearance)
}

/**
 * A render-ready copy: a bound media node with its effective appearance baked in.
 * Non-media / unbound nodes are returned unchanged. The original (with
 * `presetBinding` + raw `appearance`) stays in `_allNodes` for editors.
 */
fun CanvasNode.resolvedForRender(presetsById: Map<String, MediaStylePreset>): CanvasNode =
    if (this is CanvasNode.Media && presetBinding != null) {
        copy(appearance = resolveMediaAppearance(this, presetsById))
    } else {
        this
    }
