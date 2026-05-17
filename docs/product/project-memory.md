# Project Memory & Decisions Log

This file records unresolved architectural questions. Planned features live in [future-ideas.md](future-ideas.md).

## 🛠 Open Technical Questions (Tech Debt / Research)

1. **Unit System:** The canvas should use abstract `Units` instead of pixels. A reliable formula is needed for translating `Units -> DP`, taking into account the current Zoom (Scale) and the specific device's screen density.

2. **Frame Membership:** Membership is computed from geometry + per-frame `overrides` ([architecture/frame-membership.md](../architecture/frame-membership.md)). Geometry recompute runs on `Dispatchers.Default` to avoid freezing the Main Thread.

3. **Persistence:** The project is saved locally as SQLite + JSON. For the future: consider CRDT (Conflict-free Replicated Data Type) or Protobuf for real-time cloud collaboration, instead of rewriting the entire JSON file.

4. **Media Validation:** When opening a project, the app needs to iterate through the `media_library` and check the availability of the `sourceUri`. If the file is missing, substitute it with a Placeholder.
