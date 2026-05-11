# Product Requirements Document (PRD)

> Related: [Vision](vision.md) | [Future Ideas](future-ideas.md) | [Wizards](wizards.md) | [Architecture](../architecture/overview.md)

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
- teachers or parents making educational albums or group/class history albums
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

### 7.6 School / Kindergarten album
The user creates a school-years or preschool album organized by class, teachers, classmates, events, and milestones.

May include: school years by age/class, classmates and teachers, performances, field trips, drawings and crafts, certificates, achievements, yearly progression, funny quotes and memories.

Useful widgets: School Years / Timeline, Classmates, Calendar, Drawings Gallery, Achievements, Highlights, People.

### 7.7 Family / Genealogy album
The user creates a family history album organized around people, branches, places, old photos, documents, and stories.

May include: family tree, individual person pages, family branches and generations, ancestral homes and places, historical documents and old photographs, family stories.

Useful widgets: Family Tree, People, Place Map, Timeline.

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

**MVP:**
- Image — raster photo/image
- Video — video clip
- Audio — audio clip
- Text — inline text block
- Sticker — static sticker/illustration
- VectorShape — SVG or vector primitive

**Post-MVP:**
- AnimatedPhoto — Live Photo / animated GIF style media, inspired by "moving photos" in Harry Potter newspapers and portraits.

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

### 8.7 Non-destructive media appearance

Every media object on the canvas can carry an **appearance recipe** that controls how it is visually rendered without modifying the original source file.

```
source media asset  +  appearance recipe  =  rendered media object on canvas
```

The same source photo can therefore appear multiple times in the same album with entirely different visual styles — a normal photo here, a sepia archive print there, a Polaroid frame somewhere else.

The appearance recipe covers:
- **crop** — fit, fill, manual pan/zoom inside the bounding box, with focal point
- **color adjustments** — brightness, contrast, saturation, temperature, exposure, etc.
- **raster overlays** — textures, dust, scratches, light leaks, vignette, decorative elements applied as semi-transparent PNG/WebP layers with blend modes
- **frame overlays** — decorative photo frames rendered as stretch or nine-slice (corners not distorted)
- **border, shadow, corner radius, opacity**
- **caption** styling

Appearance is stored as data on the canvas node — not baked into the source file. Users can copy/paste appearance between objects, save it as a named style preset, and apply it in bulk.

When satisfied with a visual result, users can **save a rendered derivative** — a new image asset generated by flattening the source and all its appearance layers. The derivative becomes a reusable media asset in the library; the original remains untouched.

### 8.6 Visual atmosphere
The canvas background sets the emotional tone of the entire album or of individual frames.  
A dark, textured background can feel intimate and archival. A bright linen background can feel warm and handcrafted. A grid or cork board can feel like a project space.

Backgrounds operate at two levels:
- **Album background** — covers the entire infinite canvas. Can be a solid color, image, or tiling texture. Can be fixed to the screen (static behind everything, not affected by panning or zooming) or anchored to the world (moves and scales with the canvas, creating a sense of infinite surface).
- **Frame background** — individual frames can have their own color fill, making each section feel like a distinct page, board, or chapter within the album.

Background is a style property of the album or frame, not a canvas object — it cannot be selected, moved, or layered like media items.

### 8.8 Widget system

Widgets are **canvas-native smart objects** — first-class canvas nodes that live inside the spatial story alongside photos, text, and frames. See [widgets.md](../architecture/widgets.md) for the full technical spec.

> Widgets are not separate dashboard panels outside the album. They are part of the album world.

A widget has a position, size, rotation, and z-index like any other canvas object. It can be moved, resized, placed inside frames, and styled. But unlike a static photo, a widget has **behavior and data binding**: it displays structured data drawn from the album (places, dates, tags, people, frames) and its elements can be clicked to navigate to other parts of the album.

**Examples:**
- A **map widget** shows places visited and clicking a marker zooms to the relevant frame.
- A **calendar widget** shows album dates and clicking a day navigates to that day's content.
- A **tag cloud** shows important tags; clicking jumps to a filtered view or frame.
- A **family tree** shows people and their relationships; clicking a person opens their section.
- A **frame navigator** is a canvas-native table of contents for the album's frame hierarchy.
- A **portal** is any clickable object that acts as a navigation link to a frame, node, or external URL.

**Widget modes:**
- In **Edit mode**: widget can be selected, moved, resized, configured.
- In **View/Present mode**: widget elements are clickable for navigation; editing handles are hidden.

**LOD:** widgets support the same visibility policy as other nodes — Hidden → Stub → Preview → Full — so large canvases with many widgets remain performant.

