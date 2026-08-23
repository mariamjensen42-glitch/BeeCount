package com.cycling.beecount

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cycling.beecount.domain.usecase.OcrImageImportUseCase
import com.cycling.beecount.domain.usecase.OcrImageSource
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
        val sourceUri = android.net.Uri.fromFile(source)

        val outcome = OcrImageImportUseCase(context)(sourceUri)

        assertTrue(outcome is OcrImageImportUseCase.Outcome.Imported)
        val importedSource = (outcome as OcrImageImportUseCase.Outcome.Imported).source
        assertEquals(OcrImageSource::class, importedSource::class)
        val cachedFile = File(importedSource.cacheFilePath)
        assertTrue(cachedFile.exists())
        assertEquals(
            listOf<Byte>(1, 2, 3, 4),
            cachedFile.readBytes().toList(),
        )

        source.delete()
        cachedFile.delete()
    }
}
