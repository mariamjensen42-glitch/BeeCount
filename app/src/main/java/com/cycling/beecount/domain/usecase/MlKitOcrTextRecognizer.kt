package com.cycling.beecount.domain.usecase

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

/**
 * ML Kit 文字识别 adapter：把 [OcrImageSource] 转成 `InputImage` 并调用中文识别器。
 *
 * 该实现是 Android/ML Kit 依赖的，只在这里接触 `Uri`/`InputImage`；domain 层的
 * [EntryIntake] 仅依赖 [OcrTextRecognizer] 接口（其参数是中立的 [OcrImageSource]）。
 */
class MlKitOcrTextRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OcrTextRecognizer {
    override suspend fun recognize(source: OcrImageSource): String {
        Timber.d("ML Kit 解析 OCR 图像源：%s", source.cacheFilePath)
        val uri = Uri.fromFile(File(source.cacheFilePath))
        val image = InputImage.fromFilePath(context, uri)
        return recognizeText(image)
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    cont.resumeWithException(e)
                }
            cont.invokeOnCancellation { recognizer.close() }
        }
}
