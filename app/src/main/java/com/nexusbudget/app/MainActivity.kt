package com.nexusbudget.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexusbudget.app.data.ImportFiles
import com.nexusbudget.app.ui.NexusRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single activity. FragmentActivity is required by the biometric prompt used for app lock. */
class MainActivity : FragmentActivity() {

    private val container: AppContainer get() = (application as NexusApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a rotation the same intent comes back; it was already handled.
        if (savedInstanceState == null) handleIntent(intent)

        // With app lock on, hide the app's content in the recents screen and block screenshots.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settings.settings.map { it.appLock }.distinctUntilChanged().collect { secure ->
                    if (secure) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            NexusRoot(container = container, activity = this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data = intent.data
        if (data?.scheme == "nexusbudget" && data.host == "plaid-complete") {
            container.plaidReturns.tryEmit(Unit)
            return
        }
        // A statement or CSV file opened from a download or shared from another app.
        val file: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (file == null || file.scheme != "content") return
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) { ImportFiles.read(this@MainActivity, file) }
            if (imported != null) {
                container.pendingImport.value = imported
            } else {
                Toast.makeText(this@MainActivity, "Couldn't read that file.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
