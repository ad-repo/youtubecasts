package com.deniscerri.ytdl.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ChannelItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ChannelsViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.PlaylistViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.HomeAdapter
import com.deniscerri.ytdl.ui.more.PlaylistDialogs
import com.deniscerri.ytdl.ui.more.cookies.WebViewActivity
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL


class HomeFragment : Fragment(), HomeAdapter.OnItemClickListener, OnClickListener {
    private var inputQueries: MutableList<String>? = null
    private lateinit var homeAdapter: HomeAdapter
    private var totalCount: Int = 0
    private var firstResult: ResultItem? = null

    private var downloadSelectedFab: ExtendedFloatingActionButton? = null
    private var downloadAllFab: ExtendedFloatingActionButton? = null
    private var clipboardFab: ExtendedFloatingActionButton? = null
    private var homeFabs: LinearLayout? = null
    private var notificationUtil: NotificationUtil? = null
    private var downloadQueue: ArrayList<ResultItem>? = null

    private lateinit var playlistNameFilterScrollView: HorizontalScrollView
    private lateinit var playlistNameFilterChipGroup: ChipGroup

    private lateinit var resultViewModel : ResultViewModel
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var historyViewModel : HistoryViewModel
    private lateinit var downloadCardViewModel : DownloadCardViewModel
    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var channelsViewModel: ChannelsViewModel

    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var homeSearchButton: MaterialButton? = null
    private var recyclerView: RecyclerView? = null
    private var uiHandler: Handler? = null
    private var quickLaunchSheet = false
    private var sharedPreferences: SharedPreferences? = null
    private var actionMode: ActionMode? = null
    private var appBarLayout: AppBarLayout? = null
    private var materialToolbar: MaterialToolbar? = null
    private var loadingItems: Boolean = false

