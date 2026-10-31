package com.offlinevault.app.data.model

import androidx.annotation.StringRes
import com.offlinevault.app.R

enum class AccountCategory(
    val storageValue: String,
    @StringRes val labelRes: Int,
) {
    SOCIAL("Social", R.string.category_social),
    BANKING("Banking", R.string.category_banking),
    WORK("Work", R.string.category_work),
    SHOPPING("Shopping", R.string.category_shopping),
    OTHER("Other", R.string.category_other);

    companion object {
        fun fromStorage(value: String?): AccountCategory {
            if (value.isNullOrBlank()) return OTHER
            return entries.firstOrNull {
                it.storageValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}
