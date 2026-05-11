# Widget System

> Related: [data-model.md](data-model.md) | [overview.md](overview.md) | [todo.md §21](../todo.md#21-widget-system) | [PRD §8.8](../product/PRD.md#88-widget-system) | [wizards.md](../product/wizards.md)

Widgets are **canvas-native smart objects** — `CanvasNode.Widget` entries in the scene graph that carry a data source binding and clickable navigation links.

They participate in hit-testing, selection, drag/resize, LOD, and viewport culling identically to `Frame` and `Media` nodes. Unlike static media, widgets render structured album data (places, dates, tags, people, frames) and their internal elements can be clicked to navigate to other parts of the album.

---

## Core Concept

> Widgets are not separate dashboard panels outside the album. They are part of the album world.

A travel album overview frame might contain a route map, a trip calendar, a highlights strip, and a tag cloud. Clicking a city marker on the map zooms to the city's frame. Clicking a tag jumps to a filtered view. The album becomes a "Prezi about life" — an explorable spatial story, not a static gallery.

---

## CanvasNode.Widget

```kotlin
@Serializable
data class Widget(
    override val id: String,
    override val transform: Transform,
    val widgetType: WidgetType,
    val config: WidgetConfig,           // per-type display options (JSON blob with type discriminator)
    val dataSource: WidgetDataSource,
    val links: List<WidgetLink> = emptyList(),
    override val visibilityPolicy: VisibilityPolicy? = null,
) : CanvasNode()
```

---

## WidgetType

```kotlin
@Serializable
enum class WidgetType {
    // Core navigation
    Map, Calendar, Timeline, TagCloud, Highlights, MediaGallery,
    FrameNavigator, Portal,
    // People / relationships
    People, FamilyTree,
    // Travel
    Route, PlacesList, TripCalendar, TravelHighlights,
    // Family / child / school
    MilestoneTimeline, GrowthTimeline, Classmates, DrawingsGallery, Achievements,
    // Cookbook
    RecipeIndex, RecipeCard, RecipeSteps, Ingredients, Seasons, MealCalendar, MapOfTastes,
    RecipePeople, IncompleteRecipes, FamilyTable, CookMode, RecipeTimer,
    // AI Diary
    PeriodSummary, MemoryResurfacing, RecentEntries, NeedsReview, Statistics,
    // Project / educational
    ConceptMap, ProcessTimeline, Checklist, BeforeAfter, AssetStrip,
    // Generic
    SmartTagList,
}
```

---

## Data Source

What data the widget draws from.

```kotlin
@Serializable
sealed class WidgetDataSource {
    /** Nodes matching a filter (by type, tag, frame membership, date range, etc.) */
    @Serializable data class AlbumNodes(val filter: NodeFilter) : WidgetDataSource()

    /** Tags from the album, optionally filtered */
    @Serializable data class AlbumTags(val tags: List<String>? = null) : WidgetDataSource()

    /** Date range of album content */
    @Serializable data class AlbumDates(
        val startMs: Long? = null,
        val endMs: Long? = null,
    ) : WidgetDataSource()

    /** Places referenced in the album */
    @Serializable data class AlbumPlaces(
        val placeIds: List<String>? = null,
    ) : WidgetDataSource()

    /** Widget content defined statically in config (for Portal, FrameNavigator, etc.) */
    @Serializable data class StaticConfig(val json: String) : WidgetDataSource()

    // future: AiDiaryEntries(query), ExternalFeed(url)
}
```

---

## Navigation Links

Clickable elements inside a widget can navigate to any part of the album or beyond.

```kotlin
@Serializable
data class WidgetLink(
    val sourceElementId: String,   // id of the clickable element within the widget's rendered content
    val target: NavigationTarget,
)

@Serializable
sealed class NavigationTarget {
    @Serializable data class ToFrame(val frameId: String) : NavigationTarget()
    @Serializable data class ToNode(val nodeId: String) : NavigationTarget()
    @Serializable data class ToAlbum(val albumId: Long) : NavigationTarget()
    @Serializable data class ToFilteredView(val tagIds: List<String>) : NavigationTarget()
    @Serializable data class ToExternalUri(val uri: String) : NavigationTarget()
}
```

Navigation dispatch triggers an animated camera transition to the target (same as `Transform.toCamera()` used for frame focus).

---

## WidgetConfig

Per-type sealed class carrying display options (show labels, cluster markers, color scheme, layout variant, etc.). Serialized as a JSON blob with a `type` discriminator so new widget types can extend their config without breaking parsers that use `ignoreUnknownKeys`.

Each widget type defines its own config class, e.g.:

```kotlin
@Serializable
data class MapWidgetConfig(
    val showRoute: Boolean = true,
    val showLabels: Boolean = true,
    val clusterMarkers: Boolean = true,
    val colorScheme: String = "default",
) : WidgetConfig()

@Serializable
data class CalendarWidgetConfig(
    val view: CalendarView = CalendarView.MonthGrid,
    val showDensity: Boolean = true,
) : WidgetConfig()

enum class CalendarView { MonthGrid, YearGrid, WeekStrip, DayCards }
```

---

## Renderer Contract

Each widget type provides a Composable renderer registered at app startup.

```kotlin
interface CanvasWidgetRenderer<TConfig : WidgetConfig> {
    val type: WidgetType

    @Composable
    fun Render(
        widget: CanvasNode.Widget,
        config: TConfig,
        renderDetail: RenderDetail,
        onNavigate: (NavigationTarget) -> Unit,
    )
}
```

`RenderDetail` drives the LOD tier:
- `Hidden` — don't render
- `Stub` — simple colored rectangle with widget type icon
- `Preview` — simplified summary (e.g. map shows route shape only; calendar shows month blocks)
- `Full` — complete interactive widget

---

## Interaction Modes

**Edit mode:**
- Widget outer bounds → standard canvas selection + drag/resize handles
- Widget inner elements → NOT navigable (taps go to selection, not navigation)
- Widget settings panel available from context menu

**View / Present mode:**
- Widget outer bounds → no selection; single tap on widget area routes to inner element hit-test
- Widget inner elements (markers, dates, tags, people) → clickable, fire `onNavigate`
- No editing handles shown

**Hit-test layering:**
Widget hit-test has two layers: outer bounding box (for canvas selection) and inner element map (for navigation). The gesture layer must check current mode to route correctly.

---

## LOD & Performance

Widgets support `VisibilityPolicy` identical to other nodes. Recommended stub/preview behavior per type:

| Widget | Preview | Stub |
|--------|---------|------|
| Map | Route shape + major markers only | Rectangle with map icon |
| Calendar | Month blocks (no day detail) | Rectangle with calendar icon |
| Tag Cloud | Top 5 tags only | Rectangle with tag icon |
| People | Avatars only | Rectangle with people icon |
| Recipe Card | Title + hero photo | Rectangle with recipe icon |

---

## Persistence

`CanvasNode.Widget` is serialized in the scene graph nodes array alongside `Frame` and `Media`. `dataSource` and `links` are stored on the node. Widget UI state (open settings panel, selected inner element, temporary filter state) is transient or in `ide_workspaces`.

---

## Widget Categories

### Core Navigation
| Type | Purpose |
|------|---------|
| `Portal` | Clickable object linking to any `NavigationTarget` — simplest widget |
| `FrameNavigator` | Canvas-native table of contents showing frame hierarchy |
| `TagCloud` | Visual tag frequency map; click tag → filtered view or frame |
| `Highlights` / `MediaGallery` | Curated photo grid/strip; click item → source frame |
| `Calendar` | Month/year grid with album dates; click day/month → frame |
| `Map` | Place markers from album; click marker → frame; optional route lines |
| `Timeline` | Chronological navigation strip |

### People & Relationships
| Type | Purpose |
|------|---------|
| `People` | Person avatars with photo count; click → person frame |
| `FamilyTree` | Genealogy graph; click person → frame |

### Travel
| Type | Purpose |
|------|---------|
| `Route` | Trip route with waypoints; click segment/city → frame |
| `PlacesList` | Structured list with date range + photo count per place |
| `TripCalendar` | Travel calendar showing where family was each day |
| `TravelHighlights` | Best photo per city/day |

### Family / Child / School
| Type | Purpose |
|------|---------|
| `MilestoneTimeline` | Key events on chronological axis |
| `GrowthTimeline` | Age-based view from pregnancy to school years |
| `Classmates` | People widget for school classes; supports yearly changes |
| `DrawingsGallery` | Media gallery filtered by art/craft tag |
| `Achievements` | Achievement cards with dates |

### Cookbook
| Type | Purpose |
|------|---------|
| `RecipeIndex` | Searchable list; filter by person/ingredient/season/category |
| `RecipeCard` | Structured single-recipe widget (title, ingredients, steps, photos) |
| `RecipeSteps` | Step-by-step cooking widget; designed for Cook mode |
| `Ingredients` | Ingredient → recipe list |
| `Seasons` | Recipes by season/holiday |
| `MealCalendar` | Calendar specialized for cooking history |
| `MapOfTastes` | Map with recipe-origin regions |
| `RecipePeople` | Recipes by family member/author |
| `IncompleteRecipes` | Workflow: drafts missing photos/ingredients/steps |
| `FamilyTable` | Visual overview of the cookbook as a family table — favorite recipes, people connected to dishes, food traditions, geographic/cultural origins, seasonal and holiday dishes. Designed as the main overview object for a cookbook album. |
| `CookMode` | Interactive cooking helper — large readable step-by-step view, current step highlighted, hands-free friendly, reduced editing chrome. Optimized for use in the kitchen. Post-MVP. |
| `RecipeTimer` | Countdown timer attached to a recipe step. Fires inside Cook mode. Post-MVP. |

### AI Diary
| Type | Purpose |
|------|---------|
| `PeriodSummary` | AI-generated day/week/month/year summary |
| `MemoryResurfacing` | "1 year ago / on this day" strip |
| `RecentEntries` | Latest diary entries or album notes |
| `NeedsReview` | AI-generated items awaiting confirmation |
| `Statistics` | Compact summary (entries, photos, places, people counts) |

### Educational / Project
| Type | Purpose |
|------|---------|
| `ConceptMap` | Concepts + dependency links; click → detail frame |
| `ProcessTimeline` | Project stages with before/after and media evidence |
| `Checklist` | Interactive checklist; items can link to frames |
| `BeforeAfter` | Side-by-side or slider comparison |
| `AssetStrip` | Media strip filtered by frame/person/date/tag |

---

## Wizard Integration

Album creation wizards generate overview frames pre-populated with widgets linked to nested frames. See [wizards.md](../product/wizards.md) for the full wizard system spec.

**Travel wizard example:**
Main frame → Map + Trip Calendar + Highlights + Tag Cloud + Cities List + AI Summary widget → nested frames per city/day linked from widget elements.

**Cookbook wizard example:**
Overview → Recipe Index + Ingredients + Seasons + Family Table + People widget → nested frames per recipe.

**Child/school wizard example:**
Overview → Growth Timeline + Milestones + People + Calendar + Highlights → nested frames per year/event/school class.

**Family/genealogy wizard example:**
Overview → Family Tree + People + Place Map + Timeline → nested frames per person/branch/place/period.

---

## MVP Implementation Order

1. Infrastructure: `CanvasNode.Widget`, renderer contract, hit-test layering, LOD, persistence
2. `Portal` — simplest widget; validates the full infrastructure stack
3. `FrameNavigator` — validates data binding to frame list
4. `TagCloud` — validates tag data source
5. `Calendar` — validates date data source + navigation dispatch
6. `Map` — validates place data source + route rendering
7. `Highlights` / `MediaGallery` — validates node filter data source
