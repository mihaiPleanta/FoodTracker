package com.example.foodtracker.util.label

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val LABELS_DIR = "labels"

/** Fișier nou în cache/labels/ expus prin FileProvider (poza nu părăsește telefonul). */
fun newLabelPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, LABELS_DIR).apply { mkdirs() }
    val file = File.createTempFile("label_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Șterge toate pozele temporare ale etichetelor. */
fun clearLabelPhotos(context: Context) {
    try {
        File(context.cacheDir, LABELS_DIR).deleteRecursively()
    } catch (e: Exception) {
        // best effort
    }
}

object LabelTextRecognizer {
    /** OCR on-device; orice eșec întoarce listă goală (excepție: anulare). */
    suspend fun recognize(context: Context, uri: Uri): List<OcrLine> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val text = Tasks.await(recognizer.process(image))
                text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    if (line.text.isBlank()) return@mapNotNull null
                    OcrLine(line.text, box.left, box.top, box.right, box.bottom)
                }
            } finally {
                recognizer.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}
