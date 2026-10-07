package tk.chrk.qrloginapprover

import android.Manifest
import android.os.Bundle
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
		requestCamera.launch(Manifest.permission.CAMERA)
		setContent {
			SteamAsf2faTheme {
				App()
			}
		}
	}
}