**Persistence:** widgets are saved as `CanvasNode.Widget` entries in the scene graph JSON, not as IDE/workspace state. Their data bindings reference album content (tags, frames, dates, places) by id or query.

**Widget categories:** Core navigation (Map, Calendar, Timeline, Tag Cloud, Highlights, Frame Navigator, Portal), AI Diary integration (Period Summary, Memory Resurfacing, Needs Review), Travel (Route, Trip Calendar, Cities List), Family/Child (People, Family Tree, Milestones, Growth Timeline), Cookbook (Recipe Index, Recipe Card, Ingredients, Seasons), Educational/Project (Concept Map, Checklist, Before/After).

---

## 9. MVP Scope

> **Core MVP** proves the spatial editor loop: canvas + frames + photos + basic editing + save/reopen. **MVP-adjacent** features ship right after the first usable vertical slice but do not block it. **Post-MVP** features are planned but must not be attempted before Core MVP is stable.

### 9.1 Core MVP

#### Album
- create, open, and delete an album
- display album on an infinite or effectively unbounded canvas
- support panning, zooming, and rotation
- save and reopen album locally
- persist canvas structure, frames, and item placement
- persist references to media files

#### Frames
- create frames and place them on canvas
- move and resize frames
- visually distinguish frames from general content
- navigate / focus into a frame (animated camera interpolation)
- frame list accessible from menu or swipe gesture

#### Media
- add photo, video, text, and sticker items
- move / scale / rotate items
- delete and duplicate items
- basic text node creation and inline editing
- display media within the canvas

#### Editing
- single-node and multi-node selection
- basic undo / redo
- contextual action bar (delete, duplicate, edit)

#### Workspace
- FAB [+] → Add content bottom sheet
- canvas-first chrome: minimal default UI with TopBar and FAB only

### 9.2 MVP-adjacent (soon after Core MVP)

These features are important but do not block the first working vertical slice:

- multi-photo import and auto grid placement
- Edit / View mode split
- basic media validation and missing-media placeholder
- basic non-destructive appearance fields: opacity, crop mode (fit / fill / manual), corner radius, border, shadow
- copy / paste appearance between media objects
- save appearance as a named style preset
- solid and texture/image album background
- frame background fill color and opacity
- basic snapping and guidelines
- group align / distribute
- basic widget infrastructure: `Portal` and `FrameNavigator`
- basic media library (copied local assets, referenced by id)

### 9.3 Post-MVP

These features are part of the product vision but must not be treated as MVP requirements:

- raster overlays with blend modes (texture, dust, light leak, vignette, decoration)
- decorative frame overlays (stretch or nine-slice rendering)
- parametric color adjustments (brightness, contrast, saturation, temperature, etc.)
- save rendered derivative as a new media asset
- full non-destructive appearance editor UI
- full widget system (Map, Calendar, Tag Cloud, Family Tree, etc.)
- full wizard system
- interactive HTML export / static website / video walkthrough / print export
- cloud sync and real-time collaboration
- animated overlays and AnimatedPhoto / Live Photo support
- AI auto-layout and diary generation pipeline
- cinematic transition editor