    private var showDownloadAllFab: Boolean = false
    private var showClipboardFab: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        quickLaunchSheet = false
        notificationUtil = NotificationUtil(requireContext())
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        uiHandler = Handler(Looper.getMainLooper())

        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        playlistViewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]
        channelsViewModel = ViewModelProvider(this)[ChannelsViewModel::class.java]

        downloadQueue = ArrayList()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        //initViews
        appBarLayout = view.findViewById(R.id.home_appbarlayout)
        materialToolbar = view.findViewById(R.id.home_toolbar)
        homeSearchButton = view.findViewById(R.id.home_search)
        homeSearchButton?.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }
        view.findViewById<MaterialButton>(R.id.home_channels)?.setOnClickListener {
            findNavController().navigate(R.id.channelsFragment)
        }
        view.findViewById<MaterialButton>(R.id.home_downloads)?.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }
        view.findViewById<MaterialButton>(R.id.home_playlists)?.setOnClickListener {
            findNavController().navigate(R.id.playlistsFragment)
        }
        view.findViewById<MaterialButton>(R.id.home_more)?.setOnClickListener {
            findNavController().navigate(R.id.moreFragment)
        }
        homeFabs = view.findViewById(R.id.home_fabs)
        downloadSelectedFab = homeFabs!!.findViewById(R.id.download_selected_fab)
        downloadAllFab = homeFabs!!.findViewById(R.id.download_all_fab)
        clipboardFab = homeFabs!!.findViewById(R.id.copied_url_fab)
        playlistNameFilterScrollView = view.findViewById(R.id.playlist_selection_chips_scrollview)
        playlistNameFilterChipGroup = view.findViewById(R.id.playlist_selection_chips)

        homeAdapter =
            HomeAdapter(
                this,
                requireActivity()
            )
        homeAdapter.onAddToPlaylist = { item ->
            PlaylistDialogs.showAddToPlaylistDialog(
                this,
                item,
                playlistViewModel,
                channelsViewModel,
                downloadViewModel,
                lifecycleScope
            )
        }
        homeAdapter.onAddChannel = { item ->
            addChannelFromResult(item)
        }
        recyclerView = view.findViewById(R.id.recyclerViewHome)
        recyclerView?.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        recyclerView?.adapter = homeAdapter
        recyclerView?.enableFastScroll()

        shimmerCards = view.findViewById(R.id.shimmer_results_framelayout)



        val progressBar = view.findViewById<View>(R.id.progress)

        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]

        lifecycleScope.launch {
            resultViewModel.paginatedItems.collectLatest {
                homeAdapter.submitData(it)
            }
        }

        homeAdapter.addLoadStateListener { loadStates ->
            val isNotLoading = loadStates.refresh is androidx.paging.LoadState.NotLoading
            if (isNotLoading) {
                val size = resultViewModel.totalCount.value;
                val firstResult = resultViewModel.firstResult.value;

                progressBar.isVisible = loadingItems && size > 0
                if(resultViewModel.repository.itemCount.value > 1 || resultViewModel.repository.itemCount.value == -1){
                    showDownloadAllFab = size > 1 && firstResult!!.playlistTitle.isNotEmpty() && !loadingItems
                    downloadAllFab!!.isVisible = showDownloadAllFab
                }else if (resultViewModel.repository.itemCount.value == 1){
                    if (sharedPreferences!!.getBoolean("download_card", true)){
                        if(size == 1 && quickLaunchSheet && parentFragmentManager.findFragmentByTag("downloadSingleSheet") == null){
                            showSingleDownloadSheet(
                                firstResult!!,
                                DownloadType.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!)
                            )
                        }
                    }
                }else{
                    showDownloadAllFab = false
                    downloadAllFab!!.visibility = GONE
                }
                quickLaunchSheet = true
            }
        }

        lifecycleScope.launch {
            resultViewModel.totalCount.collectLatest {
                totalCount = it
            }
        }

        lifecycleScope.launch {
            resultViewModel.firstResult.collectLatest {
                firstResult = it
            }
        }

        lifecycleScope.launch {
            resultViewModel.playlistResults.collectLatest {
                updateMultiplePlaylistResults(it
                    .filter { it2 -> it2 != "YTDLNIS_SEARCH" }
                )
            }
        }

        downloadSelectedFab?.tag = "downloadSelected"
        downloadSelectedFab?.setOnClickListener(this)
        downloadAllFab?.tag = "downloadAll"
        downloadAllFab?.setOnClickListener(this)

        if (arguments?.getString("url") != null){
            val url = requireArguments().getString("url")
            if (inputQueries == null) inputQueries = mutableListOf()
            val argList = url!!.split("\n").filter { it.isURL() }.toMutableList()
            argList.removeAll(listOf("", null))
            inputQueries!!.addAll(argList)
        }

        if (inputQueries != null) {
            resultViewModel.setCurrentQueries(inputQueries!!)
            lifecycleScope.launch(Dispatchers.IO){
                resultViewModel.deleteAll()
                resultViewModel.parseQueries(inputQueries!!){}
                inputQueries = null
            }
        }

        mainActivity?.onBackPressedDispatcher?.addCallback(this) {
            mainActivity?.finishAffinity()
        }

        lifecycleScope.launch {
            launch{
                resultViewModel.uiState.collectLatest { res ->
                    if (res.errorMessage != null){
                        val currentQueries = resultViewModel.currentQueries.value
                        val currentSearchUrl = currentQueries.singleOrNull()
                            ?.takeIf { Patterns.WEB_URL.matcher(it).matches() }

                        kotlin.runCatching {
                            UiUtil.handleNoResults(requireActivity(), res.errorMessage!!,
                                url = currentSearchUrl,
                                continueAnyway = currentSearchUrl != null,
                                continued = {
                                    lifecycleScope.launch {
                                        val query = currentSearchUrl ?: return@launch
                                        if (sharedPreferences!!.getBoolean("download_card", true)) {
                                            withContext(Dispatchers.Main){
                                                showSingleDownloadSheet(
                                                    resultItem = downloadViewModel.createEmptyResultItem(query),
                                                    type = DownloadType.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!),
                                                    disableUpdateData = true
                                                )
                                            }
                                        } else {
                                            val downloadItem = downloadViewModel.createDownloadItemFromResult(
                                                result = downloadViewModel.createEmptyResultItem(query),
                                                givenType = DownloadType.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!)
                                            )
                                            downloadViewModel.queueDownloads(listOf(downloadItem))
                                        }
                                    }
                                },
                                cookieFetch = {
                                    currentSearchUrl?.let { query ->
                                        kotlin.runCatching {
                                            val myIntent = Intent(requireContext(), WebViewActivity::class.java)
                                            myIntent.putExtra("url", "https://${URL(query).host}")
                                            cookiesFetchedResultLauncher.launch(myIntent)
                                        }
                                    }
                                },
                                closed = {}
                            )
                        }
                        resultViewModel.uiState.update {it.copy(errorMessage  = null) }
                    }

                    loadingItems = res.processing
                    progressBar.isVisible = loadingItems && totalCount > 0
                    if (res.processing){
                        recyclerView?.setPadding(0,0,0,0)
                        shimmerCards!!.startShimmer()
                        shimmerCards!!.visibility = VISIBLE
                    }else{
                        recyclerView?.setPadding(0,0,0,100)
                        shimmerCards!!.stopShimmer()
                        shimmerCards!!.visibility = GONE

                        showDownloadAllFab = totalCount > 1 && firstResult!!.playlistTitle.isNotEmpty()
                        downloadAllFab!!.isVisible = showDownloadAllFab
                    }
                }
            }
        }

        lifecycleScope.launch {
            launch{
                downloadViewModel.alreadyExistsUiState.collectLatest { res ->
                    if (res.isNotEmpty()){
                        withContext(Dispatchers.Main){
                            val bundle = bundleOf(
                                Pair("duplicates", ArrayList(res))
                            )
                            delay(500)
                            findNavController().navigate(R.id.downloadsAlreadyExistDialog, bundle)
                        }
                        downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                    }
                }
            }
        }

    }

    private var cookiesFetchedResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            sharedPreferences?.edit()?.putBoolean("use_cookies", true)?.apply()
            retryCurrentSearch()
        }
    }

    private fun retryCurrentSearch() {
        val queries = resultViewModel.currentQueries.value
        if (queries.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            resultViewModel.deleteAll()
            resultViewModel.parseQueries(queries) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if(arguments?.getString("url") == null){
            if (!resultViewModel.uiState.value.processing){
                resultViewModel.checkTrending()
            }
        }else{
            arguments?.remove("url")
        }

        if (arguments?.getBoolean("showDownloadsWithUpdatedFormats") == true){
            notificationUtil?.cancelDownloadNotification(NotificationUtil.FORMAT_UPDATING_FINISHED_NOTIFICATION_ID)
            arguments?.remove("showDownloadsWithUpdatedFormats")
            CoroutineScope(Dispatchers.IO).launch {
                val ids = arguments?.getLongArray("downloadIds") ?: return@launch
                downloadViewModel.turnDownloadItemsToProcessingDownloads(ids.toList(), deleteExisting = true)
                withContext(Dispatchers.Main){
                    findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                }
            }
        }
        
        requireView().post {
            checkClipboard().apply {
                this?.apply {
                    showClipboardFab = this.isNotEmpty()
                    clipboardFab?.isVisible = showClipboardFab
                    clipboardFab?.setOnClickListener {
                        showClipboardFab = false
                        clipboardFab?.isVisible = false
                        val bundle = Bundle()
                        if (this.size == 1) {
                            bundle.putString("query", this.first())
                        } else {
                            bundle.putStringArrayList("queries", ArrayList(this))
                        }
                        findNavController().navigate(R.id.searchFragment, bundle)
                    }
                }

                lifecycleScope.launch {
                    clipboardFab?.extend()
                    delay(1000)
                    clipboardFab?.shrink()
                }
            }
        }
    }

    fun scrollToTop() {
        recyclerView!!.scrollToPosition(0)
        runCatching { appBarLayout?.setExpanded(true, true) }
    }

    @SuppressLint("ResourceType")
    override fun onButtonClick(item: ResultItem, type: DownloadType?) {
        if (sharedPreferences!!.getBoolean("download_card", true)) {
            showSingleDownloadSheet(item, type!!)
        } else {
            lifecycleScope.launch{
                val downloadItem = withContext(Dispatchers.IO){
                    downloadViewModel.createDownloadItemFromResult(
                        result = item,
                        givenType = type!!)
                }
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
        }
    }

    override fun onLongButtonClick(item: ResultItem, type: DownloadType?) {
        showSingleDownloadSheet(item, type!!)
    }

    @SuppressLint("RestrictedApi")
    private fun showSingleDownloadSheet(
        resultItem: ResultItem,
        type: DownloadType,
        disableUpdateData : Boolean = false
    ){
        if(findNavController().currentBackStack.value.firstOrNull {it.destination.id == R.id.downloadBottomSheetDialog} == null &&
            findNavController().currentDestination?.id == R.id.homeFragment
            ){
            //show the fragment if its not in the backstack
            val bundle = Bundle()
            downloadCardViewModel.setResultItem(resultItem)
            downloadCardViewModel.setDownloadItem(null)
            bundle.putSerializable("type", downloadViewModel.getDownloadType(type, resultItem.url))
            if (disableUpdateData) {
                bundle.putBoolean("disableUpdateData", true)
            }
            findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
        }
    }

    private fun addChannelFromResult(item: ResultItem) {
        val rawUrl = item.playlistURL?.trim().orEmpty()
        if (rawUrl.isBlank()) return
        val anchor = recyclerView ?: fragmentView ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val normalized = withContext(Dispatchers.IO) {
                channelsViewModel.normalizeChannelUrl(rawUrl)
            }
            if (normalized == null) {
                Snackbar.make(anchor, getString(R.string.invalid_channel_url), Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            val channelName = item.author.ifBlank {
                normalized.substringAfterLast("/").ifBlank { normalized }
            }
            val result = withContext(Dispatchers.IO) {
                channelsViewModel.insert(ChannelItem(name = channelName, url = normalized))
            }
            val message = if (result > 0) R.string.channel_saved else R.string.channel_already_exists
            Snackbar.make(anchor, getString(message), Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCardClick(item: ResultItem, add: Boolean) {
        lifecycleScope.launch {
            val selectedObjects = homeAdapter.getSelectedObjectsCount(totalCount)
            if (actionMode == null) actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)
            actionMode?.apply {
                if (selectedObjects == 0){
                    this.finish()
                }else{
                    actionMode?.title = "$selectedObjects ${getString(R.string.selected)}"
                    this.menu.findItem(R.id.select_between).isVisible = false
                    if(selectedObjects == 2){
                        val selectedIDs = contextualActionBar.getSelectedIDs().sortedBy { it }
                        val resultsInMiddle = withContext(Dispatchers.IO){
                            resultViewModel.getResultsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
                        }.toMutableList()
                        this.menu.findItem(R.id.select_between).isVisible = resultsInMiddle.isNotEmpty()
                    }
                }
            }
        }
    }

    override fun onCardDetailsClick(item: ResultItem) {
        if (parentFragmentManager.findFragmentByTag("resultDetails") == null){
            val bundle = Bundle()
            bundle.putParcelable("result", item)
            findNavController().navigate(R.id.resultCardDetailsDialog, bundle)
        }
    }

    override fun onClick(v: View) {
        val viewIdName: String = try {
            v.tag.toString()
        } catch (e: Exception) {""}
        if (viewIdName.isNotEmpty()) {
            if (viewIdName == "downloadAll") {
                val showDownloadCard = sharedPreferences!!.getBoolean("download_card", true)

                lifecycleScope.launch {
                    val resultIds = withContext(Dispatchers.IO) {
                        resultViewModel.getAllIds()
                    }

                    downloadViewModel.turnResultItemsToProcessingDownloads(resultIds, downloadNow = !showDownloadCard)
                    if (showDownloadCard){
                        findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                    }
                }


            }
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.main_menu_context, menu)
            mode.title = "${homeAdapter.getSelectedObjectsCount(totalCount)} ${getString(R.string.selected)}"
            homeSearchButton?.isEnabled = false
            playlistNameFilterChipGroup.children.forEach { it.isEnabled = false }
            (activity as MainActivity).disableBottomNavigation()
            downloadAllFab!!.isVisible = false
            clipboardFab!!.isVisible = false
            return true
        }

        override fun onPrepareActionMode(
            mode: ActionMode?,
            menu: Menu?
        ): Boolean {
            return false
        }

        override fun onActionItemClicked(
            mode: ActionMode?,
            item: MenuItem?
        ): Boolean {
            return when (item!!.itemId) {
                R.id.select_between -> {
                    lifecycleScope.launch {
                        val selectedIDs = getSelectedIDs().toMutableList()
                        val resultsInMiddle = withContext(Dispatchers.IO){
                            resultViewModel.getResultsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
                        }.toMutableList()
                        if (resultsInMiddle.isNotEmpty()){
                            selectedIDs.addAll(resultsInMiddle.map { it.id })
                            homeAdapter.checkMultipleItems(selectedIDs)
                            actionMode?.title = "${selectedIDs.count()} ${getString(R.string.selected)}"
                        }
                        mode?.menu?.findItem(R.id.select_between)?.isVisible = false
                    }
                    true
                }
                R.id.delete_results -> {
                    val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
                        lifecycleScope.launch {
                            val selectedObjects = getSelectedIDs()
                            if (selectedObjects.size == totalCount) {
                                resultViewModel.deleteAll()
                            } else {
                                resultViewModel.deleteSelected(selectedObjects)
                            }
                            homeAdapter.clearCheckedItems()
                            actionMode?.finish()
                        }
                    }
                    deleteDialog.show()
                    true
                }
                R.id.download -> {
                    lifecycleScope.launch {
                        val showDownloadCard = sharedPreferences!!.getBoolean("download_card", true)
                        val selectedObjects = getSelectedIDs()

                        if (showDownloadCard && selectedObjects.size == 1) {
                            var resultItem = withContext(Dispatchers.IO) {
                                resultViewModel.getByID(selectedObjects.first())
                            }

                            resultItem?.apply {
                                showSingleDownloadSheet(
                                    resultItem,
                                    downloadViewModel.getDownloadType(url = resultItem.url)
                                )
                            }
                        }else{
                            downloadViewModel.turnResultItemsToProcessingDownloads(selectedObjects, downloadNow = !showDownloadCard)
                            if (showDownloadCard){
                                findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                            }
                        }
                        homeAdapter.clearCheckedItems()
                        actionMode?.finish()
                    }
                    true
                }
                R.id.select_all -> {
                    homeAdapter.checkAll()
                    val selectedCount = homeAdapter.getSelectedObjectsCount(totalCount)
                    mode?.title = "(${selectedCount}) ${resources.getString(R.string.all_items_selected)}"
                    true
                }
                R.id.invert_selected -> {
                    homeAdapter.invertSelected()
                    val selectedCount = homeAdapter.getSelectedObjectsCount(totalCount)
                    actionMode!!.title = "$selectedCount ${getString(R.string.selected)}"
                    if (selectedCount == 0) actionMode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            (activity as MainActivity).enableBottomNavigation()
            homeAdapter.clearCheckedItems()
            homeSearchButton?.isEnabled = true
            playlistNameFilterChipGroup.children.forEach { it.isEnabled = true }
            appBarLayout?.setExpanded(true, true)

            downloadAllFab!!.isVisible = showDownloadAllFab
            clipboardFab!!.isVisible = showClipboardFab
        }

        suspend fun getSelectedIDs() : List<Long>{
            return if (homeAdapter.inverted || homeAdapter.checkedItems.isEmpty()){
                withContext(Dispatchers.IO){
                    resultViewModel.getItemIDsNotPresentIn(homeAdapter.checkedItems.toList())
                }
            }else{
                homeAdapter.checkedItems.toList()
            }
        }
    }

    private fun checkClipboard(): List<String>?{
        return kotlin.runCatching {
            val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip!!.getItemAt(0).text
            clip.split("\r","\n").map { it.trim() }.filter { Patterns.WEB_URL.matcher(it).matches() }
        }.getOrNull()
    }


    companion object {
        private const val TAG = "HomeFragment"
    }

    private fun updateMultiplePlaylistResults(playlistTitles: List<String>) {
        playlistNameFilterChipGroup.children.filter { it.tag != "all" }.forEach {
            playlistNameFilterChipGroup.removeView(it)
        }

        if (playlistTitles.isEmpty() || playlistTitles.size == 1) {
            playlistNameFilterScrollView.isVisible = false
            return
        }

        playlistNameFilterChipGroup.children.first().setOnClickListener {
            resultViewModel.setPlaylistFilter("")
        }

        for (t in playlistTitles) {
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, playlistNameFilterChipGroup, false) as Chip
            tmp.text = t
            tmp.tag = t
            tmp.setOnClickListener {
                resultViewModel.setPlaylistFilter(t)
            }

            playlistNameFilterChipGroup.addView(tmp)
        }

        if (playlistNameFilterChipGroup.children.all { !(it as Chip).isChecked }) {
            (playlistNameFilterChipGroup.children.first() as Chip).isChecked = true
        }

        playlistNameFilterScrollView.isVisible = true
    }
}
