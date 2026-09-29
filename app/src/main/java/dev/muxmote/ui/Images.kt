package dev.muxmote.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Claude scales images down to about this long edge anyway, so larger ones only cost upload time. */
const val MAX_EDGE = 1568

/** [width]x[height] shrunk to fit [MAX_EDGE], keeping the aspect ratio. */
fun fitted(width: Int, height: Int): Pair<Int, Int> {
  val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(width, height))
  return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/** The image at [uri], fitted to [MAX_EDGE] and encoded as PNG, which keeps text sharp. */
fun ContentResolver.png(uri: Uri): ByteArray {
  val bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(this, uri)) { decoder, info, _ ->
      val (width, height) = fitted(info.size.width, info.size.height)
      decoder.setTargetSize(width, height)
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
  return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
