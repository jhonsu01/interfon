package com.jhonsu.interfon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/** Contacto del agente: nombre y foto personalizables por el usuario. */
object Contact {

    fun photoFile(ctx: Context): File = File(ctx.filesDir, "agent_photo.jpg")

    /** Foto guardada (reducida a ~512px) o null si no hay. */
    fun load(ctx: Context): Bitmap? {
        val f = photoFile(ctx)
        if (!f.exists()) return null
        return runCatching {
            val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, probe)
            var sample = 1
            while ((probe.outWidth ?: 0) / sample > 512) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /**
     * Guarda la foto elegida (Uri de la galeria). Devuelve (ok, motivo de fallo).
     * Soporta HEIC/HEIF/WebP via ImageDecoder (API 28+) con fallback clasico.
     */
    fun save(ctx: Context, uri: Uri): Pair<Boolean, String?> {
        val bmp = decode(ctx, uri)
        if (bmp == null) {
            return false to "el dispositivo no pudo decodificar esa imagen (¿es HEIC desde un editor o la nube?)"
        }
        val ok = runCatching {
            photoFile(ctx).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }.isSuccess
        if (ok) Bus.agentPhoto.value = load(ctx)
        return ok to if (ok) null else "no se pudo escribir el archivo"
    }

    fun clear(ctx: Context) {
        photoFile(ctx).delete()
        Bus.agentPhoto.value = null
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? {
        // 1) ImageDecoder: HEIC/HEIF/WebP + allocation software (compress() lo exige)
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > 512) dec.setTargetSampleSize(maxDim / 512 + 1)
                }
            } catch (e: Exception) {
                // caer al decodificador clasico
            }
        }
        return decodeDownscaled(ctx, uri)
    }

    private fun decodeDownscaled(ctx: Context, uri: Uri): Bitmap? {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = ctx.contentResolver.openInputStream(uri) ?: return null
        opened.use { BitmapFactory.decodeStream(it, null, probe) }
        if ((probe.outWidth ?: 0) <= 0) return null
        var sample = 1
        while ((probe.outWidth ?: 0) / sample > 512 || (probe.outHeight ?: 0) / sample > 512) {
            sample *= 2
        }
        return ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
