# Project Memory & Decisions Log

This file replaces the raw audio transcripts. It records future ideas and unresolved architectural questions.

## 📌 Planned Features (Product Backlog)

* **Animated Transitions:** Transitions between frames (cameras) are not instant, but use linear/bezier camera interpolation.

* **Smart Tags:** Ability to tag objects (people, places). Clicking a tag = teleporting to the corresponding frame.

* **Layers:** Ability to globally hide/show groups of objects (in addition to frames).

* **Audio/Live Photos:** Support for audio notes and moving photos (Harry Potter style).

* **Crop:** Masking media through internal editing of the Bounding Box.

## 🛠 Open Technical Questions (Tech Debt / Research)

1. **Unit System:** The canvas should use abstract `Units` instead of pixels. A reliable formula is needed for translating `Units -> DP`, taking into account the current Zoom (Scale) and the specific device's screen density.

2. **Dynamic Containment:** When changing a frame's `BoundingBox`, intersections must be recalculated on a background thread (`Dispatchers.Default`) to avoid freezing the Main Thread, and `containsNodeIds` references must be updated.

3. **Persistence:** The project is saved locally as SQLite + JSON. For the future: consider CRDT (Conflict-free Replicated Data Type) or Protobuf for real-time cloud collaboration, instead of rewriting the entire JSON file.

4. **Media Validation:** When opening a project, the app needs to iterate through the `media_library` and check the availability of the `sourceUri`. If the file is missing, substitute it with a Placeholder.
