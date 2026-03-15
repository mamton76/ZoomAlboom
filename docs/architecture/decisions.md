# Architecture Decisions

## 1. Infinite canvas is the primary interaction model
The product is built around spatial navigation, not page-by-page browsing.

## 2. Frames are navigation anchors, not only visual containers
Frames structure the canvas and support transitions through album space.

## 3. Canvas state is separate from IDE overlay state
This avoids unnecessary recomposition and keeps interaction logic manageable.

## 4. Shared transform layer for camera movement
Pan / zoom / rotation should be handled through a shared transform strategy rather than by re-laying out all nodes during gestures.

## 5. Persistence is split between Room and JSON
Structured metadata lives in Room; scene graph content lives in serialized JSON.

## 6. Android-first implementation
The initial architecture is optimized for Android and Jetpack Compose rather than for full cross-platform support.
