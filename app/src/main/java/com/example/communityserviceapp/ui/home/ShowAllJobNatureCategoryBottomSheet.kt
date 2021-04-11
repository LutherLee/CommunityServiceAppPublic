package com.example.communityserviceapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.ShowAllJobNatureCategoryBottomSheetBinding
import com.example.communityserviceapp.util.Result
import com.example.communityserviceapp.util.bottomSheet.BaseBottomSheet
import com.example.communityserviceapp.util.bottomSheet.onShowAllJobNatureCategoryBottomSheetDismissListener

class ShowAllJobNatureCategoryBottomSheet : BaseBottomSheet() {

    private lateinit var binding: ShowAllJobNatureCategoryBottomSheetBinding
    private var searchQuery: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ShowAllJobNatureCategoryBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.apply {
            categoryAdvocacy.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_advocacy)) }
            categoryAnimalWelfare.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_animal_welfare)) }
            categoryChildren.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_children)) }
            categoryCulture.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_culture)) }
            categoryDifferentlyAbled.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_differently_abled)) }
            categoryEducation.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_education)) }
            categoryEnvironment.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_environment)) }
            categoryFeaturedOrganisations.setOnClickListener {
                searchRecipientBasedOnKeyword(
                    getString(R.string.category_featured_organisations)
                )
            }
            categoryHealth.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_health)) }
            categoryOrangAsli.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_orang_asli)) }
            categoryOtherCommunities.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_other_communitites)) }
            categoryRecommended.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_recommended)) }
            categoryRefugees.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_refugees)) }
            categorySeniorCitizens.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_senior_citizens)) }
            categorySingleParents.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_single_parents)) }
            categorySocialEnterprise.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_social_enterprise)) }
            categorySponsors.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_sponsors)) }
            categorySupportGroups.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_support_groups)) }
            categoryTaxExemption.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_tax_exemption)) }
            categoryWomen.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_women)) }
            categoryYouth.setOnClickListener { searchRecipientBasedOnKeyword(getString(R.string.category_youth)) }
        }
    }

    private fun searchRecipientBasedOnKeyword(searchKeyword: String) {
        dismiss()
        searchQuery = "SELECT * FROM recipient WHERE jobNature LIKE \"%$searchKeyword%\""
        val data = mutableMapOf<String, Any?>()
        data["searchQuery"] = searchQuery!!
        data["searchKeyword"] = searchKeyword
        onShowAllJobNatureCategoryBottomSheetDismissListener?.invoke(Result.Success(data))
    }
}
