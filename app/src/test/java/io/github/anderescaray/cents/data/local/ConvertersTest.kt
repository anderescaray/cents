package io.github.anderescaray.cents.data.local

import io.github.anderescaray.cents.data.local.converter.Converters
import io.github.anderescaray.cents.data.local.entity.AccountType
import io.github.anderescaray.cents.data.local.entity.CategoryKind
import io.github.anderescaray.cents.data.local.entity.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Plain JVM tests: no Android, no database, just the value mapping. */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `dates survive the round trip, including before the epoch`() {
        listOf(
            LocalDate.of(1969, 7, 20),
            LocalDate.of(1970, 1, 1),
            LocalDate.of(2026, 8, 18),
            LocalDate.of(2100, 12, 31),
        ).forEach { date ->
            val stored = converters.localDateToEpochDay(date)
            assertEquals(date, converters.epochDayToLocalDate(stored))
        }
    }

    @Test
    fun `a leap day is not shifted by the round trip`() {
        val leapDay = LocalDate.of(2024, 2, 29)

        assertEquals(leapDay, converters.epochDayToLocalDate(converters.localDateToEpochDay(leapDay)))
    }

    @Test
    fun `instants keep millisecond precision`() {
        val instant = Instant.parse("2026-08-18T21:45:12.345Z")

        assertEquals(instant, converters.epochMillisToInstant(converters.instantToEpochMillis(instant)))
    }

    @Test
    fun `enums are stored by name, so reordering them cannot remap stored rows`() {
        assertEquals("BROKER", converters.accountTypeToName(AccountType.BROKER))
        assertEquals(AccountType.BROKER, converters.nameToAccountType("BROKER"))

        assertEquals("INCOME", converters.categoryKindToName(CategoryKind.INCOME))
        assertEquals(CategoryKind.INCOME, converters.nameToCategoryKind("INCOME"))

        assertEquals("EXPENSE", converters.transactionKindToName(TransactionKind.EXPENSE))
        assertEquals(TransactionKind.EXPENSE, converters.nameToTransactionKind("EXPENSE"))
    }

    @Test
    fun `nulls stay null in both directions`() {
        assertNull(converters.localDateToEpochDay(null))
        assertNull(converters.epochDayToLocalDate(null))
        assertNull(converters.instantToEpochMillis(null))
        assertNull(converters.epochMillisToInstant(null))
        assertNull(converters.accountTypeToName(null))
        assertNull(converters.nameToAccountType(null))
    }
}
