package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.group
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Group
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomGroupRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var groups: RoomGroupRepository
    private lateinit var transactions: RoomTransactionRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
        groups = RoomGroupRepository(database.groupDao(), database.transactionDao(), clock)
        transactions = RoomTransactionRepository(database.transactionDao(), database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a trip adds up what went out, what came back, and what it cost`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group(name = "Lisbon trip"))
            database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))
            database.transactionDao().insert(transaction(checking, amountMinor = 6_000, groupId = lisbon))
            // A friend paying back half the hotel lowers what the trip cost.
            database.transactionDao().insert(
                transaction(checking, amountMinor = 9_000, kind = TransactionKind.INCOME, groupId = lisbon),
            )
            // And a movement in no group is no part of it.
            database.transactionDao().insert(transaction(checking, amountMinor = 99_999))

            val trip = groups.observeSummaries("EUR").first().single()

            assertEquals(Money(9_000, "EUR"), trip.income)
            assertEquals(Money(24_000, "EUR"), trip.expenses)
            assertEquals("negative: it cost 150 €", Money(-15_000, "EUR"), trip.net)
            assertEquals(3, trip.movementCount)
        }

    @Test
    fun `a job that earned more than it cost has a positive net`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val job = database.groupDao().insert(group(name = "Wedding photos"))
            database.transactionDao().insert(
                transaction(checking, amountMinor = 120_000, kind = TransactionKind.INCOME, groupId = job),
            )
            database.transactionDao().insert(transaction(checking, amountMinor = 5_000, groupId = job))

            assertEquals(
                Money(115_000, "EUR"),
                groups
                    .observeSummaries("EUR")
                    .first()
                    .single()
                    .net,
            )
        }

    @Test
    fun `a transfer or a trade's cash is never part of what a group came to`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val broker = database.accountDao().insert(account(name = "Broker"))
            val lisbon = database.groupDao().insert(group())
            val tradeId = database.tradeDao().insert(trade(database.holdingDao().insert(holding(broker))))
            // The form never offers a group to either, but the sum must not depend on that.
            database.transactionDao().insert(
                transaction(checking, amountMinor = 50_000, transferId = 1, groupId = lisbon),
            )
            database.transactionDao().insert(
                transaction(broker, amountMinor = 40_000, tradeId = tradeId, groupId = lisbon),
            )
            database.transactionDao().insert(transaction(checking, amountMinor = 2_000, groupId = lisbon))

            val trip = groups.observeSummaries("EUR").first().single()

            assertEquals(Money(2_000, "EUR"), trip.expenses)
            assertEquals(1, trip.movementCount)
        }

    @Test
    fun `an empty group is still listed, at zero, with no span`() =
        runTest {
            database.groupDao().insert(group(name = "Moving house"))

            val empty = groups.observeSummaries("EUR").first().single()

            assertTrue(empty.isEmpty)
            assertEquals(Money(0, "EUR"), empty.net)
            assertNull(empty.firstDate)
            assertNull(empty.lastDate)
        }

    @Test
    fun `the span is the first and last movement actually recorded`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group())
            database.transactionDao().insert(
                transaction(checking, date = LocalDate.of(2026, 10, 6), groupId = lisbon),
            )
            database.transactionDao().insert(
                transaction(checking, date = LocalDate.of(2026, 9, 20), groupId = lisbon),
            )

            val trip = groups.observeSummaries("EUR").first().single()

            assertEquals(LocalDate.of(2026, 9, 20), trip.firstDate)
            assertEquals(LocalDate.of(2026, 10, 6), trip.lastDate)
        }

    @Test
    fun `the most recent group comes first, and a new empty one counts as today's`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val summer = database.groupDao().insert(group(name = "Summer"))
            database.transactionDao().insert(transaction(checking, date = LocalDate.of(2026, 7, 10), groupId = summer))
            val spring = database.groupDao().insert(group(name = "Spring"))
            database.transactionDao().insert(transaction(checking, date = LocalDate.of(2026, 4, 3), groupId = spring))
            database.groupDao().insert(group(name = "Made this morning"))

            val order = groups.observeSummaries("EUR").first().map { it.group.name }

            assertEquals(listOf("Made this morning", "Summer", "Spring"), order)
        }

    @Test
    fun `the picker lists groups by name`() =
        runTest {
            database.groupDao().insert(group(name = "lisbon"))
            database.groupDao().insert(group(name = "Berlin"))

            assertEquals(listOf("Berlin", "lisbon"), groups.observeGroups().first().map { it.name })
        }

    @Test
    fun `spending by category covers only the group, largest first`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val hotels = database.categoryDao().insert(category(name = "Hotels"))
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            val refunds = database.categoryDao().insert(category(name = "Refunds", kind = CategoryKind.INCOME))
            val lisbon = database.groupDao().insert(group())
            database.transactionDao().insert(transaction(checking, hotels, amountMinor = 18_000, groupId = lisbon))
            database.transactionDao().insert(transaction(checking, food, amountMinor = 4_000, groupId = lisbon))
            database.transactionDao().insert(transaction(checking, food, amountMinor = 2_000, groupId = lisbon))
            database.transactionDao().insert(
                transaction(checking, refunds, TransactionKind.INCOME, amountMinor = 9_000, groupId = lisbon),
            )
            // The same category outside the trip is not the trip's.
            database.transactionDao().insert(transaction(checking, food, amountMinor = 50_000))

            val slices = groups.observeByCategory(lisbon, TransactionKind.EXPENSE, "EUR").first()

            assertEquals(listOf("Hotels", "Restaurants"), slices.map { it.name })
            assertEquals(Money(6_000, "EUR"), slices[1].total)
            assertEquals(0.75f, slices[0].share, 0.0001f)
        }

    @Test
    fun `deleting a group leaves every movement standing, in no group`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group())
            val hotel = database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))

            groups.deleteGroup(lisbon)

            assertTrue(groups.observeSummaries("EUR").first().isEmpty())
            val survivor = database.transactionDao().findById(hotel)
            assertEquals(18_000L, survivor?.amountMinor)
            assertNull(survivor?.groupId)
        }

    @Test
    fun `saving trims the name, and saving again edits rather than adds`() =
        runTest {
            val id = groups.saveGroup(Group(Group.NEW, "  Lisbon trip  "))
            groups.saveGroup(Group(id, "Lisbon", startDate = LocalDate.of(2026, 10, 2)))

            val saved = groups.observeGroups().first().single()
            assertEquals(id, saved.id)
            assertEquals("Lisbon", saved.name)
            assertEquals(LocalDate.of(2026, 10, 2), saved.startDate)
        }

    @Test
    fun `a group needs a name, and cannot end before it starts`() =
        runTest {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { groups.saveGroup(Group(Group.NEW, "   ")) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    groups.saveGroup(
                        Group(Group.NEW, "Backwards", LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 2)),
                    )
                }
            }
            assertTrue(groups.observeGroups().first().isEmpty())
        }

    @Test
    fun `the list can be narrowed to one group, and a row says which group it is in`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group(name = "Lisbon trip"))
            database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))
            database.transactionDao().insert(transaction(checking, amountMinor = 700))

            val inTrip = transactions.observeTransactions(TransactionFilter(groupId = lisbon)).first()

            assertEquals(
                18_000L,
                inTrip
                    .single()
                    .transaction.amount.minorUnits,
            )
            assertEquals("Lisbon trip", inTrip.single().groupName)
            assertEquals(lisbon, inTrip.single().transaction.groupId)
        }

    @Test
    fun `searching for the group's name finds its movements`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group(name = "Lisbon trip"))
            database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))
            database.transactionDao().insert(transaction(checking, amountMinor = 700))

            val found = transactions.observeTransactions(TransactionFilter(query = "lisbon")).first()

            assertEquals(
                18_000L,
                found
                    .single()
                    .transaction.amount.minorUnits,
            )
        }

    @Test
    fun `editing a movement keeps its group, and can move it out of one`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group())
            val id = database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))

            val stored = transactions.findById(id)!!
            assertEquals(lisbon, stored.groupId)
            transactions.updateTransaction(stored.copy(note = "Hotel"))
            assertEquals(lisbon, database.transactionDao().findById(id)?.groupId)

            transactions.updateTransaction(stored.copy(groupId = null))
            assertNull(database.transactionDao().findById(id)?.groupId)
        }

    @Test
    fun `a group with dates covers them, one open end leaves that side open, none covers nothing`() {
        val october2 = LocalDate.of(2026, 10, 2)
        val october6 = LocalDate.of(2026, 10, 6)
        val trip = Group(1, "Lisbon", october2, october6)

        assertTrue(trip.covers(october2))
        assertTrue(trip.covers(october6))
        assertFalse(trip.covers(october6.plusDays(1)))
        assertTrue(Group(2, "Since", startDate = october2).covers(LocalDate.of(2027, 1, 1)))
        assertFalse(Group(3, "Undated").covers(october2))
    }
}
