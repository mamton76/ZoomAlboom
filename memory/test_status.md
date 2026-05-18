# Test status

- `LodResolverTest` has 6 pre-existing failures (`Method d in android.util.Log not mocked` and a "Hidden vs Full" assertion mismatch) unrelated to ongoing work.
- `CommandHistoryTest.snapshot and restore round-trip preserves stacks` fails on the base branch (`expected:<3> but was:<1>`) — verified by stashing local changes and re-running. Pre-existing, not a regression from any current slice.
- Do not consider these regressions when evaluating PR readiness.
