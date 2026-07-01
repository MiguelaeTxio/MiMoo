package com.miguelaetxio.mimoo

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
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.ui.navigation.MiMooNavGraph
import com.miguelaetxio.mimoo.ui.player.PlayerBar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var storageManager: StorageManager

    /**
     * SAF folder picker. Shown once; the chosen Uri is persisted by
     * StorageManager so the picker is not shown again on subsequent
     * launches.
     * ---
     * Selector de carpeta SAF. Se muestra una vez; el Uri elegido es
     * persistido por StorageManager para que el selector no vuelva a
     * aparecer en arranques posteriores.
     */
    private val openDocumentTree =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                storageManager.saveRootUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show the folder picker the first time the app is launched,
        // so the user can choose where to store downloaded audio files.
        // Mostrar el selector de carpeta la primera vez que se abre la
        // app para que el usuario elija donde guardar los audios.
        if (!storageManager.hasRootUri()) {
            openDocumentTree.launch(null)
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

