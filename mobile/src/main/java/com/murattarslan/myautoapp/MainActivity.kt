package com.murattarslan.myautoapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.murattarslan.car.domain.MediaService
import com.murattarslan.car.domain.OnMediaStateListener
import com.murattarslan.car.service.data.MediaItemModel
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var mediaService: MediaService

    // UI Bileşenleri
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var currentTime: TextView
    private lateinit var durationTime: TextView
    private lateinit var imgArt: ImageView
    private lateinit var bg: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStart: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var seekBar: SeekBar

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
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        seekBar = findViewById(R.id.duration)

        mediaService = MediaService.builder(this)
            .setMediaStateListener(object : OnMediaStateListener{
                override fun onChangeTrack(track: MediaItemModel) {
                    updateMetadata(track)
                }
                override fun onPlayTrack() {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }

                override fun onPauseTrack() {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }

                override fun onSeek(position: Long) {
                    seekBar.visibility = if (position.toInt() == -1) SeekBar.INVISIBLE else SeekBar.VISIBLE
                    currentTime.visibility = if (position.toInt() == -1) SeekBar.INVISIBLE else SeekBar.VISIBLE
                    durationTime.visibility = if (position.toInt() == -1) SeekBar.INVISIBLE else SeekBar.VISIBLE
                    if (seekBar.max == 1) {
                        currentTime.text = "--:--"
                        seekBar.progress = 1
                    }
                    else {
                        seekBar.progress = position.toInt()
                        currentTime.text = formatTime(position)
                    }
                }
            })
            .build()
        MediaService.getInstance().loadMediaList(mockMediaData2)
        btnPlayPause.setOnClickListener {
            if (!mediaService.isPlaying())
                mediaService.onPlay()
            else
                mediaService.onPause()
        }
        btnStart.setOnClickListener {
            mediaService.onChange(mockMediaData2.last())
        }
        btnNext.setOnClickListener { mediaService.onNext() }
        btnPrev.setOnClickListener { mediaService.onPrev() }
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
        txtTitle.text = track.title
        txtArtist.text = track.artist

        if (track.duration.toInt() == -1) {
            seekBar.max = 1
            durationTime.text = "--:--"
        } else {
            seekBar.max = track.duration.toInt()
            durationTime.text = formatTime(track.duration)
        }
        seekBar.progress = 0
        currentTime.text = "--:--"

        if (track.imageUri.isEmpty().not()) {
            Glide.with(imgArt).asBitmap().load(track.imageUri).into(object : CustomTarget<Bitmap>() {
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

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds < 0) return "--:--"

        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    val mockMediaData2 = arrayListOf(
        MediaItemModel(
            id = "cat_live",
            parentId = null,
            title = "Canlı Yayınlar",
            artist = "",
            description = "",
            imageUri = "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=500",
            mediaUri = "",
            isFavorite = false
        ),
        MediaItemModel(
            id = "cat_podcast",
            parentId = null,
            title = "Podcast Dünyası",
            artist = "",
            description = "",
            imageUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500",
            mediaUri = "",
            isFavorite = false
        ),
        MediaItemModel(
            id = "cat_music",
            parentId = null,
            title = "Haftalık Mix",
            artist = "",
            description = "",
            imageUri = "https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=500",
            mediaUri = "",
            isFavorite = false
        ),
        MediaItemModel(
            id = "track_music_1",
            parentId = "cat_music",
            title = "Midnight City",
            artist = "Synthwave Artist",
            description = "Gece sürüşü için ideal",
            imageUri = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            isFavorite = true
        ),
        MediaItemModel(
            id = "track_music_2",
            parentId = "cat_music",
            title = "Acoustic Sun",
            artist = "Guitar Master",
            description = "Yumuşak akustik tınılar",
            imageUri = "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            isFavorite = false
        ),
        MediaItemModel(
            id = "track_music_3",
            parentId = "cat_music",
            title = "Deep Bass Drive",
            artist = "Electro Crew",
            description = "Bas ağırlıklı modern ritimler",
            imageUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
            isFavorite = true
        ),
        MediaItemModel(
            id = "track_tech_1",
            parentId = "cat_podcast",
            title = "AI ve İnsanlık",
            artist = "Teknoloji Podcast",
            description = "Yapay zekanın geleceği",
            imageUri = "https://images.unsplash.com/photo-1507146153580-69a1fe6d8aa1?w=500",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            isFavorite = true
        ),
        MediaItemModel(
            id = "track_tech_2",
            parentId = "cat_podcast",
            title = "Blockchain 101",
            artist = "Teknoloji Podcast",
            description = "Web3 dünyasına giriş",
            imageUri = "https://images.unsplash.com/photo-1639762681485-074b7f938ba0?w=500",
            mediaUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            isFavorite = false
        ),
        MediaItemModel(
            id = "live_1",
            parentId = "cat_live",
            title = "BBC World Service",
            artist = "News",
            description = "Global news and reports",
            imageUri = "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=500",
            mediaUri = "http://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            isFavorite = true
        ),
        MediaItemModel(
            id = "live_2",
            parentId = "cat_live",
            title = "Jazz Radio - Smooth",
            artist = "Jazz",
            description = "Smooth and relaxing jazz",
            imageUri = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=500",
            mediaUri = "https://jazzradio.ice.infomaniak.ch/jazzradio-high.mp3",
            isFavorite = false
        ),
        MediaItemModel(
            id = "live_tr_1",
            parentId = "cat_live",
            title = "TRT Nağme",
            artist = "Türkçe Pop",
            description = "En yeni Türkçe pop hitleri",
            imageUri = "https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=500",
            mediaUri = "https://rd-trtnagme.medya.trt.com.tr/master_128.m3u8",
            isFavorite = true
        ),
        MediaItemModel(
            id = "live_tr_2",
            parentId = "cat_live",
            title = "TRT FM",
            artist = "Global Hits",
            description = "Dünya listelerinden popüler müzik",
            imageUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500",
            mediaUri = "https://rd-trtfm.medya.trt.com.tr/master_128.m3u8",
            isFavorite = false
        ),
        MediaItemModel(
            id = "live_tr_arabesk",
            parentId = "cat_live",
            title = "TRT Türkü",
            artist = "Arabesk",
            description = "İlaç gibi radyo",
            imageUri = "https://trt-public-static.trt.com.tr/eradyo/public/dm_upload/modul2/87df7111-6fb7-4e4f-9f02-87ac21c4a40b.png",
            mediaUri = "https://rd-trtturku.medya.trt.com.tr/master_128.m3u8",
            isFavorite = false
        ),
        MediaItemModel(
            id = "live_classic",
            parentId = "cat_live",
            title = "Kral FM",
            artist = "Classical",
            description = "The world's greatest music",
            imageUri = "https://www.denizhaber.net/d/news/108379.jpg",
            mediaUri = "https://dygedge2.radyotvonline.net/kralfm/playlist.m3u8",
            isFavorite = false
        ),
        MediaItemModel(
            id = "live_chanson",
            parentId = "cat_live",
            title = "Chante France",
            artist = "Chanson",
            description = "Les plus belles chansons françaises",
            imageUri = "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=500",
            mediaUri = "https://chantefrance.ice.infomaniak.ch/chantefrance-128.mp3",
            isFavorite = true
        ),
        MediaItemModel(
            id = "live_country",
            parentId = "cat_live",
            title = "Country Music Radio",
            artist = "Country",
            description = "Best of Nashville",
            imageUri = "https://images.unsplash.com/photo-1516737488405-7b6d6868fad3?w=500",
            mediaUri = "https://streaming.radionomy.com/A-Country-Radio",
            isFavorite = false
        )
    )
}