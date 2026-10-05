package example

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greetsByName() {
        assertEquals("Hello, Kotlin!", greeting("Kotlin"))
    }
}
