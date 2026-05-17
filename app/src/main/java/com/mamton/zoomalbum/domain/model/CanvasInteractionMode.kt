package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Global canvas mode. Gates which contextual interactions are reachable.
 *
 *  Edit — full editor: select / move / resize / rotate / add content.
 *  View — read-only navigation: tap a node to focus it (animated camera fit);
 *         pan / pinch / rotate gestures still work; no selection overlays,
 *         no handles, no contextual action bar.
 *
 * Persisted to `ide_workspaces` (UI state, not album content).
 */
@Serializable
enum class CanvasInteractionMode { Edit, View, Presentation }
