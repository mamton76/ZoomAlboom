package com.mamton.zoomalbum.feature.canvas.editor

/**
 * A field value summarized across a multi-node selection.
 *
 * - [Same] — every node agrees on this value. Editors display it as a concrete
 *   number / color / option.
 * - [Mixed] — at least two nodes disagree. Editors display the Figma-style
 *   "Mixed" affordance (numeric field reads "Mixed", slider shows no thumb,
 *   dropdown shows "Mixed" as the selected entry). Touching the control commits
 *   the new value to every node in the selection — destructive unify per
 *   `docs/architecture/appearance.md § 14.2`.
 *
 * Foundation for Phase B/C multi-edit. Phase B introduces the type; Phase C
 * wires reductions (`List<T>.toMixedValue()`) and threads the type through the
 * appearance sheets' controls.
 */
sealed interface MixedValue<out T> {
    data class Same<T>(val value: T) : MixedValue<T>
    data object Mixed : MixedValue<Nothing>
}

/**
 * Reduce a collection of per-node values into a single [MixedValue].
 * - Empty → [MixedValue.Mixed]: degenerate case (no source values to summarize);
 *   callers are expected to size-guard before reducing, but Mixed is the safest
 *   fallback so a UI control renders an indeterminate state instead of a bogus
 *   "Same" value.
 * - All equal → [MixedValue.Same] with the shared value.
 * - At least two differ → [MixedValue.Mixed].
 */
fun <T> Iterable<T>.toMixedValue(): MixedValue<T> {
    val iter = iterator()
    if (!iter.hasNext()) return MixedValue.Mixed
    val first = iter.next()
    while (iter.hasNext()) {
        if (iter.next() != first) return MixedValue.Mixed
    }
    return MixedValue.Same(first)
}
