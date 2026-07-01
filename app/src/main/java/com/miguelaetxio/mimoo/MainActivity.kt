package com.miguelaetxio.mimoo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.miguelaetxio.mimoo.ui.navigation.MiMooNavGraph
import com.miguelaetxio.mimoo.ui.player.PlayerBar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Launcher for WRITE_EXTERNAL_STORAGE runtime permission.
     * Only needed on API 28 and below; on API 29+ Android manages
     * storage scopes automatically and no runtime grant is required.
     * ---
     * Launcher para el permiso de escritura en almacenamiento externo
     * en runtime. Solo necesario en API 28 e inferior; en API 29+
     * Android gestiona los scopes de almacenamiento automaticamente
     * y no se requiere concesion en runtime.
     */
    private val requestStoragePermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* result ignored: DownloadWorker will fail gracefully if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request WRITE_EXTERNAL_STORAGE on API <= 28 so the
        // DownloadWorker can create /sdcard/MiMoo/ and write .opus files.
        // On API 29+ scoped storage applies and no permission is needed.
        // Solicitar WRITE_EXTERNAL_STORAGE en API <= 28 para que
        // DownloadWorker pueda crear /sdcard/MiMoo/ y escribir .opus.
        // En API 29+ se aplica scoped storage y no se necesita permiso.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestStoragePermission.launch(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        Column(modifier = Modifier.weight(1f)) {
                            MiMooNavGraph(navController = navController)
                        }
                        PlayerBar()
                    }
                }
            }
        }
    }
}
