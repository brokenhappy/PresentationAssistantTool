package com.woutwerkman.pa.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.control.SpeakerNotesView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class SpeakerNotesViewTest {

    private val profile = PresentationProfile(
        title = "Test Talk",
        bulletPoints = mapOf(
            "a" to "Opening remarks",
            "b" to "Main content",
            "c" to "Closing thoughts",
        ),
    )

    @Test
    fun showsCurrentBulletText() = runComposeUiTest {
        setContent {
            AppTheme {
                SpeakerNotesView(
                    state = run {
                        val now = Clock.System.now()
                        PresentationState(
                            profile = profile,
                            isActive = true,
                            currentBulletIndex = 1,
                            presentationStartTime = now - 15.seconds,
                            bulletStartTime = now - 15.seconds,
                        )
                    },
                    onSwitchToControl = {},
                )
            }
        }

        onNodeWithText("Main content").assertIsDisplayed()
    }

    @Test
    fun showsPreviousBulletText() = runComposeUiTest {
        setContent {
            AppTheme {
                SpeakerNotesView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 1,
                    ),
                    onSwitchToControl = {},
                )
            }
        }

        onNodeWithText("Opening remarks").assertIsDisplayed()
    }

    @Test
    fun showsNextBulletText() = runComposeUiTest {
        setContent {
            AppTheme {
                SpeakerNotesView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 1,
                    ),
                    onSwitchToControl = {},
                )
            }
        }

        onNodeWithText("Closing thoughts").assertIsDisplayed()
    }

    @Test
    fun showsBulletCounter() = runComposeUiTest {
        setContent {
            AppTheme {
                SpeakerNotesView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 0,
                    ),
                    onSwitchToControl = {},
                )
            }
        }

        onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun showsTimer() = runComposeUiTest {
        setContent {
            AppTheme {
                SpeakerNotesView(
                    state = run {
                        val now = Clock.System.now()
                        PresentationState(
                            profile = profile,
                            isActive = true,
                            currentBulletIndex = 0,
                            presentationStartTime = now - 120.seconds,
                            bulletStartTime = now - 90.seconds,
                        )
                    },
                    onSwitchToControl = {},
                )
            }
        }

        onNode(hasText("1:30")).assertIsDisplayed()
        onNode(hasText("2:00")).assertIsDisplayed()
    }

    @Test
    fun backToControlsButtonWorks() = runComposeUiTest {
        var switchCalled = false

        setContent {
            AppTheme {
                SpeakerNotesView(
                    state = PresentationState(
                        profile = profile,
                        isActive = true,
                        currentBulletIndex = 0,
                    ),
                    onSwitchToControl = { switchCalled = true },
                )
            }
        }

        onNodeWithText("Back to Controls").performClick()
        assertTrue(switchCalled)
    }
}
