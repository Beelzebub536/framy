package app.framy

import android.app.Application

class FramyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Secrets.init(this)
        Store.init(filesDir)
        Notify.channels(this)
        Tick.arm(this)
    }
}
