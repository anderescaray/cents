package io.github.anderescaray.cents.domain.model

/** What kind of container an account is. */
enum class AccountType {
    CASH,
    BANK,
    CARD,
    SAVINGS,
    BROKER,
    OTHER,
}

/** Whether a category classifies money coming in or going out. */
enum class CategoryKind {
    INCOME,
    EXPENSE,
}

/** The direction of a movement. Amounts themselves are always positive. */
enum class TransactionKind {
    INCOME,
    EXPENSE,
}
