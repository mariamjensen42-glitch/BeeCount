package com.cycling.beecount.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 用例：把原始图片 Uri 复制到应用缓存，产出与 Android `Uri` 解耦的 [OcrImageSource]。
 *
 * 只有这一步接触 Android 的 `ContentResolver`/`Context`。复制后的缓存文件路径作为
 * [OcrImageSource.cacheFilePath]，后续交给 [EntryIntake.parseOcr]（内部通过
 * [OcrTextRecognizer] seam 识别）。
 */
class OcrImageImportUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    sealed interface Outcome {
        data class Imported(val source: OcrImageSource) : Outcome
        data class ReadError(val cause: Exception) : Outcome
    }

    suspend operator fun invoke(source: Uri): Outcome = withContext(Dispatchers.IO) {
        Timber.d("OCR 图片导入开始，source=%s", source)
        try {
            val directory = File(context.cacheDir, OCR_CACHE_DIRECTORY).also { it.mkdirs() }
            val destination = File.createTempFile("ocr_", ".image", directory)
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: throw IOException("图片内容不可读取")
            } catch (e: Exception) {
                Timber.e(e, "OCR 图片复制到缓存失败")
                destination.delete()
                throw e
            }
            Timber.d("OCR 图片已复制到缓存：%s", destination.absolutePath)
            Outcome.Imported(OcrImageSource(cacheFilePath = destination.absolutePath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "OCR 图片导入失败：%s", e.message)
            Outcome.ReadError(e)
        }
    }

    private companion object {
        const val OCR_CACHE_DIRECTORY = "ocr"
    }
}
