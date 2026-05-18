# ZoomAlboom UI Editing & Presentation Profiles Notes

## 1. Presentation Profiles for Albums and Frames

We discussed the future need for **presentation profiles**: different viewing configurations for different device classes and orientations.

Possible profiles:

- `phone_portrait` — phone in portrait orientation, usually 9:16
- `phone_landscape` — phone in landscape orientation, usually 16:9
- `tablet_portrait`
- `tablet_landscape`
- later: `web_desktop`, `tv`, `print/export`

### Album-level presentation profiles

In the future, an album may define:

- which presentation profiles it supports;
- which profile is the primary/default one;
- fallback behavior if the current device does not match any profile;
- preferred profile order;
- frame fitting behavior:
    - `fit`
    - `fill`
    - `crop`
    - `show surrounding canvas`
    - `blurred backdrop`

Example future model:

```kotlin
data class AlbumPresentationSettings(
    val supportedProfiles: List<PresentationProfile>,
    val defaultProfileId: PresentationProfileId,
    val fallbackMode: PresentationFallbackMode
)
```

### Frame-level presentation profiles

In the future, a frame may define:

- in which presentation profiles it is visible;
- profile-specific camera/crop/transform;
- profile-specific object visibility;
- later, multiple presentation shots for a single logical frame.

Example future model:

```kotlin
data class FramePresentationVariant(
    val profileId: PresentationProfileId,
    val cameraBounds: RectWorld,
    val visibleNodeIds: Set<NodeId>? = null,
    val hiddenNodeIds: Set<NodeId>? = null
)
```

### MVP decision

Full presentation profile support is **not required for MVP**.

For MVP, it is enough to keep frames as spatial objects on the infinite canvas:

```kotlin
Frame = rectangular scene on the infinite canvas
```

When navigating to a frame, the camera simply fits the frame into the current viewport:

```kotlin
cameraTarget = fitFrameIntoCurrentViewport(frame.bounds, viewport)
```

The important architectural rule:

> Do not hardcode the assumption that a frame always matches the current device screen or aspect ratio.

### Minimal future-proofing for MVP

Add a small abstraction layer for camera resolution:

```kotlin
interface FrameCameraResolver {
    fun resolveCameraTarget(
        frame: Frame,
        viewport: Viewport
    ): CameraTarget
}
```

For MVP, the resolver can be simple:

```kotlin
class DefaultFrameCameraResolver : FrameCameraResolver {
    override fun resolveCameraTarget(
        frame: Frame,
        viewport: Viewport
    ): CameraTarget {
        return fitFrameIntoViewport(frame.bounds, viewport)
    }
}
```

Later, this resolver can use presentation profiles without changing the core navigation flow.

Optional lightweight fields for future compatibility:

```kotlin
enum class FrameFitMode {
    FIT,
    FILL,
    CUSTOM_CAMERA // future
}

enum class FrameAspectHint {
    FREE,
    CURRENT_SCREEN,
    PHONE_PORTRAIT_9_16,
    LANDSCAPE_16_9,
    TABLET_4_3,
    SQUARE_1_1
}
```

Current recommendation:

- Do **not** implement full profiles in MVP.
- Do keep the architecture open for them.
- Do not bind frame geometry to a specific device screen.
- Do version the scene graph so profiles can be added later.

---

## 2. UI Editing Redesign Is Becoming Necessary

The current editing UI based mostly on bottom sheets feels insufficient for serious editing.

The product likely needs two different editing approaches:

1. **Tablet-first full editor**
2. **Phone-compatible quick editor**

The tablet should be the primary target for full album editing.

The phone should support viewing, quick edits, and contextual actions, but should not try to replicate the full tablet workspace.

---

## 3. Tablet Editing UI

The tablet UI should move toward an IDE/Figma/Miro-style editor.

Possible panels:

- Media Library
- Frame Navigator
- Properties Panel
- History / Undo-Redo
- Appearance Editor
- Selection Tools
- Background Editor
- Layers, later
- Frame Settings

The tablet UI may use:

- docked panels;
- floating panels;
- collapsible side panels;
- tabbed panel groups;
- customizable workspace layout.

The canvas remains the central editing surface, while tools and inspectors live around it.

---

## 4. Phone Editing UI

The phone UI should remain lightweight and contextual.

The phone is mainly for:

- viewing albums;
- quick media adding;
- simple object edits;
- contextual actions;
- simple selection operations;
- quick frame creation or navigation.

The phone should avoid large permanent panels.

Instead, it may use:

- floating action buttons;
- contextual popups near the finger;
- compact menus;
- bottom sheets only for deeper editing forms;
- clean full-screen viewing mode.

Important conclusion:

> Bottom sheets should not be the only editing mechanism. Primary object actions should likely be available through contextual popup menus.

---

## 5. Current Gesture Logic

The current selection behavior should be respected.

### Short tap

A short tap changes/replaces the current selection.

Example:

```text
Short tap on object A -> object A becomes the selected object.
Short tap on object B -> selection moves from A to B.
```

### Long tap + drag

Long tap followed by drag creates a selection area.

This is used for selecting multiple objects on the canvas.

### Long tap on object

If the user long-taps an object:

- if there is one object under the finger, it is added to the current selection;
- if there are multiple objects under the finger, an overlap picker is shown so the user can choose which objects to add to the selection.

This is important because long tap is already part of the selection system.

Therefore, contextual menus should appear **after the selection action is resolved**, not before it.

---

## 6. Context Menu Principle

The context menu should be based on the **current selection**, not only on the object under the finger.

Flow:

