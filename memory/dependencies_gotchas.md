# Dependency gotchas

Compose BOM `2026.02.01` + AGP `9.0.1` specifics that have caught us:

- **No `material-icons-core`** — use Unicode chars (`‹` `›` `▲` `▼` `⠿`) instead of `Icons.Default.*`.
- **`TabRow` is deprecated** — use `PrimaryTabRow` with `@OptIn(ExperimentalMaterial3Api::class)`.
- **`PrimaryTabRow.divider`** takes `@Composable () -> Unit`, not a `Color`.
- **`Modifier.offset { IntOffset }`** needs `import androidx.compose.foundation.layout.offset`.
