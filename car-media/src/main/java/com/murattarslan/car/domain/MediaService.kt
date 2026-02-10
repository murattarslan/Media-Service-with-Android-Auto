package com.murattarslan.car.domain

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.murattarslan.car.service.MediaPlayerService
import com.murattarslan.car.service.data.MediaItemModel

/**
 * Medya servisi sınıfı
 *
 * Bildirimde, ve medya servisleri yardımıyla WearOS ve Android Auto ekranlarında da yönetilebilen bir medya player servisi oluşturmak için kullanılır
 * @author murattarslan.dev
 * */
class MediaService private constructor(): OnMediaController, OnQueueInformation {

    companion object {
        fun builder(context: Context): Builder {
            return Builder(context)
        }

        internal var instance: MediaService? = null
        internal var isDebugEnable = true

        fun getInstance(): MediaService {
            return instance ?: throw Exception("MediaService not initialized")
        }

        internal var mediaList: ArrayList<MediaItemModel> = arrayListOf()
        internal var favMediaList: List<MediaItemModel> = listOf()

        @StringRes internal var allMediaTitle: Int? = null
        @StringRes internal var favoriteTitle: Int? = null
        @DrawableRes internal var allMediaIcon: Int? = null
        @DrawableRes internal var favoriteIcon: Int? = null

        internal var mediaController: OnMediaController? = null
        internal var queueInformation: OnQueueInformation? = null

        const val SESSION_ACTIVITY_EXTRA_KEY = "SESSION_ACTIVITY_EXTRA_KEY"
        const val SESSION_ACTIVITY_EXTRA_VALUE = -1
    }

    internal var service: Intent? = null
    internal var sessionActivity: Intent? = null

    internal var favClickListener: FavClickListener? = null
    internal var mediaStateListener: MutableList<OnMediaStateListener>? = null

    private fun setMediaStateListener(mediaStateListener: OnMediaStateListener) {
        this.mediaStateListener = mutableListOf(mediaStateListener)
    }

    fun addMediaStateListener(mediaStateListener: OnMediaStateListener) {
        if (this.mediaStateListener == null) {
            this.mediaStateListener = mutableListOf()
        }
        this.mediaStateListener?.add(mediaStateListener)
    }

    fun removeMediaStateListener(mediaStateListener: OnMediaStateListener) {
        this.mediaStateListener?.remove(mediaStateListener)
    }

    private fun setFavClickListener(favClickListener: FavClickListener) {
        this.favClickListener = favClickListener
    }

    /**
     * Kullanıcı Arayüzü etkileşimlerinin kütüphaneye iletişmesi için kullanılan fonksiyonlar
     * */


    /**
     * App tarafından beğenilen şarkının servise yansıtılması için kullanılır
     * @param track [MediaItemModel] tipinde bir nesne
     * @author murattarslan.dev
     * */
    fun onFav(track: MediaItemModel) {
        mediaController?.onFavorite(track)
    }

    /**
     * App tarafından şarkının çalınmasına karar verebilmesi için kullanılır
     * @author murattarslan.dev
     */
    override fun isPlaying(): Boolean {
        return mediaController?.isPlaying() ?: false
    }

    /**
     * App tarafından şarkı başlatma için kullanılır
     * @author murattarslan.dev
     */
    override fun onPlay() {
        mediaController?.onPlay()
    }

    /**
     * App tarafından şarkı durdurma için kullanılır
     * @author murattarslan.dev
     */
    override fun onPause() {
        mediaController?.onPause()
    }

    /**
     * App tarafından bir sonraki şarkıya geçme için kullanılır
     * @author murattarslan.dev
     */
    override fun onNext() {
        mediaController?.onNext()
    }

    /**
     * App tarafından bir önceki şarkıya geçme için kullanılır
     * @author murattarslan.dev
     */
    override fun onPrev() {
        mediaController?.onPrev()
    }

    /**
     * App tarafından şarkı pozisyonu değiştirilme için kullanılır
     * @param position Long tipinde bir değer
     * @author murattarslan.dev
     */
    override fun onSeek(position: Long) {
        mediaController?.onSeek(position)
    }

    /**
     * App tarafından farklı bir şarkıya geçme için kullanılır
     * @param track [MediaItemModel] tipinde bir nesne
     * @author murattarslan.dev
     */
    override fun onChange(track: MediaItemModel){
        mediaController?.onChange(track)
    }

