package com.murattarslan.car.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

object ResourceUtils {

    /**
     * Drawable Resource ID'yi Android Auto'nun okuyabileceği bir Uri'ye dönüştürür.
     */
    fun getUriToResource(context: Context, resId: Int): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.resources.getResourcePackageName(resId))
            .appendPath(context.resources.getResourceTypeName(resId))
            .appendPath(context.resources.getResourceEntryName(resId))
            .build()
    }
}