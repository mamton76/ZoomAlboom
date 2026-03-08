Zoom Album

About the project

Zoom Albumis an innovative Android mobile app for spatially organizing media content. The product is based on the concept of an infinite canvas, allowing you to freely arrange files and build visual and logical connections between them using navigation frames.

Core functionality (MVP)
Infinite Canvas:A scalable workspace for freely arranging various types of media files (images, videos, notes).
Frame system:A tool for visually selecting areas on the canvas, creating logical groups and smooth navigation (transitions) between them.
Media library:Basic lists for managing uploaded content and created frames.

Advanced interface (Custom UI)
For a convenient and professional canvas experience, the app offers a customizable user interface inspired by desktop development environments (IDEs):
Panel system:Info panels can be either docked or floating on top of the canvas.
Adaptive states:Interface elements support compact (collapsed to icon and title) and expanded (maximum detailed) views.
Dynamic Themes:The interface's color scheme can be reactively adjusted to the visual style and content of the currently open album.

Tech stack
Platform: Android
Programming language: Kotlin
UI framework:Jetpack Compose (making extensive use of the Canvas API, Modifier.graphicsLayer, and Slot API)
Asynchronous work:Kotlin Coroutines & Flow for non-blocking loading of heavy media files and handling UI state.

Development Focus
The project is divided into two key phases:
Kernel development:Coordinate math, canvas rendering, gesture handling (pan, zoom), and frame-to-frame movement logic.
UI development:Integrate a sophisticated panel system and dynamic themes on top of a pre-built canvas without sacrificing performance.



