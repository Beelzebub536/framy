package app.framy

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast

object Downloads {
    /** Saves to Downloads/Framy/ via the system downloader. The url is Canvas's verifier-signed link: no token is sent. */
    fun enqueue(ctx: Context, fileId: Long, url: String, name: String) {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file-$fileId" }
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(safe)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // shortcut: Android 8–9 would need the storage permission for public Downloads, so they get the app's own Download folder.
        if (Build.VERSION.SDK_INT >= 29) req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Framy/$safe")
        else req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, safe)
        try {
            ctx.getSystemService(DownloadManager::class.java).enqueue(req)
            Store.updateCanvas { it.copy(downloaded = it.downloaded + fileId) }
            Toast.makeText(ctx, "Downloading $safe", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't start the download", Toast.LENGTH_SHORT).show()
        }
    }
}
