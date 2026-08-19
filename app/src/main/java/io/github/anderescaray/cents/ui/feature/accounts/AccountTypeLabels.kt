package io.github.anderescaray.cents.ui.feature.accounts

import androidx.annotation.StringRes
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AccountType

/** What each kind of container is called on screen. Kept out of the enum: it is UI text. */
@StringRes
fun AccountType.labelRes(): Int =
    when (this) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.BANK -> R.string.account_type_bank
        AccountType.CARD -> R.string.account_type_card
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.BROKER -> R.string.account_type_broker
        AccountType.OTHER -> R.string.account_type_other
    }
