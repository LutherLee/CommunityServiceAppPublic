package com.example.communityserviceapp.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.BannerItemFragmentBinding
import com.example.communityserviceapp.util.getCircularProgressDrawable

class BannerItemFragment : Fragment() {

    private var binding: BannerItemFragmentBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BannerItemFragmentBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding?.root?.setOnClickListener(null)
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Receive values from fragment arguments (Bundle)
        val args = requireArguments()
        loadBannerImage(args.getString("imageUrl"))
        setupListener(args)
        binding?.bannerDetail?.text = args["detail"].toString()
    }

    private fun loadBannerImage(imageUrl: String?) {
        Glide.with(requireContext())
            .load(imageUrl)
            .centerCrop()
            .centerInside()
            .placeholder(getCircularProgressDrawable(requireContext()))
            .error(R.drawable.ic_baseline_image_grey_24dp)
            .into(binding!!.bannerImage)
    }

    private fun setupListener(args: Bundle) {
        binding?.root?.setOnClickListener {
            val urlLink = Uri.parse(args["website"].toString())
            startActivity(Intent(Intent.ACTION_VIEW).setData(urlLink))
        }
    }
}
