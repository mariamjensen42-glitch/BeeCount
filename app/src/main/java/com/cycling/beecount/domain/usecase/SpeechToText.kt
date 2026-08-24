package com.cycling.beecount.domain.usecase

/**
 * 语音转文本 seam：把一声语音转为文本。domain 层只依赖此接口，
 * 具体 Android 设备端识别（离线）作为 adapter 在后，与 [OcrTextRecognizer] 同构。
 */
interface SpeechToText {
    suspend fun recognize(languageTag: String): String

    /** 是否可用设备端（离线）语音识别 */
    fun isAvailable(): Boolean

    /** 是否已授予录音权限 */
    fun hasRecordAudioPermission(): Boolean
}
