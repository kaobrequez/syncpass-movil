package cl.kcorp.syncpass.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return createBitmap(size, size).also {
        it.setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val bitmap = remember(content) { generateQrBitmap(content, 512) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Código QR",
        modifier = modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp),
    )
}
