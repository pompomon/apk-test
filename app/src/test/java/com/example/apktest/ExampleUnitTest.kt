package com.example.apktest

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun helloWorldMessageIsCorrect() {
        val message = "Hello World!"
        assertEquals("Hello World!", message)
    }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
