package io.github.anderescaray.cents.data.local.converter

import androidx.room.TypeConverter
import io.github.anderescaray.cents.data.local.entity.AccountType
import io.github.anderescaray.cents.data.local.entity.CategoryKind
import io.github.anderescaray.cents.data.local.entity.TransactionKind
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters.
 *
 * Enums are stored by **name**, never by ordinal: an ordinal silently remaps every stored
 * row the day someone reorders the enum. Dates are stored as epoch day and instants as
 * epoch millis, both UTC-anchored and comparable with plain SQL.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun accountTypeToName(value: AccountType?): String? = value?.name

    @TypeConverter
    fun nameToAccountType(value: String?): AccountType? = value?.let(AccountType::valueOf)

    @TypeConverter
    fun categoryKindToName(value: CategoryKind?): String? = value?.name

    @TypeConverter
    fun nameToCategoryKind(value: String?): CategoryKind? = value?.let(CategoryKind::valueOf)

    @TypeConverter
    fun transactionKindToName(value: TransactionKind?): String? = value?.name

    @TypeConverter
    fun nameToTransactionKind(value: String?): TransactionKind? = value?.let(TransactionKind::valueOf)
}
