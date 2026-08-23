package com.cycling.beecount.domain.usecase

import android.content.Context
import android.net.Uri
import com.cycling.beecount.domain.model.WeChatImportDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 用例：读取微信账单 xlsx（经 SAF 选中的 Uri）并解析、分类为导入草稿（ADR 0012）。
 * 解析失败返回 [Outcome.Error]，消息可直接展示给用户。
 */
class ParseWeChatBillUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: WeChatBillXlsxParser,
    private val classifier: WeChatBillClassifier,
) {

    sealed interface Outcome {
        data class Parsed(val draft: WeChatImportDraft) : Outcome
        data class Error(val message: String) : Outcome
    }

    suspend operator fun invoke(uri: Uri): Outcome = withContext(Dispatchers.IO) {
        Timber.d("开始解析微信账单，uri=%s", uri)
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("账单内容不可读取")
            stream.use { input ->
                val bill = parser.parse(input)
                val draft = classifier.classify(bill)
                Timber.i("微信账单解析完成：共 %d 行、解析出 %d 条、跳过 %d 条",
                    bill.rows.size, draft.entries.size, draft.skippedCount)
                Outcome.Parsed(draft)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WeChatBillParseException) {
            Timber.w(e, "微信账单格式无法识别：%s", e.message)
            Outcome.Error(e.message ?: "无法识别的账单格式")
        } catch (e: Exception) {
            Timber.e(e, "微信账单解析异常：%s", e.message)
            Outcome.Error("读取账单失败，请确认文件是微信支付导出的账单")
        }
    }
}
