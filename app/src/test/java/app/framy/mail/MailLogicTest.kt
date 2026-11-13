package app.framy.mail

import app.framy.MailItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MailLogicTest {
    @Test fun cursorFirstRunNoBackfill() = assertEquals(MailLogic.CursorStep(null, 3000), MailLogic.cursor(-1, -1, 7, 3000))
    @Test fun cursorValidityChangeResets() = assertEquals(MailLogic.CursorStep(null, 15), MailLogic.cursor(7, 2990, 8, 15))
    @Test fun cursorNormal() = assertEquals(MailLogic.CursorStep(2991, null), MailLogic.cursor(7, 2990, 7, 3000))
    @Test fun cursorNothingNew() = assertEquals(MailLogic.CursorStep(null, null), MailLogic.cursor(7, 3000, 7, 3000))

    @Test fun hiddenCaseInsensitive() {
        val items = listOf(MailItem(1, "Shop", "Promo@Shop.example", "Sale", 0, ""), MailItem(2, "Reg", "reg@nsu.example", "Advising", 0, ""))
        assertEquals(listOf(2L), MailLogic.visible(items, setOf("promo@shop.example")).map { it.uid })
    }

    @Test fun htmlOnlySnippet() = assertEquals("Hi there", MailLogic.snippet(null, "<p>Hi&nbsp;<b>there</b></p>"))
    @Test fun plainSnippetPreferredAndCut() = assertEquals("a".repeat(120), MailLogic.snippet("a".repeat(300), "<p>x</p>"))
    @Test fun senderFallsBackToAddress() {
        assertEquals("a@b.c", MailLogic.sender(null, "a@b.c"))
        assertEquals("a@b.c", MailLogic.sender("  ", "a@b.c"))
        assertEquals("Registrar", MailLogic.sender("Registrar", "a@b.c"))
    }
}
