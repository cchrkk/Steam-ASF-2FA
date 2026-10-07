package tk.chrk.qrloginapprover

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import tk.chrk.qrloginapprover.ui.App
import tk.chrk.qrloginapprover.ui.SteamAsf2faTheme

class MainActivity : ComponentActivity() {
	private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		// Keep codes and confirmation details out of screenshots and the recents preview
		window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		requestCamera.launch(Manifest.permission.CAMERA)
		setContent {
			SteamAsf2faTheme {
				App()
			}
		}
	}
}
