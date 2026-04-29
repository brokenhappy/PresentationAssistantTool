package com.woutwerkman.pa.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.expanded.ExpandedView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ExpandedViewTest {

    private val profile = PresentationProfile(
        title = "KotlinConf 2026",
        bulletPoints = mapOf(
            "a" to "Introduction",
            "b" to "Live demo",
            "c" to "Summary",
        ),
    )

    @Test
    fun noProfileShowsEmptyMessage() = runComposeUiTest {
        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(),
                    onEvent = {},
                )
            }
        }

        onNodeWithText("No presentation loaded").assertIsDisplayed()
    }

    @Test
    fun showsBulletPoints() = runComposeUiTest {
        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(profile = profile),
                    onEvent = {},
                )
            }
        }

        onNodeWithText("Introduction").assertIsDisplayed()
        onNodeWithText("Live demo").assertIsDisplayed()
        onNodeWithText("Summary").assertIsDisplayed()
    }

    @Test
    fun clickingBulletPointSendsGoToEvent() = runComposeUiTest {
        val events = mutableListOf<PresentationEvent>()

        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 0,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        onNodeWithText("Summary").performClick()
        assertEquals(PresentationEvent.GoTo(2), events.last())
    }

    @Test
    fun activeHeaderShowsBulletCounter() = runComposeUiTest {
        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 1,
                    ),
                    onEvent = {},
                )
            }
        }

        onNodeWithText("Bullet 2 of 3").assertIsDisplayed()
        onNodeWithText("KotlinConf 2026").assertIsDisplayed()
    }

    @Test
    fun statsHeaderShowsRunStatistics() = runComposeUiTest {
        val run = RunRecord(
            id = "r1",
            timestamp = Instant.fromEpochMilliseconds(1000),
            bulletPointDurations = mapOf("a" to 10.seconds, "b" to 20.seconds, "c" to 5.seconds),
        )

        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(profile = profile, runs = listOf(run)),
                    onEvent = {},
                )
            }
        }

        onNodeWithText("Statistics").assertIsDisplayed()
        onNodeWithText("Last run").assertIsDisplayed()
        onNodeWithText("Average").assertIsDisplayed()
    }

    @Test
    fun noRunsShowsNoRunsMessage() = runComposeUiTest {
        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(profile = profile),
                    onEvent = {},
                )
            }
        }

        onNodeWithText("No runs recorded yet").assertIsDisplayed()
    }

    @Test
    fun backButtonCallsOnBack() = runComposeUiTest {
        var backCalled = false

        setContent {
            AppTheme {
                ExpandedView(
                    state = PresentationState(profile = profile),
                    onEvent = {},
                    onBack = { backCalled = true },
                )
            }
        }

        onNodeWithText("← Back").performClick()
        assertEquals(true, backCalled)
    }
}
