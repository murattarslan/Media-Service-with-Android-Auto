package com.murattarslan.car.data.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer

internal class AudioEngine(private val context: Context, private val player: ExoPlayer) {

    companion object {
        const val TAG = "AudioEngine"
    }

    // Sınıf içinde (alanlar)
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLossTransient: Boolean = false

    // Audio focus listener
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Fokus geri geldi: volume normale çek, gerekiyorsa resume et
                try {
                    player.volume = 1.0f
                    if (wasPlayingBeforeFocusLossTransient) {
                        player.play()
                        wasPlayingBeforeFocusLossTransient = false
                    }
                } catch (_: Throwable) { /* safe-guard */
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Geçici kayıp: pause et ama resume edebilmek için durumu sakla
                try {
                    wasPlayingBeforeFocusLossTransient = player.isPlaying
                    if (player.isPlaying) player.pause()
                } catch (_: Throwable) { /* safe-guard */
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck: düşük sesle devam et
                try {
                    player.volume = 0.15f
                } catch (_: Throwable) { /* safe-guard */
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Kalıcı kayıp: dur ve focus'u bırak
                try {
                    wasPlayingBeforeFocusLossTransient = false
                    if (player.isPlaying) player.pause()
                } catch (_: Throwable) { /* safe-guard */
                }
                abandonAudioFocus()
            }
        }
    }

    // audio focus isteme (return true => focus alındı)
    fun requestAudioFocus(): Boolean {
        audioManager = context.getSystemService(AudioManager::class.java)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(focusAttr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afChangeListener, Handler(Looper.getMainLooper()))
                .build()

            val res = audioManager.requestAudioFocus(audioFocusRequest!!)
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            // Legacy
            val res = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                audioManager.abandonAudioFocus(afChangeListener)
            }
        } catch (_: Throwable) { /* ignore */
        }
    }
}