package com.example.communityserviceapp.util.bottomSheet

import android.os.Bundle
import androidx.navigation.NavDirections
import com.example.communityserviceapp.R

class ShowAllJobNatureCategoryBottomSheetDirections private constructor() {
  private data class ActionShowAllJobNatureCategoryBottomSheetToRecipientFilterFragment(
    val SQLQuery: String? = null,
    val searchKeyword: String?,
    val filterKeywords: Array<String>?
  ) : NavDirections {
    override fun getActionId(): Int =
        R.id.action_showAllJobNatureCategoryBottomSheet_to_recipientFilterFragment

    override fun getArguments(): Bundle {
      val result = Bundle()
      result.putString("SQLQuery", this.SQLQuery)
      result.putString("searchKeyword", this.searchKeyword)
      result.putStringArray("filterKeywords", this.filterKeywords)
      return result
    }
  }

  companion object {
    fun actionShowAllJobNatureCategoryBottomSheetToRecipientFilterFragment(
      SQLQuery: String? = null,
      searchKeyword: String?,
      filterKeywords: Array<String>?
    ): NavDirections = ActionShowAllJobNatureCategoryBottomSheetToRecipientFilterFragment(SQLQuery,
        searchKeyword, filterKeywords)
  }
}
