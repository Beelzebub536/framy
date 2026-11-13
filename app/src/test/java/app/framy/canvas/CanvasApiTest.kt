package app.framy.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasApiTest {
    @Test fun classifyHiddenVsRejected() {
        assertEquals(Fail.Hidden, classify(401, "/api/v1/courses/5/folders"))
        assertEquals(Fail.Hidden, classify(403, "/api/v1/courses/5/modules"))
        assertEquals(Fail.TokenRejected, classify(401, "/api/v1/users/self/activity_stream"))
        assertEquals(Fail.TokenRejected, classify(403, "/api/v1/users/self/courses"))
        assertEquals(null, classify(200, "/api/v1/users/self"))
        assertEquals(Fail.Other(500), classify(500, "/api/v1/courses/5/modules"))
    }

    @Test fun nextLinkParsed() {
        val h = "<https://x/api/v1/courses/1/modules?page=1>; rel=\"current\",<https://x/api/v1/courses/1/modules?page=2&per_page=100>; rel=\"next\""
        assertEquals("https://x/api/v1/courses/1/modules?page=2&per_page=100", nextLink(h))
        assertEquals(null, nextLink("<https://x/a>; rel=\"last\""))
        assertEquals(null, nextLink(null))
    }
}
