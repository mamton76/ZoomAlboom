# Open Architecture Questions

## 1. Unit system
The canvas should probably use abstract units instead of raw pixels.
Need a consistent Units -> DP mapping that respects zoom and device density.

## 2. Dynamic containment
Frame/node intersection recalculation should happen off the main thread.
Need final strategy for keeping containment references in sync.

## 3. Persistence evolution
Current plan: Room + JSON.
Future options: Protobuf or CRDT-based collaborative model.

## 4. Media validation
On project open, missing source URIs must be detected and represented as placeholders.
Need final lifecycle and caching strategy.
