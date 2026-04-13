# Product Requirements Document (PRD)

## 1. Product Summary

**ZoomAlboom** is an Android-first interactive multimedia album built around an infinite zoomable canvas.

Instead of browsing content page by page, users move through a spatial world made of photos, videos, text, stickers, and frames. The album behaves more like a visual map, memory space, or story landscape than a traditional photo gallery.

The product is intended to support rich personal and creative storytelling: family albums, travel diaries, educational albums, recipe albums, and project journals.

---

## 2. Product Vision

ZoomAlboom turns an album into a navigable space.

A user does not simply open “the next page.”  
They pan, zoom, rotate, and move between meaningful areas of the album. Frames help structure this space and create visual and semantic anchors. A family story, a trip, a collection of recipes, or a project timeline can all exist inside one spatial canvas.

The experience should feel:
- visual
- intuitive
- exploratory
- emotionally rich
- spatial rather than linear

---

## 3. Problem Statement

Traditional albums and galleries are good at storing media, but weak at expressing relationships between pieces of media.

Users often want to show:
- how people are connected
- how places relate to each other
- how events evolve over time
- clusters of memories, topics, or stories
- both overview and detail in one space

Existing tools usually force one of these models:
- a flat gallery
- a slideshow
- a folder tree
- a timeline
- a presentation deck

These models are useful, but they do not fully support exploratory, nested, spatial storytelling.

ZoomAlboom addresses this by giving users a canvas where media can be arranged meaningfully and navigated visually.

---

## 4. Goals

### 4.1 Primary goals
- Let users create a multimedia album on an infinite canvas
- Support intuitive spatial navigation: pan, zoom, rotation
- Let users organize media into meaningful frames and zones
- Support both overview and deep detail in the same album
- Make the album suitable for emotional, narrative, and creative use cases

### 4.2 Product goals
- Build a strong MVP for Android
- Establish a clean architecture for future growth
- Make core interactions performant on large canvases
- Support extensibility for future content types and navigation models

---

## 5. Non-Goals for MVP

The following are explicitly **out of scope for MVP**:

- real-time multi-user collaboration
- cloud sync across devices
- web or desktop editor parity
- AI tagging and semantic search
- advanced animations and motion design system
- rich media editing inside the app
- full version history
- publishing/sharing platform
- CRDT-based collaborative document model
- highly polished export to video/print/web

These may be considered after core canvas and album interactions are stable.

---

## 6. Target Users

### 6.1 Primary user segments

#### A. Family storytellers
People who want to create rich family albums with:
- relatives
- children
- pets
- homes
- travel
- milestones
- generational structure

#### B. Creative personal archivists
Users who enjoy arranging memories, notes, visuals, and stories in a meaningful visual space.

#### C. Hobby/project documenters
People who want to document:
- a creative project
- learning materials
- a long-term hobby
- a trip
- a process or archive

### 6.2 Secondary user segments
- teachers or parents making educational albums
- home cooks building visual recipe collections
- travelers creating route-based memory maps
- users who think visually and dislike rigid linear album structures

---

## 7. Core Use Cases

### 7.1 Family album
The user creates a family canvas with:
- central family overview
- individual areas for each family member
- pets
- homes and places
- relatives and branches
- trips and life chapters

A user can start from a shared family overview and zoom into a person, a place, a branch of relatives, or a specific story.

### 7.2 Travel diary
The user creates a map-like or narrative travel album with:
- trips
- cities
- routes
- notes
- media clusters by location or day

### 7.3 Cookbook
The user creates a visual recipe book where each dish or recipe cluster contains:
- ingredient notes
- process photos
- short videos
- tips and variations

### 7.4 Educational album
The user arranges explanations, illustrations, diagrams, and examples spatially.

### 7.5 Project album
The user documents a process, concept, or long-term project with:
- reference material
- progress snapshots
- notes
- milestones
- outcomes

---

## 8. Core Product Concepts

### 8.1 Infinite canvas
The album exists on an effectively unbounded 2D space.

### 8.2 Frames
Frames are structured areas of the album.  
They are not just decorative boundaries: they help users organize content and navigate between meaningful regions.

Frames may represent:
- a person
- a family branch
- a trip
- a recipe
- a chapter
- a place
- a topic

### 8.3 Media items

Media types are defined by the `MediaType` enum:

**Current:**
- Image
- Video
- Text

**Future:**
- Audio
- Sticker
- AnimatedPhoto
- VectorShape

Each media item can be placed spatially on the canvas.

### 8.4 Camera-based navigation
The user navigates the album with camera-like movement:
- pan
- zoom
- rotate
- focus on a frame

### 8.5 Spatial storytelling
The meaning of the album comes not only from the media itself, but also from:
- spatial grouping
- proximity
- nesting
- scale
- transitions between areas