    /**
     * App tarafından çalan şarkının bilgileri alınması için kullanılır
     * @return [MediaItemModel] tipinde bir nesne
     * @author murattarslan.dev
     * */
    override fun currentItem(): MediaItemModel? {
        return mediaController?.currentItem()
    }


    /**
     * App servise medya yüklemek için kullanılır.
     *
     * [MediaItemModel] tipinde bir albüm listesi yüklenmesi gerekiyor.
     *
     * @param medias [MediaItemModel] tipinde bir liste
     * @sample [com.murattarslan.car.simple.load]
     * @author murattarslan.dev
     * */
    fun loadMediaList(medias: List<MediaItemModel>) {
        mediaList.clear()
        mediaList.addAll(medias)
        favMediaList = mediaList.filter { it.isFavorite }
    }

    override fun getMediaItemModel(mediaId: String): MediaItemModel? {
        return queueInformation?.getMediaItemModel(mediaId)
    }

    override fun getCurrentTrack(): MediaItemModel? {
        return queueInformation?.getCurrentTrack()
    }

    override fun getMediaList(albumId: String): List<MediaItemModel> {
        return queueInformation?.getMediaList(albumId) ?: listOf()
    }

    override fun getAlbumList(): List<MediaItemModel> {
        return queueInformation?.getAlbumList() ?: listOf()
    }

    override fun getFavMediaList(): List<MediaItemModel> {
        return queueInformation?.getFavMediaList() ?: listOf()
    }

    /**
     * [com.murattarslan.car.domain.MediaService] sınıfını oluşturmak için kullanılır.
     *
     * @author murattarslan.dev
     *
     * [setMediaStateListener] fonksiyonu ile aktif session içindeki değişimlerinden haberdar olarak gerektiğinde ui güncellenebilecek callback fonksiyonu atanabilir.
     *
     * [setFavClickListener] fonksiyonu ile medya servisi tarafından beğenilen medyayı api ile haberleştirebileceğiniz bir callback fonksiyona ulaşabilirsiniz.
     *
     * @sample [com.murattarslan.car.simple.createMediaService]
     *
     * @throws build çağrılmadan instance alınmaz.
     * */
    class Builder(val context: Context) {
        private var favClickListener: FavClickListener? = null
        private var mediaStateListener: OnMediaStateListener? = null
        private var sessionActivity: Class<*>? = null


        fun setMediaStateListener(mediaStateListener: OnMediaStateListener): Builder {
            this.mediaStateListener = mediaStateListener
            return this
        }

        fun setFavClickListener(favClickListener: FavClickListener): Builder {
            this.favClickListener = favClickListener
            return this
        }

        private var allMediaTitle: Int? = null
        private var favoriteTitle: Int? = null
        private var allMediaIcon: Int? = null
        private var favoriteIcon: Int? = null

        fun setAllMediaTitle(@StringRes title: Int): Builder {
            allMediaTitle = title
            return this
        }

        fun setFavoriteTitle(@StringRes title: Int): Builder {
            favoriteTitle = title
            return this
        }

        fun setAllMediaIcon(@DrawableRes icon: Int): Builder {
            allMediaIcon = icon
            return this
        }

        fun setFavoriteIcon(@DrawableRes icon: Int): Builder {
            favoriteIcon = icon
            return this
        }

        fun setSessionActivity(sessionActivity: Class<*>): Builder {
            this.sessionActivity = sessionActivity
            return this
        }

        fun build(): MediaService {
            val mediaService = MediaService()

            mediaStateListener?.let { mediaService.setMediaStateListener(it) }
            favClickListener?.let { mediaService.setFavClickListener(it) }

            allMediaTitle?.let { MediaService.allMediaTitle = it }
            favoriteTitle?.let { MediaService.favoriteTitle = it }
            allMediaIcon?.let { MediaService.allMediaIcon = it }
            favoriteIcon?.let { MediaService.favoriteIcon = it }

            mediaService.service = Intent(context, MediaPlayerService::class.java).let {
                context.startService(it)
                it
            }

            sessionActivity?.let {
                mediaService.sessionActivity = Intent(context, it).apply {
                    putExtra(SESSION_ACTIVITY_EXTRA_KEY, SESSION_ACTIVITY_EXTRA_VALUE)
                }
            }

            if (instance != null){
                context.stopService(instance!!.service)
            }
            instance = mediaService
            return mediaService
        }
    }
}