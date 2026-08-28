package com.kma.quiz_game.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Turns a picked image into something worth uploading.
 *
 * A photo straight off the camera is several megabytes and thousands of pixels wide, and it is
 * displayed at 96dp. Every byte of that costs Drive quota on the server and battery on the way
 * up, so it is downscaled here rather than server-side -- which also keeps the backend free of
 * an image library.
 *
 * [ImageDecoder] is used over [android.graphics.BitmapFactory] because it applies the EXIF
 * orientation itself; without that, photos taken in portrait upload sideways.
 */
object AvatarImage {
    const val MIME_TYPE = "image/jpeg"

    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 85

    suspend fun readScaledJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longestSide = maxOf(info.size.width, info.size.height)
            if (longestSide > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            // compress() cannot read a HARDWARE bitmap, which is what the decoder returns by default.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
