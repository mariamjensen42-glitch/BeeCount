package com.cycling.beecount

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cycling.beecount.domain.usecase.OcrImageImportUseCase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrImageImportUseCaseTest {

    @Test
    fun copiesSelectedContentUriIntoAppPrivateCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "ocr/source-${System.nanoTime()}.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val sourceUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            source,
        )

        val outcome = OcrImageImportUseCase(context)(sourceUri)

        assertTrue(outcome is OcrImageImportUseCase.Outcome.Imported)
        val importedUri = (outcome as OcrImageImportUseCase.Outcome.Imported).uri
        assertEquals("content", importedUri.scheme)
        assertEquals(context.packageName + ".fileprovider", importedUri.authority)
        assertEquals(
            listOf<Byte>(1, 2, 3, 4),
            context.contentResolver.openInputStream(importedUri)!!.use { it.readBytes().toList() },
        )

        source.delete()
    }
}