1. User long-taps an object.
2. Selection is created or updated.
3. A contextual popup appears near the finger or selected object.
4. The menu content depends on the selection type.

Selection types:

- no selection;
- single media object selected;
- single frame selected;
- multiple objects selected;
- mixed selection: media + frames.

---

## 7. Context Menu Use Cases

### Case 1: Nothing is selected + long tap on a single media object

Result:

- the media object becomes selected;
- a contextual popup appears.

Possible actions:

- `Edit media`
- `Edit appearance`
- `Edit mask / crop`
- `Add / edit overlay`
- `Edit border`
- `Edit shadow`
- `Replace media`
- `Duplicate`
- `Delete`

### Case 2: Nothing is selected + long tap on a frame

Result:

- the frame becomes selected;
- a contextual popup appears.

Possible actions:

- `Edit frame`
- `Edit frame appearance`
- `Edit frame background`
- `Navigate to frame`
- `Create / edit frame contents`
- `Duplicate frame`
- `Delete frame`

### Case 3: One object is selected + long tap on another object

Result:

- the second object is added to the current selection;
- selection becomes a multi-selection;
- a group context menu appears.

Possible actions:

- `Edit common appearance`
- `Create frame around selection`
- `Align`
- `Distribute`
- `Duplicate selection`
- `Delete selection`
- `Clear selection`

### Case 4: One object is selected + long tap on the same object

Result:

- the selection stays the same;
- a context menu for that object appears.

For media:

- `Edit media`
- `Edit appearance`
- `Edit mask / crop`
- `Add / edit overlay`
- `Replace media`
- `Duplicate`
- `Delete`

For frame:

- `Edit frame`
- `Edit frame appearance`
- `Edit frame background`
- `Navigate to frame`
- `Duplicate frame`
- `Delete frame`

### Case 5: Multiple objects are selected + long tap on a new object

Result:

- the new object is added to the current selection;
- the group context menu appears.

Possible actions:

- `Edit common appearance`
- `Create frame around selection`
- `Align`
- `Distribute`
- `Duplicate selection`
- `Delete selection`
- `Clear selection`

### Case 6: Multiple objects are selected + long tap on an object already inside the selection

Result:

- the selection remains unchanged;
- the group context menu appears.

Possible actions:

- `Edit common appearance`
- `Create frame around selection`
- `Remove this object from selection`
- `Edit this object only`
- `Duplicate selection`
- `Delete selection`
- `Clear selection`

---

## 8. Important Group Action: Create Frame Around Selection

When multiple objects are selected, the context menu should include:

```text
Create frame around selection
```

Behavior:

- calculate the bounding box of all selected objects;
- create a new frame around that bounding box;
- add configurable padding;
- assign a default title;
- optionally assign a default frame color;
- after creation, the new frame may become selected;
- objects will belong to the frame through the normal frame/object intersection logic.

This is a very natural workflow:

```text
Add photos -> arrange them -> select them -> create frame around selection
```

This action should probably be included fairly early, because it makes frame creation feel much more practical.

---

## 9. Appearance, Masking, and Overlays

We need to separate three related ideas:

1. Appearance
2. Mask / masking
3. Overlay

### Appearance

Appearance is the general visual style of an object.

It may include:

- border;
- border radius;
- shadow;
- opacity;
- background color;
- frame color;
- blend mode, later;
- visual preset/style.

### Mask / masking

A mask controls **which part of the object is visible**.

Examples:

- crop;
- rounded rectangle clipping;
- circle or oval mask;
- custom shape mask;
- clipping inside a frame;
- hiding part of an image.

Masking answers the question:

> Which part of the object should be shown, and which part should be hidden?

### Overlay

An overlay is **something placed visually on top of an object**.

Examples:

- film dust and scratches;
- paper texture;
- watercolor wash;
- vignette;
- color tint;
- decorative frame;
- semi-transparent pattern;
- visual aging effect.

Overlay answers the question:

> What should be visually added on top of this object?

---

## 10. Context Menu Actions for Visual Editing

For one or more selected objects, context menus should support visual editing actions.

Possible actions:

- `Edit appearance`
- `Edit border`
- `Edit shadow`
- `Edit mask`
- `Add / edit overlay`
- `Remove overlay`
- `Reset appearance`

For multi-selection, these actions should apply to all selected compatible objects.

Examples:

- add the same border to several photos;
- apply the same overlay to several media objects;
- set the same corner radius;
- apply the same shadow;
- reset appearance for a group.

The appearance editor should therefore support:

- single-object editing;
- multi-object editing;
- mixed selection with partial/indeterminate values.

---

## 11. Suggested Near-Term Direction

### Post-MVP / future architecture

Keep presentation profiles as a future feature:

- album-level supported profiles;
- default profile;
- profile-specific frame visibility;
- profile-specific camera crop/transform;
- later: logical frames and presentation shots.

Do not implement full profile editing in MVP.

### Near-term UI work

Prioritize restructuring the editing UI:

- tablet: panel-based editor;
- phone: contextual popup actions + floating buttons;
- preserve current long-tap selection logic;
- show context menu after selection is resolved;
- make the context menu depend on selection type;
- add `Create frame around selection`;
- separate appearance, mask, and overlay editing.

---

## 12. Key Product Decision

The product should not try to dynamically reflow spatial canvas content for every device during MVP.

Instead:

```text
MVP:
Frame = spatial scene on the infinite canvas.
Navigation = fit frame into current viewport.

Future:
Presentation profiles define how the same album/frame is viewed on different devices.
```

This keeps MVP manageable while leaving a clean path toward phone/tablet-specific presentation behavior later.
