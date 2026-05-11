# Future Ideas (Post-MVP)

> Related: [PRD § 10](PRD.md#10-future-scope-post-mvp) | [Vision](vision.md) | [Architecture](../architecture/overview.md) | [TODO](../todo.md)

## Navigation & Transitions
- **Cinematic transition editor** — editable camera paths between frames with duration, easing presets, waypoints, and Bezier curves. See [architecture concept](../architecture/future-features/transition-editor.md).
- **Smart tags** — tag objects with people/places/topics; clicking a tag teleports to the corresponding frame
- **Semantic jumps** — navigate across the album via tag-based graph, not just spatial proximity
- **Timeline mode** — temporal dimension for chronological navigation

## Canvas & Editing
- **Layers (full)** — type layers + user-defined layers ship as MVP-adjacent work (see [todo § 13](../todo.md#13-layers-visibility-groups)); post-MVP extensions: multi-membership, per-layer locking, per-layer effects
- **Present mode** — read-only viewing for shared/published albums (see [todo § 12.6](../todo.md#126-future))
- **Crop / masking** — mask media through internal bounding box editing
- **Text / sticker enhancements** — richer text editing, sticker library
- **Animated photos / Live Photos** — Live Photo, animated GIF, or "moving photo" media (`MediaType.ANIMATED_PHOTO`), inspired by Harry Potter-style moving portraits and newspapers.

## Media
- **Audio notes** — voice memos attached to canvas locations

## Intelligence
- **AI-assisted organization** — auto-suggest groupings, tag recognition, layout hints
- **AI Diary → album generation pipeline** — extract events and story units from diary entries, generate frames and widgets automatically. See [wizards.md](wizards.md#ai-diary--zoomalboom-generation-pipeline).

## Widget System

Canvas-native smart objects that live inside the album world rather than in external dashboard panels. See [widgets.md](../architecture/widgets.md) for the full spec.

Potential widgets:
- **Core navigation:** Map, Calendar, Timeline, Tag Cloud, Highlights / Media Gallery, Frame Navigator, Portal
- **People / relationships:** People, Family Tree
- **Travel:** Route, Trip Calendar, Places List, Travel Highlights
- **Family / child / school:** Growth Timeline, Milestones, Classmates, Drawings Gallery, Achievements, School Years
- **Cookbook:** Recipe Index, Recipe Card, Ingredients, Seasons, Family Table, Cook Mode / Timer
- **AI Diary:** Period Summary, Memory Resurfacing, Needs Review, Statistics
- **Educational / project:** Concept Map, Checklist, Before/After, Asset Strip

Widgets support data binding, clickable internal elements, navigation targets, LOD rendering, and wizard-generated layouts. See also [wizard system](wizards.md) for how wizards use widgets to populate generated album structures.

## Wizard System

Album creation wizards generate structured spatial layouts from media, diary entries, tags, dates, and places. See [wizards.md](wizards.md) for the full spec.

Wizard types planned:
- Travel wizard (route map + trip calendar + nested city/day frames)
- Cookbook wizard (recipe index + nested recipe frames)
- Child / School album wizard (growth timeline + milestones + yearly frames)
- Family / Genealogy wizard (family tree + people + nested person/branch frames)
- AI Diary wizard (pipeline from diary entries → event candidates → album structure)

## Import / Export / Publishing

ZoomAlboom should eventually support exporting albums into portable and shareable formats.

### Export targets

- **Interactive HTML package** — self-contained folder or ZIP containing the scene graph, media assets, and a lightweight web viewer. Preserves zoomable navigation, frame transitions, and interactive widget behavior where possible.
- **Static website** — a published read-only version of the album that can be hosted online. Behaves like a View/Present mode experience in a browser.
- **Video walkthrough** — a rendered cinematic path through frames, useful for sharing as a normal video file. Related to the cinematic transition editor.
- **Print / PDF export** — selected frames or album regions rendered as printable pages.
- **Album archive package** — a portable `.zip` containing scene graph JSON, media assets, thumbnails, style presets, and metadata — for backup or transfer between devices.

### Import

- **Album archive import** — load a portable `.zip` package including scene graph, media, and metadata.
- **Media folder import** — batch import a folder of photos/videos and optionally run a wizard to organize them.
- **Migration from other tools** — future: import from Google Photos albums, Apple Photos albums, or other formats.

### Design questions to resolve

- Should exported HTML be fully self-contained or allowed to reference remote assets?
- How should large media files be packaged or downsampled for export?
- Which interactions are preserved in read-only export: pan/zoom, frame navigation, widget clicks, animations?
- Should export include editor metadata (guidelines, layers) or only presentation content?
- How should versioning/migrations work for imported album packages?
- Should import/export be integrated with cloud backup, or separate?

The export system is closely related to View/Present mode: exported albums should behave like read-only spatial stories, not editable projects.

## Platform & Collaboration
- **Cloud backup and sync** — persist albums across devices
- **Real-time collaboration** — CRDT or Protobuf-based multi-user editing
- **Sharing** — shareable read-only link to an album (ZoomAlboom-hosted or self-hosted HTML)

## Technical Evolution
- **Unit system** — abstract canvas `Units` instead of raw pixels; `Units -> DP` formula accounting for zoom and screen density
- **Spatial index** — grid or R-tree to replace brute-force viewport culling at >2k nodes
- **Progressive image loading** — downsampling at low zoom, high-res on zoom-in
- **Canvas engine extraction** — extract canvas/interaction/rendering as a reusable `:canvas-engine` module shared with AI Diary (see [todo § 10](../todo.md#10-canvas-engine-extraction-canvas-engine-module))
