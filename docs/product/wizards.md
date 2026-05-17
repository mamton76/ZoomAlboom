# Album Wizard System

> Related: [PRD](PRD.md) | [future-ideas](future-ideas.md) | [widgets](../architecture/widgets.md) | [open-questions §9](../architecture/open-questions.md#9-portal--widget-target-movement-semantics) | [todo §21](../todo.md#21-widget-system)

Wizards help users generate a structured ZoomAlboom album from media, diary entries, tags, dates, places, or manual input. A wizard is not a flat template — it creates a **spatial story structure** that can be edited freely afterward.

---

## Core Idea

A wizard should not merely create a visual skeleton. It should generate the geometry of the entire canvas:

1. Generate a main overview frame.
2. Populate the overview with widgets, key media, and summary objects.
3. Generate nested frames for deeper sections of the story.
4. Link overview objects to nested frames via portal links and widget navigation targets.
5. Leave everything fully editable after generation.

The wizard creates a starting layout, not a rigid final product.

---

## Geometry-First Generation

The wizard decides how to form the geometry of the whole canvas. Likely strategy:

- Start with a **main overview frame** — the "cover" of the story.
- Place high-level widgets on the overview: map, calendar, highlights, tag cloud, people, route, recipe index, etc.
- Generate **deeper frames** around or inside the overview structure, one per meaningful unit (city, day, recipe, person, year, event).
- Link overview objects to deeper frames via portals, widget navigation targets, and smart links.
- Establish a default **camera path / frame order** for view mode.

This creates the "Prezi about life" experience: the user starts at the overview and zooms into details.

---

## Data Sources

A wizard may draw from:

- manually selected photos and videos
- imported media folders
- AI Diary entries (text, topics, people, emotions, milestones)
- tags
- dates
- places / EXIF / GPS metadata
- people metadata
- user-provided text prompts
- recipe data
- external sources (future)

---

## User Flow Variants

| Starting point | Wizard role |
|---|---|
| No content yet | Full blank-slate wizard — user answers prompts, wizard builds everything |
| Media already imported | Wizard organizes existing media into frames and widgets |
| AI Diary data available | Wizard extracts events and generates story units from diary entries |
| Manually started album | User asks AI mid-edit to suggest tags, frames, widgets, and layout |

The wizard always produces an editable result. The user may approve, reject, or modify any part of the generated structure.

---

## Wizard Output

A wizard may create:

- frames (overview + nested)
- media nodes placed within frames
- widget nodes (map, calendar, highlights, tag cloud, people, etc.)
- portal links from overview objects to deeper frames
- tags applied to nodes
- a suggested frame hierarchy and navigation order
- suggested titles and descriptions
- a default camera path
- incomplete sections marked for review (e.g. "add photos here")

---

## AI Diary → ZoomAlboom Generation Pipeline

When AI Diary data is available, the wizard can generate album structures automatically.

### Pipeline stages

**1. Raw sources**
- diary entries (text, voice notes, photos/videos)
- calendar events
- tags, places, people

**2. Signal extraction**
- dates, locations, people mentioned
- media clusters (by time, place, or topic)
- emotional tone, recurring topics
- important milestones

**3. Event candidates**
- "trip day", "birthday", "school event", "cooking session", "family visit", "child milestone"
- AI proposes event candidates; user accepts / rejects / merges / splits

**4. Draft story units**
- suggested frame with title + summary
- suggested media selection
- suggested tags
- suggested widgets and portal links

**5. User confirmation**
- accept / reject / edit per unit
- choose photos, rename frames, adjust layout
- approve or skip AI-proposed sections

**6. ZoomAlboom layout generation**
- create frames, place media, create overview widgets
- generate navigation order and portal links
- preserve provenance back to source diary/media items

**Core principle:** AI suggests structure, the user remains the final editor. Generated albums must be editable and explainable.

---

## Travel Wizard

Creates a travel story album.

**Overview frame contains:**
- Route map widget
- Trip calendar widget
- Highlights widget
- Tag cloud widget
- Places/cities list widget
- AI trip summary widget

**Nested frames (one per country / city / day / event):**
- media cluster for that location/day
- linked from map markers, calendar dates, city list items, and highlights in the overview

---

## Cookbook Wizard

Creates a visual recipe collection.

**Overview frame contains:**
- Recipe index widget
- Ingredients widget
- Seasons widget
- Family table widget
- Recipe people widget
- Incomplete recipes widget (drafts missing photos/steps)

**Nested frames (one per recipe / category / person / season):**
- recipe card widget
- step-by-step photos
- linked from recipe index, seasons, and family table

---

## Child / School Album Wizard

Creates a child's life story album organized by age and milestones.

**Overview frame contains:**
- Growth timeline widget
- Milestones widget
- People widget (family, teachers, classmates)
- Calendar widget
- Highlights widget

**Nested frames (one per age / year / school class / event / hobby / place):**
- media for that period
- classmates widget
- drawings gallery
- achievements widget
- linked from timeline and growth widget

---

## Family / Genealogy Wizard

Creates a family history album organized around people, places, and generations.

**Overview frame contains:**
- People widget
- Family tree widget
- Place map widget
- Timeline widget

**Nested frames (one per person / family branch / place / period / story):**
- photos, documents, old media
- linked from family tree nodes and place map

---

## Shareable Output

Wizard-generated albums should be exportable as shareable read-only spatial stories. See [future-ideas.md — Import / Export / Publishing](future-ideas.md#import--export--publishing) for the planned export formats.

---

## Important Principle

Wizard-generated content must remain fully editable. After generation, the user can:

- move, resize, or delete any frame
- swap or remove widgets
- replace media
- change or remove portal links
- regenerate selected sections
- manually override AI decisions
- approve or reject AI-suggested content in stages

The wizard is a starting point, not a locked template.

---

## Implementation Notes (Future)

Wizard generation is post-MVP infrastructure. However, the canvas data model already supports everything a wizard needs:

- `CanvasNode.Widget` with `WidgetDataSource` and `WidgetLink` (see [widgets.md](../architecture/widgets.md))
- `CanvasNode.Frame` with computed membership + manual overrides ([frame-membership.md](../architecture/frame-membership.md)) — wizards write `(Included, Wizard)` overrides for generated content
- `NavigationTarget` sealed class for portal links
- `CanvasCommand` snapshot-based undo so wizard output is fully undoable as a batch

When the widget infrastructure ships (see [todo §21](../todo.md#21-widget-system)), the wizard generator becomes a pure data transformation: inputs → `List<CanvasNode>` to add to the scene graph.
