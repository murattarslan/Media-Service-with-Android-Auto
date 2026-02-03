package com.murattarslan.myautoapp

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.murattarslan.myautoapp.shared.MyMusicService
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat

    // UI Bileşenleri
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var imgArt: ImageView
    private lateinit var bg: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Tanımlamaları
        txtTitle = findViewById(R.id.songTitle)
        txtArtist = findViewById(R.id.artistName)
        imgArt = findViewById(R.id.albumArt)
        bg = findViewById(R.id.bg)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MyMusicService::class.java),
            connectionCallbacks,
            null
        )
    }

    override fun onStart() {
        super.onStart()
        // Activity açıldığında servise bağlan
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        // Activity kapandığında bağlantıyı kes
        // (Not: Controller callback'lerini de burada çıkarabiliriz ama MediaBrowser.disconnect genelde yeterlidir)
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    /**
     * Servise bağlantı durumunu dinleyen callback
     */
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // Bağlantı başarılı olduğunda buraya düşeriz.

            // 2. MediaSession Token'ı Al
            val sessionToken = mediaBrowser.sessionToken

            // 3. MediaController Oluştur
            // Bu controller sayesinde butona basınca "Oynat", "Dur" emirlerini servise ileteceğiz
            val mediaController = MediaControllerCompat(this@MainActivity, sessionToken)
            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

            // 4. UI'ı Başlangıç Durumuna Getir
            buildTransportControls()
        }

        override fun onConnectionSuspended() {
            // Bağlantı koptuğunda (örn: servis çökerse) butonları devre dışı bırakabiliriz
            btnPlayPause.isEnabled = false
        }

        override fun onConnectionFailed() {
            // Bağlantı hiç kurulamazsa
        }
    }

    /**
     * Butonları işlevsel hale getiren ve durumu dinleyen fonksiyon
     */
    private fun buildTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this)

        // Mevcut durumu al (Şu an çalıyor mu? Hangi şarkı?)
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        // UI'ı mevcut duruma göre güncelle
        if (metadata != null) {
            updateMetadata(metadata)
        }
        if (pbState != null) {
            updateButtonState(pbState)
        }

        // Buton Dinleyicileri (Servise komut gönderme)
        btnPlayPause.setOnClickListener {
            val state = mediaController.playbackState.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController.transportControls.pause()
            } else {
                mediaController.transportControls.play()
            }
        }

        btnNext.setOnClickListener {
            mediaController.transportControls.skipToNext()
        }

        btnPrev.setOnClickListener {
            mediaController.transportControls.skipToPrevious()
        }

        // Servisten gelen değişiklikleri dinlemeye başla
        mediaController.registerCallback(controllerCallback)
    }

    /**
     * Şarkı değiştiğinde veya Durdur/Başlat yapıldığında servisin bize haber verdiği yer
     */
    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state?.let { updateButtonState(it) }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata?.let { updateMetadata(it) }
        }
    }

    // --- UI GÜNCELLEME METODLARI ---

    private fun updateButtonState(state: PlaybackStateCompat) {
        if (state.state == PlaybackStateCompat.STATE_PLAYING) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun updateMetadata(metadata: MediaMetadataCompat) {
        txtTitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Bilinmeyen Şarkı"
        txtArtist.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Bilinmeyen Sanatçı"

        // Albüm kapağı servisten Bitmap olarak geliyorsa:
        val art = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
        if (art.isNullOrEmpty().not()) {
            Glide.with(imgArt).asBitmap().load(art).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    imgArt.setImageBitmap(resource)
                    val scaledBitmap = scaleBitmap(resource, 200)
                    val desaturatedBitmap = desaturateBitmap(scaledBitmap, 0.5f)
                    bg.setImageBitmap(desaturatedBitmap)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
        } else {
            // Demo: Gerçek bir resim yükleme kütüphanesi (Glide/Picasso) yerine placeholder
            imgArt.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    /**
     * Bitmap'in çözünürlüğünü, orijinal en/boy oranını koruyarak düşürür.
     * @param bitmap Düşürülecek orijinal bitmap.
     * @param maxDimension Görüntünün en uzun kenarının olabileceği maksimum piksel değeri.
     * @return Yeni, daha düşük çözünürlüklü bitmap.
     */
    fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth: Int
        var resizedHeight: Int

        if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = (resizedWidth * (originalHeight.toFloat() / originalWidth.toFloat())).toInt()
        } else {
            resizedHeight = maxDimension
            resizedWidth = (resizedHeight * (originalWidth.toFloat() / originalHeight.toFloat())).toInt()
        }

        return bitmap.scale(resizedWidth, resizedHeight)
    }

    /**
     * Bitmap'in renk doygunluğunu azaltarak daha soluk görünmesini sağlar.
     * @param bitmap Rengi soldurulacak orijinal bitmap.
     * @param saturation Seviyesi. 0.0 tamamen gri tonlama, 1.0 orijinal renkler demektir.
     * @return Rengi soldurulmuş yeni bitmap.
     */
    fun desaturateBitmap(bitmap: Bitmap, saturation: Float): Bitmap {
        val newBitmap = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(newBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(saturation)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return newBitmap
    }

}