package com.cycling.beecount.domain.usecase

/**
 * OCR 图像源：domain 层接受的、与 Android `Uri` 解耦的图像引用。
 *
 * 目前承载缓存的本地文件路径（[cacheFilePath]），由 `OcrImageImportUseCase` 从原始 Uri 复制后产出。
 * 这样 `EntryIntake` 的接口不直接暴露 `android.net.Uri`，适配器实现内部再把它转为
 * ML Kit `InputImage`。
 */
data class OcrImageSource(
    val cacheFilePath: String,
)

/**
 * OCR 文字识别 seam：把图像源转为可解析文本。domain 层只依赖此接口，
 * 具体 ML Kit 识别逻辑（`MlKitOcrTextRecognizer`）作为 adapter 在后。
 */
fun interface OcrTextRecognizer {
    suspend fun recognize(source: OcrImageSource): String
}
