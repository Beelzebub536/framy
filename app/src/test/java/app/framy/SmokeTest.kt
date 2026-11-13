package app.framy

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    // Fails if the JVM test classpath only has android.jar's stubbed org.json.
    @Test fun smoke() = assertEquals(1, org.json.JSONObject("{\"a\":1}").getInt("a"))
}
