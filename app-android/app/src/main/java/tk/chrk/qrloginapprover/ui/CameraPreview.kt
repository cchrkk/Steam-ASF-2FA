package tk.chrk.qrloginapprover.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun CameraPreview(onQrDetected: (String) -> Unit, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
	val executor = remember { Executors.newSingleThreadExecutor() }

	// Keep the latest callback so the analyzer (built once) always calls into the current composition
	val onDetected by rememberUpdatedState(onQrDetected)

	Box(modifier = modifier) {
		AndroidView(
			modifier = Modifier.fillMaxSize(),
			factory = { ctx ->
				val previewView = PreviewView(ctx)

				val providerFuture = ProcessCameraProvider.getInstance(ctx)
				providerFuture.addListener({
					val provider = providerFuture.get()

					val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

					val analysis = ImageAnalysis.Builder()
						.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
						.build()

					analysis.setAnalyzer(executor) { proxy ->
						val mediaImage = proxy.image

						if (mediaImage == null) {
							proxy.close()

							return@setAnalyzer
						}

						val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)

						scanner.process(input)
							.addOnSuccessListener { barcodes ->
								barcodes.firstOrNull()?.rawValue?.let(onDetected)
							}
							.addOnCompleteListener { proxy.close() }
					}

					try {
						provider.unbindAll()
						provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
					} catch (_: Exception) {
						// camera unavailable, the screen will still let the user pick a bot
					}
				}, ContextCompat.getMainExecutor(ctx))

				previewView
			},
		)
	}
}
