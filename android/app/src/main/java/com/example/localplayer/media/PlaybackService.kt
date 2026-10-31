package com.example.localplayer.media

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.localplayer.core.AppLog
import com.example.localplayer.MainActivity

/**
 * Фоновое воспроизведение.
 *
 * Сервис живёт вне UI: Media3 сам показывает уведомление с обложкой и управлением,
 * обрабатывает наушники, звонки и экран блокировки. UI общается с ним только через
 * [PlayerController] и не знает про ExoPlayer.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Пауза, когда выдернули наушники или отключился Bluetooth.
            .setHandleAudioBecomingNoisy(true)
            // Не даём процессору уснуть во время воспроизведения с выключенным экраном.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    AppLog.w("Ошибка воспроизведения: ${error.errorCodeName}", error)
                }
            },
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Пользователь смахнул приложение из недавних. Если музыка не играет —
     * сервис больше не нужен; если играет, продолжаем (это обычное поведение плеера).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Тап по уведомлению открывает приложение. */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "Melodown"
    }
}
