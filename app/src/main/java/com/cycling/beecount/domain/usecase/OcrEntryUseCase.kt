package com.cycling.beecount.domain.usecase

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val OCR_MIN_TEXT_LENGTH = 15

/**
 * 用例：从图片 Uri 提取文字，再交由 ParseEntryUseCase 解析为账目草稿。
 *
 * 流程：Uri → ML Kit InputImage → 文字识别 → 阈值判断 → ParseEntryUseCase(isOcrInput=true)。
 * 识别结果去除空白后不足 15 字符视为识别失败，直接返回 [Outcome.RecognitionFailed]。
 */
class OcrEntryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parseEntryUseCase: ParseEntryUseCase,
) {

    sealed interface Outcome {
        /** OCR + 解析均成功，[rawText] 为识别原文供确认卡片展示 */
        data class Parsed(
            val parseOutcome: ParseEntryUseCase.Outcome,
            val rawText: String,
        ) : Outcome

        /** ML Kit 未能从图片中识别出足够文字 */
        data object RecognitionFailed : Outcome

        /** ML Kit 本身抛出异常（图片格式不支持等） */
        data class ImageError(val message: String) : Outcome
    }

    suspend operator fun invoke(uri: Uri): Outcome {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            return Outcome.ImageError(e.message ?: "无法读取图片")
        }

        val rawText = try {
            recognizeText(image)
        } catch (e: Exception) {
            return Outcome.ImageError(e.message ?: "文字识别出错")
        }

        if (rawText.filter { !it.isWhitespace() }.length < OCR_MIN_TEXT_LENGTH) {
            return Outcome.RecognitionFailed
        }

        return Outcome.Parsed(parseEntryUseCase(rawText, isOcrInput = true), rawText)
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
