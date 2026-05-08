package com.example.apktest

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun mainActivity_displaysGameHostAndControls() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fragmentGameHost)).check(matches(isDisplayed()))
            onView(withId(R.id.spinnerPlayerPolicy)).check(matches(isDisplayed()))
            onView(withId(R.id.spinnerNpcPolicy)).check(matches(isDisplayed()))
            onView(withId(R.id.spinnerDifficulty)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonApply)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonPause)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonRestart)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonUp)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonLeft)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonDown)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonRight)).check(matches(isDisplayed()))
        }
    }
}
