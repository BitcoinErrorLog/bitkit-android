package to.bitkit.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class KeyboardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun keyboard_displaysAllButtons() {
        composeTestRule.setContent {
            Keyboard(onClick = {}, onClickBackspace = {})
        }

        composeTestRule.onNodeWithTag("N1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N6").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N8").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N9").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("N0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("NRemove").assertIsDisplayed()
    }

    @Test
    fun keyboard_tripleZero_when_not_decimal() {
        composeTestRule.setContent {
            Keyboard(onClick = {}, isDecimal = false, onClickBackspace = {})
        }
        composeTestRule.onNodeWithTag("N000").assertIsDisplayed()
    }

    @Test
    fun keyboard_decimal_when_decimal() {
        composeTestRule.setContent {
            Keyboard(onClick = {}, isDecimal = true, onClickBackspace = {})
        }
        composeTestRule.onNodeWithTag("N.").assertIsDisplayed()
    }

    @Test
    fun keyboard_button_click_triggers_callback() {
        var clickedValue = ""
        composeTestRule.setContent {
            Keyboard(onClick = { clickedValue = it }, onClickBackspace = {})
        }

        composeTestRule.onNodeWithTag("N5").performClick()
        assert(clickedValue == "5")

        composeTestRule.onNodeWithTag("N.").performClick()
        assert(clickedValue == ".")

        composeTestRule.onNodeWithTag("N0").performClick()
        assert(clickedValue == "0")

    }

    @Test
    fun keyboard_button_click_tripleZero() {
        var clickedValue = ""
        composeTestRule.setContent {
            Keyboard(onClick = { clickedValue = it }, onClickBackspace = {}, isDecimal = false)
        }

        composeTestRule.onNodeWithTag("N000").performClick()
        assert(clickedValue == "000")
    }

}
