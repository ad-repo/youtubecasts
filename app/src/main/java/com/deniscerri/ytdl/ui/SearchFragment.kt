package com.deniscerri.ytdl.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingData
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.SearchSuggestionItem
import com.deniscerri.ytdl.database.models.SearchSuggestionType
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.HomeAdapter
import com.deniscerri.ytdl.ui.adapter.SearchSuggestionsAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.isURL
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment(), SearchSuggestionsAdapter.OnItemClickListener, HomeAdapter.OnItemClickListener {
    private lateinit var mainActivity: MainActivity
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var searchSuggestionsAdapter: SearchSuggestionsAdapter
    private lateinit var searchResultsAdapter: HomeAdapter
    private lateinit var providersChipGroup: ChipGroup
    private lateinit var queriesChipGroup: ChipGroup
    private lateinit var queriesConstraint: ConstraintLayout
    private lateinit var chipGroupDivider: View
    private lateinit var searchProgress: View
    private lateinit var layoutInflaterRef: LayoutInflater
    private var queryList = mutableListOf<String>()
    private var suggestionsJob: Job? = null
    private var navigateHomeWhenHidden = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        layoutInflaterRef = inflater
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]

        searchBar = view.findViewById(R.id.search_bar)
        searchView = view.findViewById(R.id.search_view)
        searchView.setupWithSearchBar(searchBar)
        queriesChipGroup = view.findViewById(R.id.queries)
        queriesConstraint = view.findViewById(R.id.queries_constraint)
        providersChipGroup = view.findViewById(R.id.providers)
        chipGroupDivider = view.findViewById(R.id.chipGroupDivider)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.search_toolbar)
        toolbar.setNavigationOnClickListener { closeSearchWindow() }

        searchSuggestionsAdapter = SearchSuggestionsAdapter(this, requireActivity())
        view.findViewById<RecyclerView>(R.id.search_suggestions_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchSuggestionsAdapter
            itemAnimator = null
        }
        setupSearchResults(view)

        initMenu()
        mainActivity.hideBottomNavigation()
        searchView.show()

        val initialQuery = arguments?.getString("query").orEmpty()
        val initialQueries = arguments?.getStringArrayList("queries").orEmpty()
        if (initialQueries.isNotEmpty()) {
            initialQueries.forEach { addQueryChip(it) }
        } else if (initialQuery.isNotBlank()) {
            searchView.setText(initialQuery)
        }

        searchView.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.SHOWN) {
                mainActivity.hideBottomNavigation()
            } else if (newState == SearchView.TransitionState.HIDDEN && navigateHomeWhenHidden) {
                navigateHomeWhenHidden = false
                navigateHome()
            }
        }

        mainActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            closeSearchWindow()
        }
    }

    private fun setupSearchResults(view: View) {
        searchProgress = view.findViewById(R.id.search_progress)
        searchResultsAdapter = HomeAdapter(this, requireActivity())

        view.findViewById<RecyclerView>(R.id.search_results_recycler).apply {
            layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
            adapter = searchResultsAdapter
            enableFastScroll()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSearchViewItems(searchView.editText.text.toString())
    }

    override fun onDestroyView() {
        suggestionsJob?.cancel()
        mainActivity.showBottomNavigation()
        super.onDestroyView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initMenu() {
        val queriesInitStartBtn = queriesConstraint.findViewById<MaterialButton>(R.id.init_search_query)
        val isRightToLeft = resources.getBoolean(R.bool.is_right_to_left)
        val providers = resources.getStringArray(R.array.search_engines)
        val providersValues = resources.getStringArray(R.array.search_engines_values).toMutableList()

        for (i in providersValues.indices) {
            val provider = providers[i]
            val providerValue = providersValues[i]
            val chip = layoutInflaterRef.inflate(R.layout.filter_chip, providersChipGroup, false) as Chip
            chip.text = provider
            chip.id = i
            chip.tag = providerValue
            chip.setOnClickListener {
                sharedPreferences.edit().putString("search_engine", providerValue).apply()
            }
            providersChipGroup.addView(chip)
        }

        searchView.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.SHOWN) {
                val currentProvider = sharedPreferences.getString("search_engine", "ytsearch")
                providersChipGroup.children.forEach {
                    val chip = providersChipGroup.findViewById<Chip>(it.id)
                    if (chip?.tag?.equals(currentProvider) == true) {
                        chip.isChecked = true
                        return@forEach
                    }
                }

                updateSearchProviderVisibility(searchView.editText.text.toString())
                updateSearchViewItems(searchView.editText.text.toString())
            }
        }

        searchView.editText.doAfterTextChanged {
            if (searchView.currentTransitionState != SearchView.TransitionState.SHOWN) return@doAfterTextChanged
            updateSearchViewItems(it.toString())
        }

        searchView.editText.setOnTouchListener { _, event ->
            try {
                val drawableLeft = 0
                val drawableRight = 2
                if (event.action == MotionEvent.ACTION_UP) {
                    val tappedAddIcon =
                        (isRightToLeft && event.x < searchView.editText.left - searchView.editText.compoundDrawables[drawableLeft].bounds.width()) ||
                            (!isRightToLeft && event.x > searchView.editText.right - searchView.editText.compoundDrawables[drawableRight].bounds.width())
                    if (tappedAddIcon) {
                        addQueryChip(searchView.editText.text.toString())
                        searchView.editText.setText("")
                        return@setOnTouchListener true
                    }
                }
            } catch (ignored: Exception) {
            }
            false
        }

        searchView.editText.setOnEditorActionListener { _, _, _ ->
            initSearch()
            true
        }

        searchBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.delete_results -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        resultViewModel.cancelParsingQueries()
                        resultViewModel.deleteAll()
                    }
                    searchBar.setText("")
                }

                R.id.delete_search -> {
                    resultViewModel.deleteAllSearchQueryHistory()
                    searchSuggestionsAdapter.submitList(listOf())
                }
            }
            true
        }

        queriesChipGroup.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            queriesConstraint.isVisible = queriesChipGroup.childCount > 0
        }

        queriesInitStartBtn.setOnClickListener {
            initSearch()
        }
    }

    private fun updateSearchViewItems(searchQuery: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            delay(250)
            if (searchView.editText.text.isEmpty()) {
                searchView.editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            } else {
                searchView.editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_plus, 0)
            }

            val combinedList = mutableListOf<SearchSuggestionItem>()

            val history = withContext(Dispatchers.IO) {
                resultViewModel.getSearchHistory().map { it.query }.filter { it.contains(searchQuery, ignoreCase = true) }
            }.map {
                SearchSuggestionItem(it, SearchSuggestionType.HISTORY)
            }
            val suggestions = if (sharedPreferences.getBoolean("search_suggestions", false)) {
                withContext(Dispatchers.IO) {
                    resultViewModel.getSearchSuggestions(searchQuery)
                }
            } else {
                emptyList()
            }.map {
                SearchSuggestionItem(it, SearchSuggestionType.SUGGESTION)
            }

            combinedList.addAll(history)
            combinedList.addAll(suggestions)

            val url = checkClipboard()
            url?.apply {
                val alreadyHasThem = all { queriesChipGroup.children.any { c -> (c as Chip).text.contains(it) } }
                if (isNotEmpty() && !alreadyHasThem) {
                    combinedList.add(0, SearchSuggestionItem(joinToString("\n"), SearchSuggestionType.CLIPBOARD))
                }
            }

            searchSuggestionsAdapter.submitList(combinedList)
            updateSearchProviderVisibility(searchView.editText.text.toString())
        }
    }

    private fun updateSearchProviderVisibility(searchText: String) {
        if (Patterns.WEB_URL.matcher(searchText).matches()) {
            providersChipGroup.visibility = GONE
            chipGroupDivider.visibility = GONE
        } else {
            providersChipGroup.visibility = VISIBLE
            chipGroupDivider.visibility = VISIBLE
        }
    }

    private fun initSearch() {
        queryList = mutableListOf()
        if (queriesChipGroup.childCount > 0) {
            queriesChipGroup.children.forEach {
                val query = (it as Chip).text.toString().trim { c -> c <= ' ' }
                if (query.isNotEmpty()) {
                    queryList.add(query)
                }
            }
            queriesChipGroup.removeAllViews()
        }
        if (searchView.editText.text.isNotBlank()) {
            queryList.add(searchView.editText.text.toString())
        }

        if (queryList.isEmpty()) return
        if (queryList.size == 1) {
            searchBar.setText(queryList.first())
        }

        searchView.hide()
        if (!sharedPreferences.getBoolean("incognito", false)) {
            queryList.forEach { q ->
                resultViewModel.addSearchQueryToHistory(q)
            }
        }
        resultViewModel.setCurrentQueries(queryList)
        startSearch()
    }

    private fun startSearch() {
        lifecycleScope.launch {
            searchProgress.isVisible = true
            if (sharedPreferences.getBoolean("quick_download", false) || sharedPreferences.getString("preferred_download_type", "video") == "command") {
                if (queryList.size == 1 && Patterns.WEB_URL.matcher(queryList.first()).matches()) {
                    withContext(Dispatchers.IO) { resultViewModel.repository.deleteAll() }
                    try {
                        val result = downloadViewModel.createEmptyResultItem(queryList.first())
                        if (sharedPreferences.getBoolean("download_card", true)) {
                            showSingleDownloadSheet(
                                resultItem = result,
                                type = DownloadType.valueOf(sharedPreferences.getString("preferred_download_type", "video")!!)
                            )
                        } else {
                            val downloadItem = withContext(Dispatchers.IO) {
                                downloadViewModel.createDownloadItemFromResult(
                                    result = result,
                                    givenType = DownloadType.valueOf(sharedPreferences.getString("preferred_download_type", "video")!!)
                                )
                            }
                            downloadViewModel.queueDownloads(listOf(downloadItem))
                        }
                    } finally {
                        searchProgress.isVisible = false
                    }
                } else {
                    renderSearchResults()
                }
            } else {
                renderSearchResults()
            }
        }
    }

    private suspend fun renderSearchResults() {
        try {
            val results = withContext(Dispatchers.IO) {
                resultViewModel.repository.deleteAll()
                queryList.flatMap {
                    resultViewModel.repository.getResultsFromSource(
                        inputQuery = it,
                        resetResults = false,
                        addToResults = false
                    )
                }
            }
            // Hide the progress indicator before submitting: submitData with a static
            // PagingData suspends indefinitely, so the finally below is unreachable on success.
            searchProgress.isVisible = false
            searchResultsAdapter.submitData(PagingData.from(results))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.errored))
                .setMessage(e.message)
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        } finally {
            searchProgress.isVisible = false
        }
    }

    private fun showSingleDownloadSheet(resultItem: ResultItem, type: DownloadType) {
        if (findNavController().currentBackStack.value.firstOrNull { it.destination.id == R.id.downloadBottomSheetDialog } == null) {
            val bundle = Bundle()
            downloadCardViewModel.setResultItem(resultItem)
            downloadCardViewModel.setDownloadItem(null)
            bundle.putSerializable("type", downloadViewModel.getDownloadType(type, resultItem.url))
            findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
        }
    }

    private fun navigateHome() {
        if (findNavController().currentDestination?.id != R.id.homeFragment) {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
    }

    private fun closeSearchWindow() {
        if (searchView.isShowing) {
            navigateHomeWhenHidden = true
            searchView.hide()
        } else {
            navigateHome()
        }
    }

    private fun addQueryChip(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val present = queriesChipGroup.children.firstOrNull { (it as Chip).text.toString() == value }
        if (present == null) {
            val chip = layoutInflaterRef.inflate(R.layout.input_chip, queriesChipGroup, false) as Chip
            chip.text = value
            chip.chipBackgroundColor = ColorStateList.valueOf(
                MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK)
            )
            chip.setOnClickListener {
                if (queriesChipGroup.childCount == 1) queriesConstraint.visibility = View.GONE
                queriesChipGroup.removeView(chip)
            }
            queriesChipGroup.addView(chip)
        }
        queriesConstraint.isVisible = queriesChipGroup.childCount > 0
    }

    private fun checkClipboard(): List<String>? {
        return kotlin.runCatching {
            val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip!!.getItemAt(0).text
            clip.split("\r", "\n").map { it.trim() }.filter { it.isURL() }
        }.getOrNull()
    }

    override fun onSearchSuggestionClick(text: String) {
        val res = text.split("\n")
        if (res.size == 1) {
            searchView.setText(text)
            initSearch()
        } else {
            res.forEach {
                onSearchSuggestionAdd(it)
            }
        }
    }

    override fun onSearchSuggestionAdd(text: String) {
        text.split("\n").forEach { addQueryChip(it) }
        searchView.editText.setText("")
        searchSuggestionsAdapter.getList().apply {
            if (isNotEmpty() && first().type == SearchSuggestionType.CLIPBOARD) {
                searchSuggestionsAdapter.submitList(toMutableList().drop(1))
            }
        }
    }

    override fun onSearchSuggestionLongClick(text: String, position: Int) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + text + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
            resultViewModel.removeSearchQueryFromHistory(text)
            updateSearchViewItems(searchView.editText.text.toString())
        }
        deleteDialog.show()
    }

    override fun onSearchSuggestionAddToSearchBar(text: String) {
        searchView.editText.setText(text)
        searchView.editText.setSelection(searchView.editText.length())
    }

    override fun onButtonClick(item: ResultItem, type: DownloadType?) {
        if (sharedPreferences.getBoolean("download_card", true)) {
            showSingleDownloadSheet(item, type!!)
        } else {
            lifecycleScope.launch {
                val downloadItem = withContext(Dispatchers.IO) {
                    downloadViewModel.createDownloadItemFromResult(result = item, givenType = type!!)
                }
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
        }
    }

    override fun onLongButtonClick(item: ResultItem, type: DownloadType?) {
        showSingleDownloadSheet(item, type!!)
    }

    override fun onCardClick(item: ResultItem, isChecked: Boolean) {
        searchResultsAdapter.clearCheckedItems()
    }

    override fun onCardDetailsClick(item: ResultItem) {
        val bundle = Bundle()
        bundle.putParcelable("result", item)
        findNavController().navigate(R.id.resultCardDetailsDialog, bundle)
    }
}
