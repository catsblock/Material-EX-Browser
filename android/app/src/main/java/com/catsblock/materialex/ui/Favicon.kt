package com.catsblock.materialex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.core.favicon.FaviconPolicy
import com.catsblock.materialex.core.favicon.FaviconStore

@Composable
fun Favicon(url: String, size: Dp, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val key = if (enabled) FaviconPolicy.hostKey(url) else null
    val image by produceState<ImageBitmap?>(
        initialValue = key?.let { FaviconStore.peek(url)?.asImageBitmap() },
        key1 = key
    ) {
        value = if (key == null) null else FaviconStore.get(url)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size * 0.7f)
            )
        } else {
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(size * 0.5f)) {
                val stroke = Stroke(width = this.size.minDimension * 0.08f)
                drawCircle(color, style = stroke)
                drawLine(color, Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), stroke.width)
                drawOval(
                    color,
                    topLeft = Offset(this.size.width * 0.3f, 0f),
                    size = Size(this.size.width * 0.4f, this.size.height),
                    style = stroke
                )
            }
        }
    }
}
