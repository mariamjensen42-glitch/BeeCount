package com.cycling.beecount.domain.usecase

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface OcrImageLoader {
    fun load(uri: Uri): InputImage
}

class MlKitOcrImageLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OcrImageLoader {
    override fun load(uri: Uri): InputImage = InputImage.fromFilePath(context, uri)
}
