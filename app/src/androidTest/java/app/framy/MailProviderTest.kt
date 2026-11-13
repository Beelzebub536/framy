package app.framy

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Properties
import javax.mail.Session

/** Packaging must keep JavaMail's META-INF provider files, or every IMAP connection fails. */
@RunWith(AndroidJUnit4::class)
class MailProviderTest {
    @Test fun imapsProviderIsPackaged() = assertNotNull(Session.getInstance(Properties()).getStore("imaps"))
}
