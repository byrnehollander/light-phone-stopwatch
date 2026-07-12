package com.byrne.stopwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

@InitialScreen
class StopwatchScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, StopwatchViewModel>(sealedActivity) {

    override val viewModelClass: Class<StopwatchViewModel>
        get() = StopwatchViewModel::class.java

    override fun createViewModel() = StopwatchViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val uiState by viewModel.uiState.collectAsState()

        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(center = LightTopBarCenter.Text("Stopwatch"))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = if (uiState.isReady) uiState.elapsedText else "Loading...",
                        variant = if (uiState.isReady) {
                            elapsedTimeVariant(uiState.elapsedText)
                        } else {
                            LightTextVariant.Copy
                        },
                        lighten = !uiState.isReady,
                        monospace = uiState.isReady,
                        maxLines = 1,
                    )
                }

                LightBottomBar(items = bottomBarItems(uiState))
            }
        }
    }

    private fun elapsedTimeVariant(elapsedText: String): LightTextVariant = when {
        elapsedText.count { it == ':' } == 1 -> LightTextVariant.Title
        elapsedText.length <= 12 -> LightTextVariant.Subtitle
        else -> LightTextVariant.Heading
    }

    private fun bottomBarItems(uiState: StopwatchUiState): List<LightBarButton?> {
        if (!uiState.isReady) return emptyList()

        return when (uiState.stopwatchState) {
            StopwatchState.Idle -> listOf(
                LightBarButton.Text(
                    text = "START",
                    contentDescription = "Start stopwatch",
                    onClick = viewModel::start,
                ),
            )
            is StopwatchState.Running -> listOf(
                LightBarButton.Text(
                    text = "PAUSE",
                    contentDescription = "Pause stopwatch",
                    onClick = viewModel::pause,
                ),
            )
            is StopwatchState.Paused -> listOf(
                LightBarButton.Text(
                    text = "RESET",
                    contentDescription = "Reset stopwatch",
                    onClick = viewModel::reset,
                ),
                LightBarButton.Text(
                    text = "RESUME",
                    contentDescription = "Resume stopwatch",
                    onClick = viewModel::resume,
                ),
            )
        }
    }
}
