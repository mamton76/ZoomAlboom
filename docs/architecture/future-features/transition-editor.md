# Transition Editor — Architecture Concept

> **Status:** Future feature (post-MVP)
>
> Related: [future-ideas.md](../../product/future-ideas.md#navigation--transitions) | [overview](../overview.md) | [data-model](../data-model.md) | [navigation](../navigation.md) | [rendering](../rendering.md)

## Summary

A cinematic transition editor that lets users define and customize camera paths between frames on the infinite canvas. Transitions are spatial objects — visible paths in world space — not timeline tracks.

## Mental Model

A transition is a **camera path from Frame A to Frame B**. By default it is automatic (computed from frame positions with sensible easing). When made explicit, it appears as a styled line on the canvas that the user can tap to edit.

Three levels of complexity, progressively disclosed:

| Level | What the user sees | Data created |
|-------|-------------------|--------------|
| 0 — Auto | Camera animates to frame via `Transform.toCamera()`. No visible path. | Nothing (computed) |
| 1 — Explicit link | Straight path line on canvas. Bottom sheet for duration + preset. | `FrameTransition` with defaults |
| 2 — Keyframed | Path with draggable waypoints, per-segment easing, optional Bezier curves. | `FrameTransition` + `TransitionWaypoint` + `TransitionSegment` |

## UX Concept

### Entry Points

| Action | Result |
|--------|--------|
| Long-press a frame edge | Connector handles appear; drag to another frame to create transition |
| FrameList bottom sheet: "Link" button | Select two frames, auto-create transition |
| TopBar toggle: "Show transitions" | Reveal all transition paths on canvas |

### Transition Inspector (tap the path)

Bottom sheet (same pattern as `AddContentBottomSheet`):

```
┌──────────────────────────────────────┐
│  Transition: "Family" → "Trip 2024"  │
├──────────────────────────────────────┤
│  Duration: [===●=========] 2.0s      │
│                                      │
│  Style:  ○ Calm   ● Soft   ○ Fast   │
│          ○ Linear ○ Custom           │
│                                      │
│  [▶ Preview]         [Delete Link]   │
└──────────────────────────────────────┘
```

- **Duration** — slider, 0.3s–8.0s. Default: auto-calculated from distance.
- **Presets:**
  - *Calm* — ease-in-out, 1.2x auto duration, no zoom shift
  - *Soft cinematic* — ease-in-out, slight zoom-out at midpoint (camera "breathes")
  - *Fast* — ease-out, 0.5x auto duration, snappy
  - *Linear* — constant speed
  - *Custom* — unlocks per-segment editing
- **Preview** — plays the animation in-place, camera returns after.

### Waypoint Inspector (tap an intermediate point)

```
┌──────────────────────────────┐
│  Waypoint 2 of 3             │
│                              │
│  Zoom:     [===●===] 0.4×   │
│  Rotation: [===●===] 15°    │
│  Pause:    [===●===] 0.0s   │
│                              │
│  [Camera Preview]  [Delete]  │
└──────────────────────────────┘
```

- **Camera Preview** — translucent viewport rectangle on canvas showing what the user would see at this waypoint's camera state.
- **Zoom / Rotation** — `null` = interpolate from neighbors (user doesn't have to set them).

### Segment Inspector (tap a line between two waypoints)

Inline popover: duration slider + easing picker for that segment only.

### Bezier Handles

When a waypoint is selected, two handle dots extend for tangent control. These control **spatial path shape**, not temporal easing (those are separate concepts).

## Data Model

### New Domain Models

```kotlin
@Serializable
data class FrameTransition(
    val id: String,
    val fromFrameId: String,               // CanvasNode.Frame id
    val toFrameId: String,                 // CanvasNode.Frame id
    val durationMs: Long = 0,              // 0 = auto-calculate from distance
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val preset: TransitionPreset = TransitionPreset.SOFT,
    val waypoints: List<TransitionWaypoint> = emptyList(),
    val segments: List<TransitionSegment> = emptyList(),
    // segments.size == waypoints.size + 1 when waypoints exist,
    // or empty (= use global easing for the single implicit segment)
)

@Serializable
data class TransitionWaypoint(
    val id: String,
    val position: WorldPoint,              // cx, cy in world space
    val zoom: Float? = null,               // null = interpolate from neighbors
    val rotation: Float? = null,           // null = interpolate from neighbors
    val pauseMs: Long = 0,                 // dwell time at this point
    val handleIn: WorldPoint? = null,      // Bezier control point (incoming)
    val handleOut: WorldPoint? = null,     // Bezier control point (outgoing)
)

@Serializable
data class TransitionSegment(
    val durationMs: Long = 0,              // 0 = proportional share of total
    val easing: EasingType = EasingType.EASE_IN_OUT,
)

@Serializable
data class WorldPoint(val x: Float, val y: Float)

@Serializable
enum class EasingType {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT
}

@Serializable
enum class TransitionPreset {
    CALM,            // ease-in-out, 1.2x auto duration, no zoom shift
    SOFT,            // ease-in-out, 1.0x auto duration, slight zoom-out mid-path
    FAST,            // ease-out, 0.5x auto duration, no zoom shift
    LINEAR,          // linear, 1.0x auto duration
    CUSTOM           // per-segment overrides active
}
```

### Scene Graph Integration

Transitions are a **sibling list** next to nodes (not nested inside frames):

```json
{
  "albumId": 123,
  "viewport": { ... },
  "nodes": [ ... ],
  "transitions": [
    {
      "id": "tr_1",
      "fromFrameId": "frame_1",
      "toFrameId": "frame_2",
      "durationMs": 2000,
      "preset": "SOFT",
      "easing": "EASE_IN_OUT",
      "waypoints": [ ... ],
      "segments": [ ... ]
    }
  ]
}
```

**Why not inside Frame?** A transition connects *two* frames — storing it in one creates ownership ambiguity. A separate list is easier to query and supports future "play entire album" sequencing.

### Integration with Existing Architecture

- `CanvasNode.Frame` — unchanged, no new fields.
- `SceneGraphSerializer` — gains optional `transitions` field (default empty list, backward compatible via `ignoreUnknownKeys`).
- `Camera` — reused directly. Each waypoint and frame endpoint produces a `Camera` via `Transform.toCamera()`. Interpolation happens between `Camera` values.
- `CanvasCommand` — gains `AddTransition` / `RemoveTransition` / `UpdateTransition` variants for undo/redo.

### Auto-Duration Formula

When `durationMs == 0`:

```
autoDuration = clamp(
    baseMs = 600,
    distanceFactor = worldDistance(frameA.center, frameB.center) * 0.8,
    zoomFactor = abs(log2(scaleA / scaleB)) * 400,
    min = 400ms,
    max = 5000ms
)
```

### Rendering

Transition paths are rendered as styled `Path` objects in `drawBehind` at a low z-index. No new rendering infrastructure — same `graphicsLayer` pipeline as nodes. Paths live in world space, transform with the camera.

## MVP vs. Advanced Phases

### MVP (v1) — Simple Links

| Feature | Notes |
|---------|-------|
| Auto-transitions (no data model) | `Transform.toCamera()` with fixed easing. Already partially specced. |
| Create explicit transition link | Drag-connect or FrameList action. |
| Global duration + preset picker | Bottom sheet with slider + 4 presets. |
| Preview button | Play transition, camera returns after. |
| Show/hide transition paths | TopBar toggle. Straight lines only. |
| Delete transition | Remove the link. |

MVP does **not** include: waypoints, per-segment easing, Bezier curves, custom preset, pause, viewport preview rectangles.

### v2 — Waypoints

- Add waypoints by tapping path
- Drag waypoints on canvas
- Per-waypoint zoom + rotation
- Proportional duration redistribution
- Camera preview rectangle at waypoint

### v3 — Curves & Polish

- Bezier handles on waypoints
- Per-segment easing
- Pause at waypoint (dwell time)
- "Play album" mode — sequential playback of all transitions in FrameList order

## Risks & Mitigations

### Complexity creep
**Risk:** Feature evolves into a video NLE timeline.
**Mitigation:** MVP has one sheet, one slider, four presets. No timeline. Waypoints/segments are progressive disclosure behind the "Custom" preset gate.

### Motion sickness
**Risk:** Rapid zoom changes, rotation swings, or long transitions cause nausea.
**Mitigations:**
- Clamp rotation delta — default transitions never rotate more than ±45° total; pick shorter angular path.
- Limit zoom rate — no more than 4x zoom change per second; auto-extend duration if exceeded.
- "Soft cinematic" default — slight midpoint zoom-out mimics a dolly move, gentler on vestibular perception.
- Preview before committing.
- "Reduce motion" accessibility setting — forces transitions to simple cut/crossfade.

### Discoverability
**Risk:** Users don't know transitions are editable.
**Mitigation:** One-time tooltip on first frame-to-frame navigation: "You can customize how the camera moves between frames."

### Path becomes stale after frame move
**Risk:** User moves a frame; path endpoint is wrong.
**Mitigation:** Endpoints are always derived from frame's current `Transform`, not stored as absolute positions. Only waypoints are absolute (proportional repositioning is a v3 feature).

### Orphaned transitions
**Risk:** Frame deleted, transition still references it.
**Mitigation:** `RemoveNode` command cascade-deletes transitions referencing the removed frame (add `RemoveTransition` command variant).

## Simplifications That Preserve the Magic

1. **Skip curves in MVP.** Straight lines + good easing already feel cinematic.
2. **Auto-derive duration from distance.** Slider is override, not required input.
3. **Four presets replace a full easing curve editor.** Covers 90% of use cases.
4. **Waypoint zoom/rotation default to null (interpolate).** Users can bend the path without thinking about camera state.
5. **No timeline UI.** Everything is spatial. Duration is a single slider. This is the key simplification.
6. **Rendering is just a `Path` in `drawBehind`.** No new infrastructure.
7. **"Play album" is just sequential frame navigation.** FrameList order defines sequence; transitions define *how*.
