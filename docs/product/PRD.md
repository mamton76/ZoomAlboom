# Product Requirements Document (PRD)

**Product:** Alboom (Android)

**Stage:** MVP (Core Product)

## 1. Core Concept

An Infinite Canvas for the spatial organization of media content (photos, videos, notes) with a system of navigation frames and a customizable interface (IDE-style).

### References (Inspirations):

* **Prezi:** Zoomable User Interface (ZUI) concept, non-linear presentations, and using frames as "scenes" for storytelling.

* **ZoomNotes:** Free infinite space for notes and spatial organization of thoughts.

* **Figma / Miro:** Logic of frame-containers, object selection (Bounding Box), and an advanced interface with side panels (IDE-style).

* **Obsidian / Notion:** System of smart tags and interactive hyperlinks (portals) to create a connected knowledge base.

### Use Cases:

* **Travel Diaries:** A visual map of a trip, where photos are tied to locations, and text notes and impressions are scattered around.

* **Family Archives and Family Trees:** A spatial structure of generations with the ability to "dive" into the details of each family member through a system of tag-links.

* **Moodboards and Design Projects:** (e.g., renovations): Gathering references, palettes, and before/after photos in a single infinite space divided by room-frames.

## 2. Core Functionality (Core / MVP)

* **Infinite Canvas:** A scalable workspace for freely arranging various types of media files.

* **Frame System:** A tool for visually highlighting areas on the canvas, creating logical groups, and enabling smooth navigation (transitions) between them.

* **Media Library:** Basic lists for managing uploaded content and created frames.

## 3. Advanced Interface (IDE Custom UI)

For convenient and professional work with the canvas, the app provides a customizable user interface inspired by desktop Integrated Development Environments (IDEs):

* **Panel System:** Information panels can be either docked or floating above the canvas.

* **Adaptive States:** Interface elements support compact (collapsed to an icon and name) and expanded (maximum detail) views.

* **Dynamic Themes:** The color scheme of the interface can reactively adapt to the visual style and content of the currently open album.

## 10. Technical Challenges and Architecture

### 10.2. Coordinate Math and Frames

* **Challenge:** The need to process intersections of element Bounding Boxes with frames (dynamic ownership) during group movement, ensuring it doesn't block the Main Thread.

### 10.3. Media and Memory Management (OOM Prevention)

* **Challenge:** The application stores only file URIs, not the actual images.

* **Solutions:**

    * Using a library for asynchronous image loading (Coil) with aggressive caching.

    * Implementing "downsampling" (reducing bitmap quality) when the camera is zoomed out heavily to avoid `OutOfMemoryError`, and loading high-res versions when zooming in.

### 10.4. State Management and Layers (IDE UI)

* **Challenge:** The application features a complex interface with floating and docked panels, as well as an editing mode (object selection).

* **Solutions:**

    * State management via the MVI pattern.

    * **Critical Separation:** Strict separation of the Canvas State (nodes, coordinates) and the Interface State (which panels are open, which object is selected) so that UI changes do not trigger a recomposition of the heavy canvas.

### 10.5. Persistence (Data Saving)

* **Challenge:** Fast app startup and reliable autosaving are required.

* **Solutions:**

    * Storing metadata (project list, UI state) in a relational database (Room).

    * Serializing the entire Scene Graph into JSON format (using kotlinx-serialization) to save the infinite canvas structure.
