package com.example.communityserviceapp.ui.faq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.communityserviceapp.databinding.FaqFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.ui.adapters.FaqAdapter
import com.example.communityserviceapp.util.setupSwipeRefreshColorScheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FaqFragment : Fragment() {

    private val faqViewModel: FaqViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FaqFragmentBinding
    @Inject lateinit var faqAdapter: FaqAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FaqFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            faqViewmodel = faqViewModel
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshUser(this.javaClass.simpleName)
    }

    override fun onDestroyView() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActionBar()
        setupRecyclerview()
        setupListener()
        subscribeUI()
        setupSwipeRefreshColorScheme(binding.swipeRefreshLayout)
    }

    private fun setupRecyclerview() {
        binding.faqRecyclerview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = faqAdapter
        }
    }

    private fun setupActionBar() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupListener() {
        faqAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val itemCount = faqAdapter.itemCount
                    val isEmpty = itemCount <= 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        onEmptyFaq()
                    } else if (!isEmpty) {
                        onFaqFound()
                    }
                }
                LoadState.Loading -> { }
                is LoadState.Error -> {
                    val error = when {
                        loadState.prepend is LoadState.Error -> loadState.prepend as LoadState.Error
                        loadState.append is LoadState.Error -> loadState.append as LoadState.Error
                        loadState.refresh is LoadState.Error -> loadState.refresh as LoadState.Error
                        else -> null
                    }
                    error?.let { Timber.d("Paging Load Failed: ${it.error.message}") }
                }
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            faqAdapter.refresh()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun subscribeUI() {
        faqViewModel.faqList.observe(
            viewLifecycleOwner,
            {
                faqAdapter.submitData(viewLifecycleOwner.lifecycle, it)
            }
        )
    }

    private fun onEmptyFaq() {
        binding.faqRecyclerview.visibility = View.GONE
        binding.noItemFoundContainer.visibility = View.VISIBLE
        binding.noItemFoundAnimationView.resumeAnimation()
    }

    private fun onFaqFound() {
        binding.noItemFoundContainer.visibility = View.GONE
        binding.noItemFoundAnimationView.pauseAnimation()
        binding.faqRecyclerview.visibility = View.VISIBLE
    }
}
