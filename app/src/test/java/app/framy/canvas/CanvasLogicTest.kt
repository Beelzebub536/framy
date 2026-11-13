package app.framy.canvas

import app.framy.CanvasItem
import app.framy.PlannerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CanvasLogicTest {
    private val H = 3_600_000L

    @Test fun parseCoursesFallsBackToNameThenId() {
        val c = CanvasLogic.parseCourses("""[{"id":1,"course_code":"CSE215","name":"Data Structures"},
            {"id":2,"course_code":null,"name":"Ethics"},{"id":3}]""")
        assertEquals(listOf("CSE215", "Ethics", "course 3"), c.map { it.code })
    }

    @Test fun parseActivitySkipsNothingAndSurvivesNulls() {
        val items = CanvasLogic.parseActivity("""[
          {"id":11,"type":"Announcement","title":"Midterm moved","course_id":63177,"created_at":"2026-11-12T05:13:21Z",
           "html_url":"https://x/courses/63177/announcements/9","message":"<p>Room <b>NAC 512</b></p>"},
          {"id":12,"type":"Conversation","course_id":null,"created_at":"2026-11-12T06:00:00Z"}]""")
        assertEquals(2, items.size)
        assertEquals("Room NAC 512", items[0].preview)
        assertEquals(63177L, items[0].courseId)
        val conv = items[1]
        assertEquals(null, conv.courseId); assertEquals("Conversation", conv.title); assertEquals("", conv.htmlUrl)
        assertEquals("12", conv.id)
    }

    @Test fun parsePlannerDropsUndatedAndUnsubmittable() {
        val p = CanvasLogic.parsePlanner("""[
          {"plannable_type":"assignment","plannable_id":5,"course_id":1,"plannable_date":"2026-12-16T17:59:59Z",
           "plannable":{"title":"Lab 6","due_at":"2026-12-16T17:59:59Z"},"submissions":false,"html_url":"/courses/1/assignments/5"},
          {"plannable_type":"quiz","plannable_id":6,"course_id":1,"plannable":{"title":"Quiz 3","due_at":null},"submissions":false},
          {"plannable_type":"planner_note","plannable_id":7,"plannable_date":"2026-12-01T00:00:00Z","plannable":{"title":"note","todo_date":"2026-12-01T00:00:00Z"}},
          {"plannable_type":"assignment","plannable_id":8,"course_id":2,"plannable":{"title":"Essay","due_at":"2026-12-01T10:00:00Z"},
           "submissions":{"submitted":true,"graded":false},"html_url":"/courses/2/assignments/8"}]""",
            "https://x.instructure.com")
        assertEquals(listOf("assignment:5", "assignment:8"), p.map { it.key })
        assertEquals(false, p[0].submitted)
        assertEquals(true, p[1].submitted)
        assertEquals("https://x.instructure.com/courses/1/assignments/5", p[0].htmlUrl)
        assertEquals(1797443999000L, p[0].dueAt)
    }

    private fun item(id: String, course: Long?) = CanvasItem(id, "Announcement", "t", course, 0, "", "")

    @Test fun toNotifyFiltersInactiveAndSeen() {
        val fetched = listOf(item("1", 10), item("2", 99), item("3", null), item("4", 10))
        assertEquals(listOf("1", "3"), CanvasLogic.toNotify(fetched, setOf("4"), setOf(10L), firstRun = false).map { it.id })
        assertTrue(CanvasLogic.toNotify(fetched, emptySet(), setOf(10L), firstRun = true).isEmpty())
    }

    private fun due(left: Long, submitted: Boolean = false) = PlannerItem("assignment:1", "Lab", 1, 1_000_000_000L + left, submitted, "")

    @Test fun reminderNearestOnly() {
        val now = 1_000_000_000L
        assertEquals(listOf("3h"), CanvasLogic.dueReminders(listOf(due(2 * H)), emptySet(), emptySet(), now).map { it.second })
        assertEquals(listOf("24h"), CanvasLogic.dueReminders(listOf(due(20 * H)), emptySet(), emptySet(), now).map { it.second })
        assertTrue(CanvasLogic.dueReminders(listOf(due(20 * H)), setOf("assignment:1:24h"), emptySet(), now).isEmpty())
        assertTrue(CanvasLogic.dueReminders(listOf(due(2 * H)), setOf("assignment:1:3h"), emptySet(), now).isEmpty())
        assertTrue(CanvasLogic.dueReminders(listOf(due(2 * H, submitted = true)), emptySet(), emptySet(), now).isEmpty())
        assertTrue(CanvasLogic.dueReminders(listOf(due(2 * H)), emptySet(), setOf("assignment:1"), now).isEmpty())
        assertTrue(CanvasLogic.dueReminders(listOf(due(30 * H)), emptySet(), emptySet(), now).isEmpty())
        assertTrue(CanvasLogic.dueReminders(listOf(due(-H)), emptySet(), emptySet(), now).isEmpty())
    }

    @Test fun isoUtcToDhaka() {
        val ms = CanvasLogic.parseIso("2026-12-16T17:59:59Z")
        assertEquals(1797443999000L, ms)
        assertEquals("16 Dec, 11:59 PM", CanvasLogic.formatDue(ms, ZoneId.of("Asia/Dhaka")))
    }

    @Test fun cappedFive() {
        assertEquals(listOf(1, 2, 3, 4, 5) to 7, capped((1..12).toList()))
        assertEquals(listOf(1, 2, 3) to 0, capped(listOf(1, 2, 3)))
    }
}
