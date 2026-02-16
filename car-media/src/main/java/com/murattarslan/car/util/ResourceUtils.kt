package com.murattarslan.car.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

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

    fun urlToBitmap(context: Context, imageUri: String, onResult: (Bitmap?) -> Unit){
        Glide.with(context).asBitmap().load(imageUri).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(
                resource: Bitmap,
                transition: Transition<in Bitmap>?
            ) {
                onResult(resource)
            }
            override fun onLoadCleared(placeholder: Drawable?) {
                onResult(null)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                onResult(null)
            }
        })
    }
}