package app.framy

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreJsonTest {
    private val canvas = CanvasState(
        courses = listOf(Course(1, "CSE215", "Data Structures")),
        items = listOf(CanvasItem("7", "Announcement", "Midterm moved", 1, 1000, "https://x/1", "Room NAC 512", read = true),
            CanvasItem("8", "Conversation", "Hi", null, 2000, "", "")),
        planner = listOf(PlannerItem("assignment:5", "Lab 6", 1, 3000, false, "https://x/a/5")),
        seen = setOf("7", "8"), remindersSent = setOf("assignment:5:24h"), done = setOf("quiz:2"),
        downloaded = setOf(42L), firstRunDone = true, tokenRejected = true,
    )
    private val mail = MailState(7, 3000, listOf(MailItem(3000, "Registrar", "reg@nsu.example", "Advising", 4000, "Opens Monday", true)),
        setOf("promo@shop.example"), live = true, authRejected = true)

    @Test fun roundTrip() {
        assertEquals(canvas, StoreJson.canvasFromJson(StoreJson.canvasToJson(canvas)))
        assertEquals(mail, StoreJson.mailFromJson(StoreJson.mailToJson(mail)))
    }

    @Test fun corruptFileGivesEmptyState() {
        assertEquals(CanvasState(), StoreJson.canvasFromJson("{oops"))
        assertEquals(MailState(), StoreJson.mailFromJson(""))
    }
}
