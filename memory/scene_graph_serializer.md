# SceneGraphSerializer — gotchas

## `CanvasNode` polymorphism discriminator is the FQCN, not a stable short name

`CanvasNode.Frame` and `CanvasNode.Media` carry **no `@SerialName`**. kotlinx-serialization defaults to the fully-qualified class name for the `"type"` field, so today's on-disk JSON looks like:

```json
{
  "type": "com.mamton.zoomalbum.domain.model.CanvasNode.Frame",
  ...
}
```

Not `"type": "FRAME"`. The architecture docs (`data-model.md`) display the short form `"FRAME"` / `"MEDIA"` aspirationally — that wire shape would require explicit `@SerialName("FRAME")` / `@SerialName("MEDIA")` plus a JSON migration.

**Why this matters:**
- Any new migration code in `SceneGraphSerializer` that wants to gate on node type must **not** read `obj["type"]` and compare against `"FRAME"` — it will never match. The Slice A `Frame.background` migration learned this the hard way; the final implementation gates on the *presence of the `background` key* (only legacy frames had one) instead.
- Renaming or moving the `CanvasNode` package would break every existing scene graph.

If we ever want to ship stable short discriminators we'd need a two-step migration: add `@SerialName(...)` AND read both old (FQCN) and new (short) values during a transition window.

## Test-only legacy JSON should be synthesized, not hand-written

`SceneGraphSerializerTest` tests legacy migrations by serializing a current-shape node and then *editing* the resulting `JsonObject` — moving fields, dropping discriminators — to mimic the legacy shape. This insulates the tests from the FQCN discriminator above (and from any other shape change we make without a migration). Don't paste hand-written `"type": "FRAME"` JSON into tests; it won't deserialize.

## `ignoreUnknownKeys = true` covers most schema additions

The serializer's `Json` config has `ignoreUnknownKeys = true`. Adding optional fields to `CanvasNode.*` or to nested types deserializes cleanly from older JSON without any extra migration. Removing or renaming a field requires either a default value (then no migration is needed) or a `JsonObject`-rewriting pass like the one used for `Frame.background → Frame.appearance.background`.
