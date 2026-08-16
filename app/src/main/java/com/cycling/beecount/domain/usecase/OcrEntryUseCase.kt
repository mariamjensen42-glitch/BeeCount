package com.cycling.beecount.domain.usecase

import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val OCR_MIN_TEXT_LENGTH = 15

/**
 * 用例：从图片 Uri 提取文字，再交由 ParseEntryUseCase 解析为账目草稿。
 *
 * 流程：Uri → ML Kit InputImage → 文字识别 → 阈值判断 → ParseEntryUseCase(isOcrInput=true)。
 * 识别结果去除空白后不足 15 字符视为识别失败，直接返回 [Outcome.RecognitionFailed]。
 */
class OcrEntryUseCase @Inject constructor(
    private val imageLoader: OcrImageLoader,
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

        /** 图片 Uri 无法读取或解码 */
        data class ImageReadError(val cause: Exception) : Outcome

        /** ML Kit 识别图片时发生异常 */
        data class RecognitionError(val cause: Exception) : Outcome
    }

    suspend operator fun invoke(uri: Uri): Outcome {
        val image = try {
            imageLoader.load(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Outcome.ImageReadError(e)
        }

        val rawText = try {
            recognizeText(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Outcome.RecognitionError(e)
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
