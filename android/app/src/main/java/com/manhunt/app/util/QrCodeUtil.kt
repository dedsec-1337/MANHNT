package com.manhunt.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Local tag proof helpers — encode catch payload as QR for face-to-face verification.
 * Format: MANHNT|<joinCode>|<playerId>|<catchCode>
 */
object QrCodeUtil {
    fun catchPayload(joinCode: String, playerId: String, catchCode: String): String =
        "MANHNT|${joinCode.trim().uppercase()}|$playerId|${catchCode.trim()}"

    fun encodeToBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
