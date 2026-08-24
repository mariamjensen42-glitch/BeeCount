package com.cycling.beecount.data.ml

import android.content.Context
import android.content.res.AssetManager
import com.cycling.beecount.domain.usecase.CategoryClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter
import timber.log.Timber

/**
 * 端侧 TF Lite 交易分类器（智能分类建议，离线隐私）：
 * 加载 assets/ml 下的 category_model.tflite + vocab.txt + labels.txt，
 * 把「对方 + 备注」字符级 token 化后推理，返回 Top-3 类别候选。
 *
 * 模型由 scripts/train_category_classifier.py 训练导出。文件缺失或损坏时 [ready]=false，
 * [suggest] 安全返回空列表（降级，不阻断记账）。输入长度固定 [MAX_LEN]，字符级 vocab 与
 * 训练脚本保持一致。
 */
@Singleton
class TfliteCategoryClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : CategoryClassifier {

    private companion object {
        const val MODEL_PATH = "ml/category_model.tflite"
        const val VOCAB_PATH = "ml/vocab.txt"
        const val LABELS_PATH = "ml/labels.txt"
        const val MAX_LEN = 32
        const val TOP_K = 3
    }

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var vocab: Map<String, Int> = emptyMap()
    @Volatile private var labels: List<String> = emptyList()
    @Volatile private var ready = false

    init { load() }

    private fun load() {
        try {
            val am = context.assets
            interpreter = Interpreter(loadBuffer(am, MODEL_PATH))
            vocab = am.open(VOCAB_PATH).bufferedReader().useLines { lines ->
                lines.withIndex().associate { (i, w) -> w.trim() to i }
            }
            labels = am.open(LABELS_PATH).bufferedReader().useLines { it.toList() }
            ready = true
            Timber.d("TF Lite 分类模型已加载：%d 类别 / %d 词表", labels.size, vocab.size)
        } catch (e: Exception) {
            Timber.w(
                e,
                "TF Lite 分类模型未就绪，分类建议降级为空（运行 scripts/train_category_classifier.py 生成模型后放入 app/src/main/assets/ml/）",
            )
            ready = false
        }
    }

    private fun loadBuffer(am: AssetManager, path: String): ByteBuffer {
        val fd = am.openFd(path)
        val stream = FileInputStream(fd.fileDescriptor)
        val channel = stream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            .also { stream.close() }
    }

    override suspend fun suggest(counterparty: String?, note: String?): List<String> {
        val interp = interpreter ?: return emptyList()
        if (!ready || labels.isEmpty() || vocab.isEmpty()) return emptyList()
        val text = buildString {
            counterparty?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
            note?.takeIf { it.isNotBlank() }?.let { append(it) }
        }.trim()
        if (text.isEmpty()) return emptyList()

        val input = Array(1) { tokenize(text) }
        val output = Array(1) { FloatArray(labels.size) }
        interp.run(input, output)
        return output[0]
            .mapIndexed { i, score -> labels[i] to score }
            .sortedByDescending { it.second }
            .take(TOP_K)
            .map { it.first }
    }

    /** 字符级 token 化（与训练脚本一致）：按字符切分，未知字归 0，截断到 [MAX_LEN] */
    private fun tokenize(text: String): IntArray {
        val unknown = 0
        return text.take(MAX_LEN).map { ch -> vocab[ch.toString()] ?: unknown }.toIntArray()
    }
}