---

## 9. MVP Scope

### 9.1 In scope for MVP

#### Album canvas
- create and open an album
- display album on an infinite or effectively unbounded canvas
- support panning
- support zooming
- support rotation

#### Frames
- create frames
- place frames on canvas
- move/resize frames
- visually distinguish frames from general content
- navigate/focus into frames
- animated frame transitions — smooth camera interpolation (linear/bezier) between frames

#### Media items
- add photo item
- add video item
- add text item
- add sticker item
- move / scale / rotate media items
- display media within the canvas

#### Editing
- basic selection
- transform selected object
- edit basic text content
- delete object
- duplicate object

#### Structure / panels / workspace
- media library as a panel OR bottom sheet (accessible via FAB → Add content flow)
- frame list / structure panel (accessible via menu or swipe gesture, not permanently visible)
- contextual action bar for selected canvas nodes
- IDE-like overlay panels available as opt-in power-user configuration
- Panel Configuration UI: allows users to toggle panels on/off, choose position (docked slot or floating), and reset to defaults 

#### Persistence
- save and reopen albums locally
- persist canvas structure, frames, and item placement
- persist references to media files

---

## 10. Future Scope (Post-MVP)

These are likely future directions, but not required for initial release. See [future-ideas.md](future-ideas.md) for the full categorized list.

