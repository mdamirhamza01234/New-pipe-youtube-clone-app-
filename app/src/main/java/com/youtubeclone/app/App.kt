package com.youtubeclone.app

import android.app.Application
import com.youtubeclone.app.data.ShortsLimiter
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.download.DownloadCenter
import com.youtubeclone.app.player.PlayerController
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(
            DownloaderImpl.instance,
            Localization.fromLocale(Locale("en", "IN")),
            ContentCountry("IN"),
        )
        Store.init(this)
        DownloadCenter.init(this)
        ShortsLimiter.init(this)
        PlayerController.init(this)
    }
}
