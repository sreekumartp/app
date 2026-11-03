package app.anchor.anchor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.anchor.graph
import app.anchor.ui.theme.AnchorTheme
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * IN-04 physical anchor. A ₹20–40 NFC sticker in another room (or a printed QR code on phones
 * without NFC) is the only way to end a Strict session early. Only a salted hash of the tag UID or
 * QR secret is stored.
 */
class AnchorActivity : ComponentActivity() {

    private enum class Mode { REGISTER, UNLOCK }

    private sealed interface Step {
        data object Choose : Step
        data object WaitingForTag : Step
        data class ShowQr(val content: String, val bitmap: Bitmap) : Step
        data class Done(val message: String) : Step
    }

    private val mode by lazy { if (intent.getStringExtra(EXTRA_MODE) == MODE_UNLOCK) Mode.UNLOCK else Mode.REGISTER }
    private var step by mutableStateOf<Step>(Step.Choose)
    private var error by mutableStateOf<String?>(null)
    private val nfc: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    private val scan = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { onSecret(it.toByteArray(), "qr") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (mode == Mode.UNLOCK) step = Step.WaitingForTag
        setContent {
            AnchorTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { Content() }
                }
            }
        }
    }

    @Composable
    private fun Content() {
        val hasNfc = nfc != null
        when (val s = step) {
            Step.Choose -> {
                Text("Choose your anchor", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Put it in another room. Walking to it breaks the impulse — and it's the only way to end a Strict session early.",
                    textAlign = TextAlign.Center,
                )
                if (hasNfc) {
                    Button(onClick = { step = Step.WaitingForTag }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Nfc, contentDescription = null)
                        Text("  Use an NFC sticker")
                    }
                }
                OutlinedButton(onClick = { step = newQr() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCode2, contentDescription = null)
                    Text("  Use a printed QR code")
                }
                TextButton(onClick = ::finish) { Text("Cancel") }
            }
            Step.WaitingForTag -> {
                Icon(Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (mode == Mode.UNLOCK) "Walk to your anchor and tap it" else "Hold your NFC sticker to the back of the phone",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                if (!hasNfc) Text("This phone has no NFC. Scan your printed QR code instead.", textAlign = TextAlign.Center)
                else if (nfc?.isEnabled == false) Text("NFC is turned off. Turn it on in quick settings.", textAlign = TextAlign.Center)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                }
                if (mode == Mode.UNLOCK) {
                    OutlinedButton(onClick = ::launchScan, modifier = Modifier.fillMaxWidth()) { Text("Scan my QR anchor instead") }
                }
                TextButton(onClick = ::finish) { Text(if (mode == Mode.UNLOCK) "Keep focusing" else "Cancel") }
            }
            is Step.ShowQr -> {
                Text("Your QR anchor", style = MaterialTheme.typography.headlineSmall)
                Image(s.bitmap.asImageBitmap(), contentDescription = "QR code anchor", modifier = Modifier.size(260.dp))
                Text(
                    "Print it (or photograph it with another device) and stick it up in another room. Don't keep it on this phone.",
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { onSecret(s.content.toByteArray(), "qr") }, modifier = Modifier.fillMaxWidth()) { Text("I've saved it somewhere else") }
                TextButton(onClick = { step = Step.Choose }) { Text("Back") }
            }
            is Step.Done -> {
                Text(s.message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Button(onClick = ::finish) { Text("Done") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfc?.enableReaderMode(
            this,
            { tag -> runOnUiThread { if (step == Step.WaitingForTag) onSecret(tag.id, "nfc") } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }

    private fun launchScan() {
        scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan your QR anchor").setBeepEnabled(false).setOrientationLocked(false))
    }

    private fun newQr(): Step {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val content = "anchor:v1:" + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val bmp = BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 720, 720)
        return Step.ShowQr(content, bmp)
    }

    private fun onSecret(secret: ByteArray, kind: String) {
        val g = graph
        when (mode) {
            Mode.REGISTER -> {
                g.secure.setAnchor(kind, secret)
                lifecycleScope.launch { g.configRepo.update { it.copy(anchorRegistered = true) } }
                step = Step.Done(if (kind == "nfc") "NFC anchor registered. Stick it somewhere you'd rather not walk to." else "QR anchor registered.")
            }
            Mode.UNLOCK -> {
                if (!g.secure.matchesAnchor(secret)) {
                    error = "That isn't your anchor."
                    return
                }
                g.guard.endFocus(viaAnchor = true)
                Toast.makeText(this, "Session ended with your anchor", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val MODE_UNLOCK = "unlock"
        private const val MODE_REGISTER = "register"

        fun register(context: Context) = context.startActivity(Intent(context, AnchorActivity::class.java).putExtra(EXTRA_MODE, MODE_REGISTER))
        fun unlock(context: Context) = context.startActivity(Intent(context, AnchorActivity::class.java).putExtra(EXTRA_MODE, MODE_UNLOCK))
    }
}
