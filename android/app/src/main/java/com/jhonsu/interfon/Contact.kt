package com.jhonsu.interfon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    /** Copia la foto elegida (Uri del galeria) al almacenamiento interno. */
    fun save(ctx: Context, uri: Uri): Boolean {
        val bmp = decodeDownscaled(ctx, uri) ?: return false
        val ok = runCatching {
            photoFile(ctx).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }.isSuccess
        if (ok) Bus.agentPhoto.value = load(ctx)
        return ok
    }

    fun clear(ctx: Context) {
        photoFile(ctx).delete()
        Bus.agentPhoto.value = null
    }

    private fun decodeDownscaled(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, probe)
        } ?: return null
        var sample = 1
        while ((probe.outWidth ?: 0) / sample > 512 || (probe.outHeight ?: 0) / sample > 512) {
            sample *= 2
        }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
