package io.github.anderescaray.cents.domain.model

/**
 * What kind of container an account is: cash, a bank, or a broker (`DECISIONS.md` D34). *Card*,
 * *Savings* and *Other* went in Room v12, which rewrote any account of those types to [BANK].
 */
enum class AccountType {
    CASH,
    BANK,
    BROKER,
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
