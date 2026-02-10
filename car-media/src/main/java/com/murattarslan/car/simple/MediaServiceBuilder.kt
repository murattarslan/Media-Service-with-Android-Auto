package com.murattarslan.car.simple

import android.content.Context
import com.murattarslan.car.R
import com.murattarslan.car.domain.FavClickListener
import com.murattarslan.car.domain.MediaService
import com.murattarslan.car.domain.OnMediaStateListener
import com.murattarslan.car.service.data.MediaItemModel

private fun createMediaService(context: Context) {

    val service = MediaService.builder(context)
        .setAllMediaTitle(R.string.all_media)
        .setAllMediaIcon(R.drawable.ic_audio)
        .setFavoriteTitle(R.string.favorite)
        .setFavoriteIcon(R.drawable.ic_recent)
        .setFavClickListener(object : FavClickListener {
            override fun onFavClick(position: Int) {
                // Fav Click Listener
            }
        })
        .setMediaStateListener(object : OnMediaStateListener {
            override fun onPauseTrack() {
                super.onPauseTrack()
            }

            override fun onPlayTrack() {
                super.onPlayTrack()
            }

            override fun onChangeTrack(track: MediaItemModel) {
                super.onChangeTrack(track)
            }

            override fun onSeek(position: Long) {
                super.onSeek(position)
            }
        })
        .build()

    // service içindeki fonksiyonlara erişilebilir
    service.loadMediaList(emptyList())

    // veya bu şekilde de kullanılabilir
    MediaService.getInstance().loadMediaList(emptyList())
}