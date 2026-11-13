package app.framy.mail

import app.framy.MailItem
import app.framy.canvas.CanvasLogic

/** Pure mail rules: UID cursor, hidden-sender filter, snippets. */
object MailLogic {
    /** fetchFromUid: first UID to fetch (null = nothing); resetTo: move the cursor here without notifying. */
    data class CursorStep(val fetchFromUid: Long?, val resetTo: Long?)

    fun cursor(stateValidity: Long, stateLastUid: Long, serverValidity: Long, serverMaxUid: Long): CursorStep = when {
        stateLastUid == -1L || stateValidity != serverValidity -> CursorStep(null, serverMaxUid)
        serverMaxUid > stateLastUid -> CursorStep(stateLastUid + 1, null)
        else -> CursorStep(null, null)
    }

    fun visible(items: List<MailItem>, hidden: Set<String>): List<MailItem> {
        val h = hidden.map { it.lowercase() }.toSet()
        return items.filter { it.fromAddr.lowercase() !in h }
    }

    fun snippet(plain: String?, html: String?): String =
        (plain?.takeIf { it.isNotBlank() }?.replace(Regex("\\s+"), " ")?.trim() ?: CanvasLogic.stripHtml(html ?: "")).take(120)

    fun sender(personal: String?, address: String): String = personal?.takeIf { it.isNotBlank() }?.trim() ?: address
}
