package com.murattarslan.car.data.source

import com.murattarslan.car.domain.interfaces.DataSource
import com.murattarslan.car.domain.models.DataState
import com.murattarslan.car.domain.models.MediaItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultDataSource: DataSource {

    private val _mediaState: MutableStateFlow<DataState> = MutableStateFlow(DataState())
    override val mediaState: StateFlow<DataState> = _mediaState.asStateFlow()

    override fun fetchMediaItems() {
        _mediaState.update { state ->
            state.copy(data = mockMediaData2, isLoading = true)
        }
        _mediaState.update { state ->
            state.copy(data = mockMediaData2, isLoading = false)
        }
    }

    override fun favorite(mediaId: String, isFavorite: Boolean) {
        _mediaState.update { state ->
            state.copy(data = mockMediaData2, isLoading = true)
        }
        _mediaState.update { state ->
            state.copy(
                data = state.data.map {
                    if (it.id == mediaId) it.copy(isFavorite = isFavorite) else it
                },
                isLoading = false
            )
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