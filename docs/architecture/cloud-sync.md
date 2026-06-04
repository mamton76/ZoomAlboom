# Cloud Sync

> Related: [data-model](data-model.md) | [decisions](decisions.md) | [undo-redo](undo-redo.md) | [overview](overview.md) | [TODO § 26](../todo.md#26-cloud-sync-deferred)
>
> Status: **decided 2026-06-03**, implementation deferred. No Google Drive code is part of the current `EditorState` / `ActiveTool` / `Eraser` work.

The app is **local-first**. Cloud storage is an opt-in remote binding on top of an always-local album. The first cloud feature is **not** manual-only backup — it is automatic snapshot sync with conflict-safe editing.

---

## 1. Core Decision

> **Local-first automatic snapshot sync with conflict-safe editing.**

The application always works with a **local working copy**. For an album connected to a remote (initially: Google Drive):

- Every stable local save is stored **locally first**.
- After each stable local commit, the app attempts to sync a new snapshot/revision to the remote.
- When the album is opened, the app checks the **remote head revision** before enabling Edit mode.
- A manual `Sync now` action remains available.
- Conflicts **never** trigger automatic overwrite or automatic merge.

A "stable local commit" is the same boundary as the undo/redo `FinishInteraction` snapshot (see [undo-redo.md](undo-redo.md)) — one finalized user-visible operation, not every keystroke or gesture frame.

---

## 2. Open Flow

For a cloud-connected album:

1. Open the local copy **immediately in View mode**.
2. Kick off a remote revision check in the background.
3. **Keep Edit mode disabled** until the remote revision check has completed safely.

Outcomes of the revision check gate:

| Remote state vs. local | Edit mode | UX |
|------------------------|-----------|----|
| Remote head = local head (in sync) | enabled | normal editing |
| Remote head is ahead, no local pending edits | enabled after silent fast-forward of the local copy | informational toast |
| Remote head is ahead, local has pending edits | **disabled until conflict policy resolves** (see § 3) | banner + choice |
| Network failure / offline | disabled by default | `Edit offline anyway` requires explicit user choice + warning |

`Edit offline anyway` is a deliberate, explicit user action. The warning states clearly: *editing offline may later produce a conflict copy*.

View mode is never gated on network — viewing the local copy is always available.

---

## 3. Conflict Policy

If local pending edits exist **and** the remote version has advanced independently:

- **Do not overwrite.**
- **Do not auto-merge.**
- **Preserve the local branch as a separate conflict-copy album.**
- **Restore/update the primary connected album from the remote head revision.**

Example surfacing in the home screen:

```text
Italy Trip                       ← connected; now at remote head
Italy Trip — local conflict copy ← local-only; preserves the user's pending edits
```

The conflict copy is a normal local album: editable, exportable, and can itself be cloud-connected later if the user wants. There is no implicit re-merge into the primary album — any reconciliation is a user action (manual copy/paste of nodes, etc.), deferred indefinitely.

Why this policy:

- Eliminates silent data loss as a failure mode.
- Makes "what happened" inspectable (two albums side by side) instead of hidden in three-way merge logs.
- Defers automatic merge (which would need CRDT or operation-replay over the scene graph) without blocking the rest of cloud sync.

---

## 4. Sync Triggers

| Trigger | When | Notes |
|---------|------|-------|
| Open-time revision check | On opening a synced album | Gates Edit mode (§ 2). |
| Post-commit automatic sync | After each stable local command boundary (`FinishInteraction`) | Debounced + coalesced (see below). |
| Manual `Sync now` | User action in album chrome | Always available; same path as automatic. |
| Retry on network return | Connectivity callback | Resumes the most recent pending sync only — older queued attempts collapse. |
| App backgrounding | OS lifecycle | **Best-effort only**, not the primary trigger. Do not architect around `onStop`. |

**Debounce / coalesce.** Rapid successive commits MUST NOT cause one upload per commit. Coalesce into a single upload representing the most recent local head once the user pauses past a short window. Tunable; not load-bearing on the model.

**Retry policy.** Failures are transient by default — the local copy is unaffected, the pending sync stays queued, and the next trigger retries. No exponential blow-up: at most one in-flight sync per album.

---

## 5. Model Direction

### 5.1 What this is *not*

Do **not** model storage as:

```kotlin
// WRONG
enum class StorageMode { Local, GoogleDrive }
data class Album(..., val storageMode: StorageMode)
```

This collapses two independent concerns (the album exists; the album is bound to a remote) into one flag, makes every album touchpoint cloud-aware, and forces a re-model the moment a second backend (Dropbox, S3, self-hosted) appears.

### 5.2 What this *is*

Two separate concepts:

- **`Album`** — always exists locally, has a stable UUID, has a versioning boundary (revision lineage).
- **`RemoteBinding`** *(future)* — an optional pairing between a local album and a remote location. Each album has zero or one binding. The binding owns: remote provider, remote location reference, last-synced revision id, pending-sync queue marker.

Sketch (not load-bearing — final field set decided when the slice ships):

```kotlin
data class Album(
    val id: AlbumId,            // stable UUID, survives rename and re-binding
    val name: String,
    val headRevisionId: RevisionId,
    val parentRevisionId: RevisionId?,
    // ... existing scene-graph + metadata fields
)

sealed interface RemoteBinding {
    val albumId: AlbumId
    val lastSyncedRevisionId: RevisionId?

    data class GoogleDrive(
        override val albumId: AlbumId,
        val folderId: String,
        val accountId: String,
        override val lastSyncedRevisionId: RevisionId?,
    ) : RemoteBinding
}
```

Two consequences:

- Existing local-only albums need no migration when cloud sync ships — they simply have no `RemoteBinding`.
- A second provider is additive: a new `RemoteBinding` variant, not a new column on `Album`.

### 5.3 Revision lineage, not timestamps

Conflict detection MUST rely on **revision lineage**, not wall-clock timestamps. Device clocks are wrong often enough to be unsafe; "last write wins by timestamp" is exactly the silent-data-loss mode § 3 exists to prevent.

Each stable local commit produces a new `RevisionId` (opaque, content-addressed or random) and records its `parentRevisionId`. Conflict detection becomes:

- Local head's parent chain contains remote head → local is ahead, push.
- Remote head's parent chain contains local head → remote is ahead, fast-forward.
- Neither contains the other → divergence → conflict policy (§ 3).

Timestamps may exist as display metadata. They are not a sync input.

### 5.4 Per-album remote granularity

Each album is the binding unit. If/when Google Drive ships, each cloud-connected album corresponds to a separate Drive folder. The folder is an implementation detail of the `GoogleDrive` binding variant, not a user-facing model field.

---

## 6. Future Encryption Constraint

The architecture must remain compatible with future end-to-end / zero-knowledge style encryption:

- Remote storage may contain **encrypted snapshots/blobs only** — Drive (or any provider) sees opaque payloads, not scene-graph JSON.
- Keys remain under user control. Key management is out of scope for the first sync slice but must not be designed out.
- Sync MUST NOT depend on the provider inspecting or merging plaintext album content. This rules out provider-side merge, provider-side thumbnail generation, and provider-driven indexing of album contents.
- Server-side operations are limited to: store blob, fetch blob, list revisions, atomic compare-and-swap on the head pointer. Anything richer would couple us to a non-encrypted future.

This is why § 3 picks **conflict-copy preservation** over auto-merge: auto-merge requires reading album content; conflict-copy preservation does not.

---

## 7. What This Does *Not* Decide

Explicitly deferred to the implementation slice (not blocking this decision):

- Authentication / OAuth flow for Google Drive.
- Concrete `RemoteBinding` field set and Room/JSON shape.
- Sync debounce window (units of seconds).
- UX placement of `Sync now`, the offline banner, and the `Edit offline anyway` confirmation.
- Conflict-copy naming scheme beyond the `" — local conflict copy"` suffix example.
- Quota / large-album behavior (chunking, partial upload, resumable uploads).
- Multi-device active-editing detection (e.g., advisory "this album is open on another device" hint).
- Encryption key management UX.
- Migration from "many local albums" to "some of them connected" — the model already supports it (no binding = local-only).

These are real questions; they are decisions for the slice that actually builds sync, not for this graduation.

---

## 8. Non-Goals

For the first cloud sync slice, explicitly out of scope:

- **Real-time collaboration.** No live presence, no live cursors, no live edits. One user, one device editing at a time. Multi-device same-user is allowed (and is the main motivation), but with revision-check gating, not concurrent editing.
- **Automatic three-way merge.** Even a "smart" merge over the scene graph is rejected for v1.
- **CRDT-backed scene graph.** Mentioned in `todo.md § 18 Future` as a separate post-MVP track; orthogonal to this decision.
- **Cross-album linking through the cloud.** Cloud sync is per-album.
- **Inspecting album content from the cloud.** Web viewer, share-via-link, server-rendered thumbnails are all incompatible with § 6 and are not on the path.

---

## 9. Relationship to Other Subsystems

- **Undo/redo** ([undo-redo.md](undo-redo.md)) — sync's "stable local commit" boundary is the existing `FinishInteraction` snapshot. Cloud sync rides on top of the same boundary; it does not introduce a second notion of "committed state."
- **Edit / View mode** ([todo.md § 12](../todo.md#12-canvas-interaction-modes-edit--view)) — the open-flow gate (§ 2) is implemented by keeping Edit mode disabled until the revision check resolves. This is additive to the existing mode split.
- **Album storage** ([data-model.md](data-model.md)) — the local working copy is unchanged. JSON scene graph + Room metadata stay authoritative locally. `RemoteBinding` is a new optional table when the slice lands.
- **Presentation profiles** ([presentation-profile.md](presentation-profile.md)) — orthogonal; presentation lives inside the scene graph and is synced as part of the album payload.
