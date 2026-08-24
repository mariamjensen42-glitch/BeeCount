package com.cycling.beecount.domain.usecase

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

/**
 * 语音识别 adapter：优先设备端（离线）识别，设备端不可用时不拦截而是用标准识别器。
 * `isOnDeviceRecognitionAvailable` 要求设备已下载离线模型，很多设备/模拟器恒为 false——
 * 因此 [isAvailable] 放宽为「存在任意语音识别服务」，识别时按可用性选则设备端或标准识别器，
 * 两者都以 `EXTRA_PREFER_OFFLINE` 偏好离线。domain 层的 [SpeechToText] 只在这里接触 `android.speech.*`。
 */
class OnDeviceSpeechToText @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SpeechToText {

    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun recognize(languageTag: String): String {
        val offlineAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        // 设备端可用则优先（真正离线）；否则用标准识别器（默认服务，也偏好离线）
        val recognizer = if (offlineAvailable) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        return try {
            suspendCancellableCoroutine { cont ->
                Timber.d("语音识别开始，offline=%b，lang=%s", offlineAvailable, languageTag)
                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onError(error: Int) {
                        Timber.w("语音识别错误：%d", error)
                        if (cont.isActive) cont.resume("")
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (cont.isActive) cont.resume(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
                recognizer.setRecognitionListener(listener)
                cont.invokeOnCancellation {
                    Timber.d("语音识别取消")
                    recognizer.destroy()
                }
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    },
                )
            }
        } finally {
            runCatching { recognizer.destroy() }
        }
    }
}
