package app.rosetta.tv.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object BitmapCache : LruCache<String, ImageBitmap>(24)

/** Decodes an image from app assets (or a local file path for live photos) off the main thread. */
suspend fun loadImage(context: Context, path: String): ImageBitmap? = BitmapCache.get(path) ?: withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = if (path.startsWith("/")) BitmapFactory.decodeFile(path)
        else context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        bitmap.asImageBitmap().also { BitmapCache.put(path, it) }
    }.getOrNull()
}

@Composable
fun rememberImage(path: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = path?.let { BitmapCache.get(it) }, path) {
        value = path?.let { loadImage(context, it) }
    }
    return image
}

/** Asset image that crossfades in once decoded, so TV screens never pop. */
@Composable
fun AssetImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val image = rememberImage(path)
    Crossfade(targetState = image, animationSpec = tween(450), label = "asset", modifier = modifier) { bmp ->
        if (bmp != null) Image(bmp, contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize())
    }
}

/**
 * A phone photo shown whole (portrait or landscape, never stretched or cropped), with [overlay] drawn inside the
 * photo's own rectangle so AI boxes in 0..1 photo coordinates land exactly on what they mark.
 */
@Composable
fun FittedPhoto(path: String, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val image = rememberImage(path)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            Box(Modifier.aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))) {
                Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                overlay()
            }
        }
    }
}
