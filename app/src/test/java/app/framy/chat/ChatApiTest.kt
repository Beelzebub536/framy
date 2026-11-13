package app.framy.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatApiTest {
    @Test fun parsesWorkerRows() {
        val c = ChatApi.parse("""[{"id":9,"nick":"Rafi","tag":"a3f2","body":"Mail is late","is_dev":false,"created_at":"2026-11-14T10:00:00.123Z"},
            {"id":8,"nick":"Anonymous","tag":"00ff","body":"Fixed","is_dev":true,"created_at":"2026-11-14T09:00:00Z"}]""")
        assertEquals(listOf(9L, 8L), c.map { it.id })
        assertEquals(true, c[1].isDev)
        assertEquals(1794650400123L, c[0].createdAt)
    }
}
