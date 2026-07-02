package com.deniscerri.ytdl.ui.more

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.YtdlnisComposeTheme
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.util.NavbarUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        mainSharedPreferences =  PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()

        val navBarItems = NavbarUtil.getNavBarItems(requireContext())
        val showTerminal = navBarItems.none { n -> n.itemId == R.id.terminalActivity && n.isVisible }
        val showDownloads = navBarItems.none { n -> n.itemId == R.id.historyFragment && n.isVisible }
        val showDownloadQueue = navBarItems.none { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        val tintDynamicAppIcon = mainSharedPreferences.getString("theme_accent", "blue") == "Default" &&
            Build.VERSION.SDK_INT >= 32

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var terminateEnabled by rememberSaveable { mutableStateOf(true) }

                YtdlnisComposeTheme {
                    MoreScreen(
                        showTerminal = showTerminal,
                        showDownloads = showDownloads,
                        showDownloadQueue = showDownloadQueue,
                        tintDynamicAppIcon = tintDynamicAppIcon,
                        terminateEnabled = terminateEnabled,
                        onTerminalClick = {
                            val intent = Intent(context, TerminalActivity::class.java)
                            startActivity(intent)
                        },
                        onLogsClick = {
                            findNavController().navigate(R.id.downloadLogListFragment)
                        },
                        onCommandTemplatesClick = {
                            findNavController().navigate(R.id.commandTemplatesFragment)
                        },
                        onDownloadsClick = {
                            findNavController().navigate(R.id.historyFragment)
                        },
                        onDownloadQueueClick = {
                            findNavController().navigate(R.id.downloadQueueMainFragment)
                        },
                        onCookiesClick = {
                            findNavController().navigate(R.id.cookiesFragment)
                        },
                        onBackClick = {
                            findNavController().navigateUp()
                        },
                        onObserveSourcesClick = {
                            findNavController().navigate(R.id.observeSourcesFragment)
                        },
                        onChannelsClick = {
                            findNavController().navigate(R.id.channelsFragment)
                        },
                        onTerminateClick = {
                            showTerminateConfirmationDialog {
                                terminateEnabled = false
                            }
                        },
                        onTerminateLongClick = {
                            showTerminateConfirmationDialog(skipPreference = true) {
                                terminateEnabled = false
                            }
                        },
                        onSettingsClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    fun showTerminateConfirmationDialog(skipPreference: Boolean = false, onTerminating: () -> Unit = {}) {
        val shouldAskToTerminate = mainSharedPreferences.getBoolean("ask_terminate_app", true)
        if (!shouldAskToTerminate && !skipPreference) {
            onTerminating()
            terminateApp()
            return
        }

        var doNotShowAgainFinalState = !shouldAskToTerminate

        lateinit var dialog: AlertDialog
        val terminateDialog = MaterialAlertDialogBuilder(requireContext())
        terminateDialog.setTitle(getString(R.string.kill_app))
        val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
        terminateDialog.setView(dialogView)

        checkbox.isChecked = doNotShowAgainFinalState
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            doNotShowAgainFinalState = isChecked
        }

        terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.cancel()
        }

        terminateDialog.setPositiveButton(getString(R.string.ok), null)
        dialog = terminateDialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setCanceledOnTouchOutside(false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            mainSharedPreferencesEditor.putBoolean("ask_terminate_app", !doNotShowAgainFinalState).commit()
            onTerminating()
            terminateApp()
        }
    }

    fun terminateApp() {
        lifecycleScope.launch {
            downloadViewModel.pauseAllDownloads()
            mainActivity.finishAndRemoveTask()
            mainActivity.finishAffinity()
            exitProcess(0)
        }
    }

    companion object {
        const val TAG = "MoreFragment"
    }

}