See [§10](#10-future-scope-post-mvp) and [future-ideas.md](future-ideas.md) for the detailed list.

---

## 9b. MVP Vertical Slice

The first successful vertical slice should allow a user to:

1. create an album;
2. add several photos;
3. create several frames;
4. arrange photos spatially inside/around frames;
5. pan, zoom, rotate, and focus frames;
6. save and reopen the album without losing layout;
7. undo/redo basic editing operations;
8. switch to a simple View mode where accidental editing is disabled.

This vertical slice proves the core product: a spatial multimedia album, not just a gallery.

---

## 10. Future Scope (Post-MVP)

These are likely future directions, but not required for initial release. See [future-ideas.md](future-ideas.md) for the full categorized list.

- cinematic transition editor — editable camera paths between frames with presets, waypoints, and curves. See [future-ideas](future-ideas.md#navigation--transitions).
- smart tags for people / places / topics
- semantic jumps across the album
- layers and visibility control
- crop / masking
- live-photo-like media (`MediaType.ANIMATED_PHOTO`)
- timeline mode / temporal dimension
- AI-assisted organization and tagging
- cloud backup and sync
- real-time collaboration (CRDT or Protobuf)

### Import / Export / Publishing

- **Interactive HTML package** — self-contained folder or ZIP containing scene graph, media assets, and a lightweight web viewer. Preserves zoomable navigation and frame transitions.
- **Static website** — published read-only album hosted online. Behaves like View/Present mode in a browser.
- **Video walkthrough** — rendered cinematic path through frames as a shareable video file.
- **Print / PDF export** — selected frames or album regions as printable pages.
- **Album archive package** — portable `.zip` for backup or device-to-device transfer (scene graph JSON + media + thumbnails + style presets + metadata).
- **Album archive import** — load a portable archive on a new device.
- **Media folder import** — batch import a photo/video folder, optionally with wizard organization.

See [future-ideas.md](future-ideas.md#import--export--publishing) for open design questions.

### Wizard System

Album creation wizards generate structured spatial layouts from media, diary entries, tags, dates, and places. See [wizards.md](wizards.md) for the full spec. Wizard types: Travel, Cookbook, Child/School, Family/Genealogy, AI Diary pipeline.

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

When AI Diary data is available, ZoomAlboom can generate album structures automatically from diary entries, media clusters, and extracted events. See [wizards.md](wizards.md#ai-diary--zoomalboom-generation-pipeline) for the generation pipeline.

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

### 11.8 Media appearance (non-destructive editing)

> **Scope note:** Basic fields (opacity, crop, corner radius, border, shadow, copy/paste appearance, named presets) are MVP-adjacent. Raster overlays, parametric color adjustments, decorative frame overlays, and rendered derivatives are post-MVP.

- Original source media files are never modified by appearance editing
- User can set crop mode per media object: fit (whole image visible), fill (fills bounds, may crop), manual (pan+zoom inside bounds), stretch
- User can set a focal point to preserve important areas (faces) during auto-crop
- User can apply opacity, corner radius, border color/width, drop shadow
- User can add raster overlay layers (PNG/WebP with alpha): texture, dust, scratches, light leaks, vignette, decorative elements
- Each overlay has independent opacity and blend mode (Normal, Multiply, Screen, Overlay, Soft Light, Darken, Lighten)
- User can apply a decorative frame overlay in stretch or nine-slice mode (corners are not distorted in nine-slice)
- Frame overlay defines content insets (e.g. Polaroid's caption area below the photo)
- User can apply parametric color adjustments: brightness, contrast, saturation, temperature, tint, exposure, highlights, shadows, blur, sharpen, vignette
- User can copy appearance from one media object and paste it onto another
- User can save a complete appearance recipe as a named style preset and apply it to other objects
- User can reset appearance to default (no effects)
- User can save the rendered result as a new media asset (source + all appearance layers flattened into a new PNG/JPEG/WebP)
- Rendered derivative is stored in the album's project folder and appears in the media library
- Rendered derivative tracks its source asset id and the appearance recipe used to produce it
- User can create a rendered copy on canvas (new node alongside original) or replace the original node with the rendered result (with undo)
- Post-MVP: batch apply preset, AI auto-enhance, background removal, animated overlays

### 11.7 Backgrounds
- User can set an album-level background: none, solid color, or texture/image
- User can choose whether the album background is screen-fixed or world-anchored (moves with canvas pan/zoom)
- User can set the tile mode for a textured background: none, stretch, cover, contain, repeat, repeatX, repeatY
- User can set the position and size of the background tile grid (anchor point and tile dimensions in canvas space)
- User can set background opacity
- User can set a fill color and opacity on individual frames
- Frame fill is clipped to frame bounds
- Background settings are persisted with the album
- Post-MVP: frame texture backgrounds, per-layer backgrounds

### 11.9 Widget system

See [widgets.md](../architecture/widgets.md) for domain types and the full spec. See [open-questions.md §9](../architecture/open-questions.md#9-portal--widget-target-movement-semantics) for unresolved portal / widget target movement semantics.

- User can place widget objects on the canvas; widgets have position, size, rotation, z-index like any canvas node
- User can move, resize, and place widgets inside frames
- User can configure a widget's data source (album tags, dates, places, frames, diary entries)
- User can configure navigation links from widget elements to frames, nodes, albums, or external URLs
- Widget elements are clickable in View/Present mode and trigger camera navigation
- Widgets support the same LOD visibility policy as other nodes (Hidden / Stub / Preview / Full)
- Widgets are saved in the scene graph JSON as part of the album composition
- MVP-adjacent widget set: Portal, Frame Navigator
- Extended widget set (post-MVP): Calendar, Map, Tag Cloud, Highlights/Media Gallery, People, Timeline, Family Tree, Route, Recipe Index/Card, Milestones, Growth Timeline, Period Summary, Memory Resurfacing, and domain-specific widgets for travel / family / cookbook / educational albums
- Wizard integration (post-MVP): wizards auto-generate overview frames populated with widgets linked to nested frames

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

> users can build and navigate emotionally meaningful multimedia spaces, not just store files in a gallery.
