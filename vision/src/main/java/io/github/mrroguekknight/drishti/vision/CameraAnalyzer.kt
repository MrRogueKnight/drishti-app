package io.github.mrroguekknight.drishti.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class CameraAnalyzer(
  private val tfLiteDetector: TFLiteDetector,
  private val resultCallback: (List<Detection>) -> Unit,
  private val modelInputWidth: Int,
  private val modelInputHeight: Int
): ImageAnalysis.Analyzer {

  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val bitmap = imageProxy.toBitmap()
    if (bitmap != null) {
      // Preprocess the image from ImageProxy
      val imageProcessor = ImageProcessor.Builder()
          .add(ResizeOp(modelInputHeight, modelInputWidth, ResizeOp.ResizeMethod.BILINEAR))
          // Add other ops like normalization if required by the model
          .build()

      var tensorImage = TensorImage(DataType.UINT8) // Or FLOAT32 if model is not quantized
      tensorImage.load(bitmap)
      tensorImage = imageProcessor.process(tensorImage)

      // Run inference using the detector
      val results = tfLiteDetector.run(tensorImage)
      resultCallback(results)
    }
    imageProxy.close()
  }
}
