package com.murattarslan.myautoapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.murattarslan.car.core.MediaService
import com.murattarslan.car.domain.listeners.OnMediaStateListener
import com.murattarslan.car.domain.models.MediaItemModel
import com.murattarslan.car.util.ResourceUtils
import com.murattarslan.car.util.StringFormatter
import com.murattarslan.car.util.desaturateBitmap
import com.murattarslan.car.util.scaleBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnMediaStateListener {

    private lateinit var mediaService: MediaService
    private var currentTrack: MediaItemModel? = null

    // UI Bileşenleri
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var currentTime: TextView
    private lateinit var durationTime: TextView
    private lateinit var imgArt: ImageView
    private lateinit var bg: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStart: ImageButton
    private lateinit var btnFav: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var seekBar: SeekBar

    override fun onStart() {
        super.onStart()
        mediaService.addMediaStateListener(this)
    }

    override fun onStop() {
        mediaService.removeMediaStateListener(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // UI Tanımlamaları
        txtTitle = findViewById(R.id.songTitle)
        txtArtist = findViewById(R.id.artistName)
        currentTime = findViewById(R.id.currentTime)
        durationTime = findViewById(R.id.durationTime)
        imgArt = findViewById(R.id.albumArt)
        bg = findViewById(R.id.bg)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnStart = findViewById(R.id.start)
        btnFav = findViewById(R.id.fav)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        seekBar = findViewById(R.id.duration)

        mediaService = MediaService.getInstance()
        if (mediaService.isPlaying().not())
            mediaService.startService(this)
        btnPlayPause.setOnClickListener {
            if (!mediaService.isPlaying())
                mediaService.onPlay()
            else
                mediaService.onPause()
        }
        btnStart.setOnClickListener {
            mediaService.onChange("track_music_2")
        }
        btnNext.setOnClickListener { mediaService.onNext() }
        btnPrev.setOnClickListener { mediaService.onPrev() }
        btnFav.setOnClickListener {
            currentTrack?.let {
                mediaService.onFav(it)
            }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            var seeking = false
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {}

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seeking = false
                mediaService.onSeek(seekBar!!.progress.toLong())
            }

        })
    }

    // --- UI GÜNCELLEME METODLARI ---

    private fun updateMetadata(track: MediaItemModel) {
        currentTrack = track
        txtTitle.text = track.title
        txtArtist.text = track.artist

        if (track.duration.toInt() == -1) {
            seekBar.max = 1
            durationTime.text = "--:--"
        } else {
            seekBar.max = track.duration.toInt()
            durationTime.text = StringFormatter.formatTime(track.duration)
        }

        onFavoriteTrack(track)
        if (track.imageUri.isEmpty().not()) {
            ResourceUtils.urlToBitmap(this, track.imageUri) {
                imgArt.setImageBitmap(it)
                val scaledBitmap = it?.scaleBitmap(200)
                val desaturatedBitmap = scaledBitmap?.desaturateBitmap(0.5f)
                bg.setImageBitmap(desaturatedBitmap)
            }
        } else {
            imgArt.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun onPlayTrack(trackId: String?, position: Long, lastPositionUpdateTime: Long, playbackSpeed: Float) {
        runnable = createRunnable(position, lastPositionUpdateTime, playbackSpeed)
        runnable?.start()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
    }

    override fun onPauseTrack(trackId: String?) {
        runnable?.cancel()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    override fun onChangeTrack(track: MediaItemModel) {
        runnable?.cancel()
        updateMetadata(track)
    }

    override fun onFavoriteTrack(track: MediaItemModel) {
        if (track == currentTrack) {
            btnFav.tag = track.isFavorite
            btnFav.setImageResource(
                if (track.isFavorite)
                    androidx.media3.session.R.drawable.media3_icon_heart_filled
                else
                    androidx.media3.session.R.drawable.media3_icon_heart_unfilled
            )
        }
    }

    var runnable: Job? = null
    private fun createRunnable(position: Long, lastPositionUpdateTime: Long, playbackSpeed: Float): Job {
        return lifecycleScope.launch {
            while (isActive){
                val timeDiff = SystemClock.elapsedRealtime() - lastPositionUpdateTime
                val progress = position + (timeDiff * playbackSpeed).toLong()
                if (progress >= seekBar.max) {
                    break
                }
                seekBar.progress = progress.toInt()
                currentTime.text = StringFormatter.formatTime(progress)
                delay(1000)
            }
        }
    }

}