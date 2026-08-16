package com.cycling.beecount.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrImageImportUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    sealed interface Outcome {
        data class Imported(val uri: Uri) : Outcome
        data class ReadError(val cause: Exception) : Outcome
    }

    suspend operator fun invoke(source: Uri): Outcome = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.cacheDir, OCR_CACHE_DIRECTORY).also { it.mkdirs() }
            val destination = File.createTempFile("ocr_", ".image", directory)
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: throw IOException("图片内容不可读取")
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
            Outcome.Imported(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destination,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.ReadError(e)
        }
    }

    private companion object {
        const val OCR_CACHE_DIRECTORY = "ocr"
    }
}