- cinematic transition editor — editable camera paths between frames with presets, waypoints, and curves. See [future-ideas](future-ideas.md#navigation--transitions).
- smart tags for people / places / topics
- semantic jumps across the album
- layers and visibility control
- crop / masking
- audio notes
- live-photo-like media
- timeline mode / temporal dimension
- AI-assisted organization
- cloud backup and sync
- export to video / print / web page

---

## 10b. AI Diary Integration — Visualization Modes

ZoomAlboom serves as the **visualization and exploration layer** for [AI Diary](../product/vision.md), which handles capture and structuring of personal entries. AI Diary has three verticals — **Life Diary**, **Baby Diary**, and **Travel Diary** — each producing structured entries with different metadata (topics, emotions, people, milestones, locations, etc.). ZoomAlboom provides default canvas layouts optimized for each vertical while preserving full creative freedom.

### Visualization Modes

#### 1. Timeline Mode (default for Life Diary)

Chronological horizontal or vertical layout. Entries are placed along a time axis.

- **AI-generated clusters** group related entries (e.g. "your photography phase", "that stressful month at work").
- **Zoom out** → months and years as overview.
- **Zoom in** → individual entries with full detail.
- **Frames auto-generated** for time periods (week, month, year).

#### 2. Milestone Mode (default for Baby Diary)

Visual milestone map — key developmental moments as prominent nodes on the canvas.

- A timeline runs underneath, but **milestones** (first smile, first word, first steps, first day at school) are elevated as large visual anchors with photos.
- Periods between milestones show daily observations at lower zoom levels.
- Supports a **"growth journey" narrative** — zooming out shows the whole arc from birth to current age.

#### 3. Map Mode (default for Travel Diary)

Geographic layout — entries positioned on a spatial canvas that mirrors real-world geography.

- Each **trip is a frame** containing its locations.
- Photos, notes, and highlights clustered around place markers.
- **Zoom out** → all trips as dots on an abstract world view.
- **Zoom in** → single trip with daily route and stops.
- Multiple trips to the same region **layer on top of each other**.

### Architectural Principles

- **Starting layouts, not rigid templates.** Users can always rearrange, mix, and customize.
- **Cross-mode entries.** The same entry can appear in different modes (a travel entry during baby's first trip appears in both Map and Milestone modes).
- **Seamless mode switching.** Same data, different spatial arrangement — switching modes re-layouts the canvas without losing content.
- **AI mode suggestions.** AI can suggest which mode works best based on entry content and metadata.
- **Future: AI auto-layout.** When entries arrive from AI Diary, ZoomAlboom places them on canvas automatically based on the selected mode.

### Sharing

ZoomAlboom canvases should be exportable as **shareable visual stories** — a read-only zoomable view that can be shared via link with family, friends, or publicly.

This is the "LiveJournal post" equivalent: the artifact you create from your memories and share with others. The diary captures; the canvas arranges; the shared view lets others explore.

---

## 11. Functional Requirements

### 11.1 Album lifecycle
- User can create a new album
- User can open an existing album
- User can save album state locally
- User can continue editing after reopening

### 11.2 Canvas interaction
- User can pan across album space
- User can zoom in and out smoothly
- User can rotate the view
- Interactions should remain responsive with non-trivial amounts of content

### 11.3 Frame management
- User can create a frame
- User can rename a frame
- User can move and resize a frame
- User can focus/navigate to a frame
- Frame list should help navigate project structure

### 11.4 Media placement
- User can import or attach media items
- User can place media onto canvas
- User can move, scale, and rotate items
- User can edit text items
- User can remove items
- User can duplicate items

### 11.5 Panels / editor shell
- By default, the canvas uses minimal chrome (TopBar, FAB, contextual action bar) — see [§12.6](#126-canvas-first-chrome)
- User can access supporting panels without losing the canvas mental model
- Panels are hidden by default and available as an opt-in power-user configuration via the Panel Configuration UI
- When enabled, panels coexist with the canvas and feel like tools around it, not a separate mode replacing it

### 11.6 Persistence
- Album state should survive app restart
- Missing media references should be handled gracefully
- Data model should support future extension

---

## 12. UX Principles

### 12.1 Spatial first
The app should feel like navigating a visual space, not filling a form.

### 12.2 Overview + detail
Users should be able to:
- see the big picture
- zoom into small stories
- move naturally between levels

### 12.3 Direct manipulation
Objects should feel directly movable and transformable.

### 12.4 Non-destructive mental model
The user should feel that content lives in a stable world and is being explored, not constantly restructured by hidden system logic.

### 12.5 Lightweight editing shell
Editor tools should support the canvas rather than dominate it.

### 12.6 Canvas-first chrome
The default UI should maximize canvas visibility. On a phone screen, persistent panels consume 20–30% of canvas width and conflict with spatial immersion. The default experience therefore uses minimal chrome:

- **Navigate mode** (default): canvas takes ~100% of screen. Only a thin TopBar (album name, undo/redo, menu icon) and a single FAB [+] for adding content.
- **Add content mode**: a bottom sheet slides up with content type picker and media library. The canvas remains visible behind the sheet.
- **Object selected mode**: a contextual action bar appears at the bottom (move/scale/delete/duplicate/edit) and disappears when selection is cleared.

The full IDE-style panel system (docked + floating panels) is preserved but hidden by default. Users can opt in to panels via a Panel Configuration UI accessible from the menu.

---

## 13. UX Risks

- Too much UI chrome can destroy the feeling of free navigation
- Too many overlapping gestures can make interaction confusing
- Large canvases can become disorienting without structure
- Panels can compete with the core canvas metaphor
- Performance drops during gestures will immediately damage perceived quality
- **Panel discoverability:** panels are hidden by default in the canvas-first model. Power users who would benefit from docked panels (media library, frame list) may not discover the Panel Configuration UI without guidance. Mitigations: onboarding hint, menu labeling, and first-run tooltip.

---

## 14. Technical Constraints

See [architecture overview](../architecture/overview.md) for implementation details.

### 14.1 Platform
- Android-first
- Kotlin
- Jetpack Compose

### 14.2 Rendering model
- Canvas-heavy UI
- Shared transform strategy for pan/zoom/rotation
- Performance-sensitive rendering path

### 14.3 Persistence model
Likely split between:
- structured local metadata storage
- serialized scene/canvas content
- media URI/file references

### 14.4 Architecture requirements
- clean separation of domain, data, and UI concerns
- canvas state separate from overlay/panel state
- future extensibility for more media types and navigation modes

---

## 15. Success Criteria for MVP

The MVP is successful if a user can:

1. create an album
2. place several frames
3. add photos, videos, text, and stickers
4. navigate the canvas smoothly with pan/zoom/rotation
5. use frames as meaningful anchors
6. save the album and reopen it without losing structure
7. feel that the album is spatial and expressive rather than just a gallery

---

## 16. Key Product Risks

### 16.1 Interaction complexity
Users may struggle if navigation and editing modes are unclear.

### 16.2 Performance risk
A spatial media canvas can become expensive to render and update.

### 16.3 Scope creep
There are many tempting future features; MVP must stay tight.

### 16.4 Discoverability
Users may need gentle structure to understand how to build meaningful albums.

---

## 17. Open Product Questions

- How opinionated should frame behavior be in MVP?
- Should frame containment be explicit, inferred, or hybrid?
- How much rotation should be exposed in everyday UX?
- Should navigation favor free exploration or structured jumping?
- How much editing power belongs in MVP versus post-MVP?
- Should the first-run experience include templates?

---

## 18. Inspirations

The product is inspired by tools and ideas in the space of:
- zoomable interfaces
- spatial note-taking
- visual knowledge spaces
- multimedia scrapbooks
- interactive albums
- Prezi-like navigation
- ZoomNotes-like freeform composition

ZoomAlboom is not intended to be just a presentation tool or note app.  
Its main focus is **multimedia storytelling in a navigable space**.

---

## 19. Summary

ZoomAlboom is a spatial multimedia album for Android.

Its MVP should prove one core thing:

> users can build and navigate emotionally meaningful multimedia spaces, not just store files in a gallery. toma
