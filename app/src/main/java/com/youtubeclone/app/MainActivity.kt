package com.youtubeclone.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.player.PlayerController
import com.youtubeclone.app.ui.AppRoot
import com.youtubeclone.app.ui.UiFlags
import com.youtubeclone.app.ui.YtTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { YtTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** youtube.com / youtu.be link par tap karne se video seedha is app mein khulta hai. */
    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) {
            i.data?.toString()?.let { PlayerController.open(it) }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 &&
            Store.pip &&
            PlayerController.expanded &&
            PlayerController.exo.isPlaying
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        UiFlags.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        val inPip = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
        if (!Store.backgroundPlay && !inPip) PlayerController.exo.pause()
    }
}
