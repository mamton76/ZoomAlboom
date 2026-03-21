package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamton.zoomalbum.core.designsystem.AccentCyan
import com.mamton.zoomalbum.core.designsystem.PanelBackground
import com.mamton.zoomalbum.core.designsystem.PanelBorder
import com.mamton.zoomalbum.core.designsystem.TextPrimary
import com.mamton.zoomalbum.core.designsystem.TextSecondary
import com.mamton.zoomalbum.feature.ide_ui.ui.panels.FrameListPanel
import com.mamton.zoomalbum.feature.ide_ui.ui.panels.MediaLibraryPanel
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeAction
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelPosition
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelState
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelTab

private val LEFT_PANEL_TABS = listOf(PanelTab.MediaLibrary, PanelTab.FrameList)
private val LEFT_POSITIONS = setOf(PanelPosition.LeftTop, PanelPosition.LeftBottom)

@Composable
fun DockedPanel(
    panel: PanelState,
    onAction: (IdeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val innerTabs = if (panel.position in LEFT_POSITIONS) LEFT_PANEL_TABS else emptyList()

    val enterTransition = when (panel.position) {
        PanelPosition.LeftTop, PanelPosition.LeftBottom -> slideInHorizontally { -it }
        PanelPosition.RightTop, PanelPosition.RightBottom -> slideInHorizontally { it }
        PanelPosition.Top -> slideInVertically { -it }
        PanelPosition.Bottom -> slideInVertically { it }
        PanelPosition.Floating -> slideInHorizontally { -it }
    }
    val exitTransition = when (panel.position) {
        PanelPosition.LeftTop, PanelPosition.LeftBottom -> slideOutHorizontally { -it }
        PanelPosition.RightTop, PanelPosition.RightBottom -> slideOutHorizontally { it }
        PanelPosition.Top -> slideOutVertically { -it }
        PanelPosition.Bottom -> slideOutVertically { it }
        PanelPosition.Floating -> slideOutHorizontally { -it }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PanelHeader(
            title = panel.title,
            isExpanded = panel.isExpanded,
            position = panel.position,
            onToggleExpand = { onAction(IdeAction.TogglePanelExpanded(panel.id)) },
        )

        AnimatedVisibility(
            visible = panel.isExpanded,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            Column {
                if (innerTabs.size > 1) {
                    InnerTabBar(
                        tabs = innerTabs,
                        activeTab = panel.activeTab,
                        onTabSelected = { onAction(IdeAction.SelectTab(panel.id, it)) },
                    )
                }
                Box(modifier = Modifier.fillMaxHeight()) {
                    InnerTabContent(activeTab = panel.activeTab)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    isExpanded: Boolean,
    position: PanelPosition,
    onToggleExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(PanelBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        val arrow = when {
            position == PanelPosition.RightTop || position == PanelPosition.RightBottom -> if (isExpanded) "›" else "‹"
            position == PanelPosition.Top -> if (isExpanded) "▲" else "▼"
            position == PanelPosition.Bottom -> if (isExpanded) "▼" else "▲"
            else -> if (isExpanded) "‹" else "›" // LeftTop, LeftBottom, Floating
        }
        Box(
            modifier = Modifier
                .padding(4.dp)
                .clickable(onClick = onToggleExpand),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = arrow, color = TextSecondary, fontSize = 18.sp)
        }
    }
    HorizontalDivider(color = PanelBorder, thickness = 1.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InnerTabBar(
    tabs: List<PanelTab>,
    activeTab: PanelTab,
    onTabSelected: (PanelTab) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0),
        containerColor = PanelBackground,
        contentColor = AccentCyan,
        divider = { HorizontalDivider(color = PanelBorder, thickness = 1.dp) },
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == activeTab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.label,
                        fontSize = 12.sp,
                        color = if (tab == activeTab) AccentCyan else TextSecondary,
                    )
                },
            )
        }
    }
}

@Composable
private fun InnerTabContent(activeTab: PanelTab) {
    when (activeTab) {
        PanelTab.MediaLibrary -> MediaLibraryPanel()
        PanelTab.FrameList -> FrameListPanel(
            frames = emptyList(),
            onDeleteFrame = {},
        )
    }
}