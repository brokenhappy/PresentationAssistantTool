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
import com.woutwerkman.pa.ui.control.ControlView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class ControlViewTest {

    private val profile = PresentationProfile(
        title = "Test Talk",
        bulletPoints = mapOf(
            "a" to "First",
            "b" to "Second",
            "c" to "Third",
        ),
    )

    @Test
    fun inactiveWithProfileShowsStartButton() = runComposeUiTest {
        setContent {
            AppTheme {
                ControlView(
                    state = PresentationState(profile = profile),
                    onEvent = {},
                    onSwitchToNotes = {},
                    onSwitchToExpanded = {},
                )
            }
        }

        onNodeWithText("Start Presentation").assertIsDisplayed()
    }

    @Test
    fun startButtonSendsStartEvent() = runComposeUiTest {
        val events = mutableListOf<PresentationEvent>()

        setContent {
            AppTheme {
                ControlView(
                    state = PresentationState(profile = profile),
                    onEvent = { events.add(it) },
                    onSwitchToNotes = {},
                    onSwitchToExpanded = {},
                )
            }
        }

        onNodeWithText("Start Presentation").performClick()
        assertEquals<List<PresentationEvent>>(listOf(PresentationEvent.Start), events)
    }

    @Test
    fun activeStateShowsTimerAndBulletText() = runComposeUiTest {
        setContent {
            AppTheme {
                ControlView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 0,
                        currentBulletElapsed = 42.seconds,
                    ),
                    onEvent = {},
                    onSwitchToNotes = {},
                    onSwitchToExpanded = {},
                )
            }
        }

        onNode(hasText("0:42")).assertIsDisplayed()
        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun navigationButtonsAreVisible() = runComposeUiTest {
        setContent {
            AppTheme {
                ControlView(
                    state = PresentationState(profile = profile),
                    onEvent = {},
                    onSwitchToNotes = {},
                    onSwitchToExpanded = {},
                )
            }
        }

        onNodeWithText("Speaker Notes").assertIsDisplayed()
        onNodeWithText("Expanded View").assertIsDisplayed()
    }

    @Test
    fun switchToNotesCalled() = runComposeUiTest {
        var notesCalled = false

        setContent {
            AppTheme {
                ControlView(
                    state = PresentationState(profile = profile),
                    onEvent = {},
                    onSwitchToNotes = { notesCalled = true },
                    onSwitchToExpanded = {},
                )
            }
        }

        onNodeWithText("Speaker Notes").performClick()
        assertEquals(true, notesCalled)
    }
}
