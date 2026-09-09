package io.github.anderescaray.cents.data.local.converter

import androidx.room.TypeConverter
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.model.TradeSide

/**
 * The investments half of the type converters.
 *
 * A second class rather than more methods on [Converters], for the reason a resource file per
 * feature exists: a converter is added whenever an enum is, and one growing class is a place
 * every feature has to edit. Room takes as many converter classes as it is given.
 *
 * The rule they all follow is [Converters]': enums are stored by **name**, never by ordinal,
 * because an ordinal silently remaps every stored row the day someone reorders the enum.
 */
class InvestmentConverters {
    @TypeConverter
    fun assetClassToName(value: AssetClass?): String? = value?.name

    @TypeConverter
    fun nameToAssetClass(value: String?): AssetClass? = value?.let(AssetClass::valueOf)

    @TypeConverter
    fun tradeSideToName(value: TradeSide?): String? = value?.name

    @TypeConverter
    fun nameToTradeSide(value: String?): TradeSide? = value?.let(TradeSide::valueOf)

    @TypeConverter
    fun quoteSourceToName(value: QuoteSource?): String? = value?.name

    @TypeConverter
    fun nameToQuoteSource(value: String?): QuoteSource? = value?.let(QuoteSource::valueOf)
}
