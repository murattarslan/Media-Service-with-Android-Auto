package com.murattarslan.car.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale


/**
 * Bitmap'in çözünürlüğünü, orijinal en/boy oranını koruyarak düşürür.
 * @param this Düşürülecek orijinal bitmap.
 * @param maxDimension Görüntünün en uzun kenarının olabileceği maksimum piksel değeri.
 * @return Yeni, daha düşük çözünürlüklü bitmap.
 */
fun Bitmap.scaleBitmap(maxDimension: Int): Bitmap {
    val originalWidth = width
    val originalHeight = height
    var resizedWidth: Int
    var resizedHeight: Int

    if (originalWidth > originalHeight) {
        resizedWidth = maxDimension
        resizedHeight = (resizedWidth * (originalHeight.toFloat() / originalWidth.toFloat())).toInt()
    } else {
        resizedHeight = maxDimension
        resizedWidth = (resizedHeight * (originalWidth.toFloat() / originalHeight.toFloat())).toInt()
    }

    return scale(resizedWidth, resizedHeight)
}

/**
 * Bitmap'in renk doygunluğunu azaltarak daha soluk görünmesini sağlar.
 * @param this Rengi soldurulacak orijinal bitmap.
 * @param saturation Seviyesi. 0.0 tamamen gri tonlama, 1.0 orijinal renkler demektir.
 * @return Rengi soldurulmuş yeni bitmap.
 */
fun Bitmap.desaturateBitmap(saturation: Float): Bitmap {
    val newBitmap = createBitmap(width, height)
    val canvas = Canvas(newBitmap)
    val paint = Paint()
    val colorMatrix = ColorMatrix()
    colorMatrix.setSaturation(saturation)
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return newBitmap
}