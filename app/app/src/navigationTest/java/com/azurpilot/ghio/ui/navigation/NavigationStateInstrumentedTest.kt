package com.azurpilot.ghio.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azurpilot.ghio.theme.AzurPilotTheme
import com.azurpilot.ghio.ui.motion.AppNavigation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证真实导航宿主的状态恢复、参数保存及交互式返回取消和提交。
 *
 * Verifies state restoration, argument preservation, and interactive back cancellation and
 * completion through the real navigation host.
 */
@RunWith(AndroidJUnit4::class)
class NavigationStateInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun restoresTaskArgumentsAndParentDraft() {
        lateinit var navigator: AppNavigator
        val restoration = StateRestorationTester(compose)
        val taskName = "舰队/配置 ?mode=1#任务"
        restoration.setContent {
            navigator = rememberAppNavigator()
            AzurPilotTheme {
                AppNavigation(navigator) { swipe ->
                    entry<AppRoute.Main> {
                        var draft by rememberSaveable { mutableStateOf("") }
                        Column {
                            BasicText("Home")
                            TextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.testTag("draft"),
                            )
                        }
                    }
                    entry<AppRoute.Task>(swipeDismiss = swipe) { BasicText(it.taskName) }
                }
            }
        }
        compose.onNodeWithTag("draft").performTextInput("Saved input")
        compose.runOnIdle { navigator.push(AppRoute.Task(taskName)) }
        compose.onNodeWithText(taskName).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(taskName).assertIsDisplayed()
        compose.runOnIdle { navigator.pop() }
        compose.onNodeWithTag("draft").assertTextEquals("Saved input")
    }

    @Test
    fun swipeReturnCanCancelThenCommitWithoutDuplicatingEntries() {
        lateinit var navigator: AppNavigator
        compose.setContent {
            navigator = rememberAppNavigator()
            AzurPilotTheme {
                AppNavigation(navigator) { swipe ->
                    entry<AppRoute.Main> { BasicText("Home") }
                    entry<AppRoute.Display>(swipeDismiss = swipe) {
                        Box(Modifier.fillMaxSize().testTag("detail")) { BasicText("Detail") }
                    }
                }
            }
        }
        compose.runOnIdle {
            repeat(5) { navigator.push(AppRoute.Display) }
            assertEquals(2, navigator.backStack.size)
        }
        compose.onNodeWithTag("detail").performTouchInput {
            down(Offset(width * 0.1f, height * 0.5f))
            moveTo(Offset(width * 0.15f, height * 0.5f), delayMillis = 100)
            moveTo(Offset(width * 0.2f, height * 0.5f), delayMillis = 100)
            advanceEventTime(300)
            up()
        }
        compose.onNodeWithText("Detail").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AppRoute.Display, navigator.current) }
        compose.onNodeWithTag("detail").performTouchInput {
            down(Offset(width * 0.1f, height * 0.5f))
            moveTo(Offset(width * 0.5f, height * 0.5f), delayMillis = 150)
            moveTo(Offset(width * 0.9f, height * 0.5f), delayMillis = 150)
            advanceEventTime(100)
            up()
        }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, navigator.backStack.size)
            navigator.pop()
            assertEquals(AppRoute.Main, navigator.current)
        }
    }
}
