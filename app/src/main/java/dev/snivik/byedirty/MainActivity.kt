package dev.snivik.byedirty

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import dev.snivik.byedirty.data.ShowerHeadReading
import dev.snivik.byedirty.data.ShowerStore
import dev.snivik.byedirty.nfc.Iso15693Reader
import dev.snivik.byedirty.reminder.Reminders
import dev.snivik.byedirty.ui.AppActions
import dev.snivik.byedirty.ui.ByeDirtyApp
import dev.snivik.byedirty.ui.ScanState
import dev.snivik.byedirty.ui.pretty
import dev.snivik.byedirty.ui.theme.ByeDirtyTheme
import java.io.IOException

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private lateinit var store: ShowerStore
    private val nfc: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    /** Null when no scan sheet is showing. */
    private var scanState by mutableStateOf<ScanState?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableReminders() else toast("Allow notifications to get a filter reminder")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = ShowerStore(applicationContext)

        val actions = AppActions(
            sync = ::startSync,
            dismissScan = { scanState = null },
            openNfcSettings = {
                scanState = null
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            },
            shareDumps = ::shareDumps,
            toggleReminders = ::toggleReminders,
            setShowersPerWeek = { store.changeShowersPerWeek(it); Reminders.reschedule(this, store) },
        )
        setContent {
            ByeDirtyTheme {
                ByeDirtyApp(store, scanState, actions)
            }
        }
        handleTagIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTagIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // While the app is open, any tap on the shower head syncs - no need to press Sync first.
        nfc?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }

    /** Launched by tapping the tag while the app was closed (its NDEF link points at helloklean.com). */
    private fun handleTagIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        intent.action = null // don't re-sync on rotation
        Thread { onTagDiscovered(tag) }.start()
    }

    private fun startSync(fullMemory: Boolean) {
        val adapter = nfc
        scanState = when {
            adapter == null -> ScanState.Error("This phone doesn't have NFC, so it can't talk to the shower head.")
            !adapter.isEnabled -> ScanState.Error("Turn on NFC to sync with your shower head.", nfcOff = true)
            else -> ScanState.Waiting(fullMemory)
        }
    }

    /** Runs on a binder thread (reader mode) or a worker thread (launch intent). */
    override fun onTagDiscovered(tag: Tag) {
        // The first sync reads the whole log to build history; after that the newest records are enough.
        val fullMemory = (scanState as? ScanState.Waiting)?.fullMemory ?: false || store.records.isEmpty()
        runOnUiThread { scanState = ScanState.Reading(0f) }
        val result = try {
            val dump = Iso15693Reader.read(
                tag, fullMemory,
                extraBlocks = { ShowerHeadReading.recentRecordBlocks(it, RECENT_RECORDS) },
                onProgress = { p -> runOnUiThread { scanState = ScanState.Reading(p) } },
            )
            if (!ShowerHeadReading.decode(dump).isSupportedShowerHead) {
                ScanState.Error("That tag doesn't look like a supported shower head.")
            } else {
                runOnUiThread {
                    store.onSynced(dump)
                    Reminders.reschedule(this, store)
                }
                ScanState.Success(
                    if (fullMemory) "Full scan saved (${dump.readCount * dump.blockSize / 1024} KB). You can share it from this screen."
                    else "Your filter status is up to date."
                )
            }
        } catch (e: TagLostException) {
            ScanState.Error("The phone moved away too soon. Hold it still against the handle and try again.")
        } catch (e: IOException) {
            ScanState.Error("Something went wrong talking to the shower head (${e.message}). Please try again.")
        } catch (e: SecurityException) {
            // Thrown when the tag goes out of range between operations on some devices.
            ScanState.Error("The phone moved away too soon. Hold it still against the handle and try again.")
        }
        runOnUiThread { scanState = result }
    }

    private fun toggleReminders() {
        if (store.remindersEnabled) {
            store.changeReminders(false)
            Reminders.reschedule(this, store)
            toast("Filter reminder turned off")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableReminders()
        }
    }

    private fun enableReminders() {
        store.changeReminders(true)
        val date = Reminders.reschedule(this, store)
        toast(
            when {
                date != null -> "We'll remind you on ${date.pretty()}"
                store.estimate?.refillDate == null -> "Reminder on. We'll set it once a few syncs show how fast the filter is used."
                else -> "Reminder on. Your filter is already due for a change."
            }
        )
    }

    private fun shareDumps() {
        val files = store.archivedDumps()
        if (files.isEmpty()) return toast("Sync first to capture a scan")
        val uris = ArrayList(files.map { FileProvider.getUriForFile(this, "$packageName.files", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("text/plain")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .putExtra(Intent.EXTRA_SUBJECT, "ByeDirty scans (${files.size})")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share shower head scans"))
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private companion object {
        /** Log records fetched on a normal sync: enough for the 30-shower view with short sessions mixed in. */
        const val RECENT_RECORDS = 64
    }
}
