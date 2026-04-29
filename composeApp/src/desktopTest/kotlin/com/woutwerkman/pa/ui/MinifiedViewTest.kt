package com.woutwerkman.pa.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.minified.MinifiedView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class MinifiedViewTest {

    private val profile = PresentationProfile(
        title = "KotlinConf 2026",
        bulletPoints = mapOf(
            "a" to "Introduction",
            "b" to "Live demo",
            "c" to "Summary",
        ),
    )

    @Test
    fun emptyStateShowsDropHint() = runComposeUiTest {
        setContent {
            AppTheme {
                MinifiedView(
                    state = PresentationState(),
                    onEvent = {},


                )
            }
        }

        onNodeWithText("Drop a .json presentation file here").assertIsDisplayed()
    }

    @Test
    fun idleStateShowsTitleAndStartButton() = runComposeUiTest {
        setContent {
            AppTheme {
                MinifiedView(
                    state = PresentationState(profile = profile),
                    onEvent = {},


                )
            }
        }

        onNodeWithText("KotlinConf 2026").assertIsDisplayed()
        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun startButtonSendsStartEvent() = runComposeUiTest {
        val events = mutableListOf<PresentationEvent>()

        setContent {
            AppTheme {
                MinifiedView(
                    state = PresentationState(profile = profile),
                    onEvent = { events.add(it) },


                )
            }
        }

        onNodeWithText("Start").performClick()
        assertEquals<List<PresentationEvent>>(listOf(PresentationEvent.Start), events)
    }

    @Test
    fun activeStateShowsTimerAndBulletText() = runComposeUiTest {
        setContent {
            AppTheme {
                MinifiedView(
                    state = run {
                        val now = Clock.System.now()
                        PresentationState(
                            profile = profile,
                            isActive = true,
                            currentBulletIndex = 1,
                            presentationStartTime = now - 5.seconds,
                            bulletStartTime = now - 5.seconds,
                        )
                    },
                    onEvent = {},


                )
            }
        }

        onNodeWithText("Live demo").assertIsDisplayed()
        onNode(hasText("0:05")).assertIsDisplayed()
    }

    @Test
    fun activeStateShowsProgressBar() = runComposeUiTest {
        setContent {
            AppTheme {
                MinifiedView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 0,
                    ),
                    onEvent = {},


                )
            }
        }

        onNodeWithText("Introduction").assertIsDisplayed()
    }
}
