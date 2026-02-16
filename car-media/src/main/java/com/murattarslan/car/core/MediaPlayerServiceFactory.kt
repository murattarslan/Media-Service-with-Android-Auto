package com.murattarslan.car.core

import android.content.Context
import com.murattarslan.car.data.player.DefaultPlayerController
import com.murattarslan.car.data.queue.DefaultQueueManager
import com.murattarslan.car.data.session.DefaultSessionManager
import com.murattarslan.car.data.source.DefaultDataSource
import com.murattarslan.car.domain.interfaces.SessionManager
import kotlinx.coroutines.CoroutineScope

internal object MediaPlayerServiceFactory {

    fun createSessionManager(
        context: Context,
        scope: CoroutineScope
    ): SessionManager {

        // 1. Önce Veri Kaynağını (DataSource) al/oluştur
        // (İlerde dışarıdan enjekte edeceğiz)
        val dataSource = MediaService.instance?.customDataSource ?: DefaultDataSource()

        // 2. QueueManager'ı oluştur ve DataSource'u içine ver
        val queueManager = MediaService.instance?.customQueueManager ?: DefaultQueueManager(dataSource, scope)

        // 3. PlayerController'ı oluştur ve QueueManager'ı bağla
        val playerController = MediaService.instance?.customPlayerController ?: DefaultPlayerController(
            context,
            queueManager,
            scope
        )

        // 4. En son SessionManager'ı oluştur ve Player'ı içine enjekte et
        return MediaService.instance?.customSessionManager ?: DefaultSessionManager(context, playerController, scope)
    }
}