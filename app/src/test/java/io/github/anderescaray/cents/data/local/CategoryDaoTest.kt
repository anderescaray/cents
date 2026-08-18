package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteConstraintException
import io.github.anderescaray.cents.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryDaoTest {
    private lateinit var database: CentsDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `subcategories are found through their parent`() =
        runTest {
            val dao = database.categoryDao()
            val car = dao.insert(category(name = "Car"))
            val fuel = dao.insert(category(name = "Fuel", parentId = car))
            dao.insert(category(name = "Restaurants"))

            assertEquals(listOf(fuel), dao.observeChildren(car).first().map { it.id })
        }

    @Test
    fun `income and expense categories are listed separately`() =
        runTest {
            val dao = database.categoryDao()
            val salary = dao.insert(category(name = "Salary", kind = CategoryKind.INCOME))
            val rent = dao.insert(category(name = "Rent", kind = CategoryKind.EXPENSE))

            assertEquals(listOf(salary), dao.observeActiveByKind(CategoryKind.INCOME).first().map { it.id })
            assertEquals(listOf(rent), dao.observeActiveByKind(CategoryKind.EXPENSE).first().map { it.id })
        }

    @Test
    fun `a category still holding subcategories cannot be deleted`() =
        runTest {
            val dao = database.categoryDao()
            val car = dao.insert(category(name = "Car"))
            dao.insert(category(name = "Fuel", parentId = car))
            val stored = requireNotNull(dao.findById(car))

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { dao.delete(stored) }
            }
        }

    @Test
    fun `an unused category reports no transactions and can be deleted`() =
        runTest {
            val dao = database.categoryDao()
            val id = dao.insert(category(name = "Typo"))

            assertEquals(0, dao.countTransactions(id))

            dao.delete(requireNotNull(dao.findById(id)))

            assertEquals(emptyList<Long>(), dao.observeAll().first().map { it.id })
        }

    @Test
    fun `archiving hides a category from the picker while keeping its history`() =
        runTest {
            val dao = database.categoryDao()
            val id = dao.insert(category(name = "Old habit"))

            dao.setArchived(id, archived = true)

            assertEquals(emptyList<Long>(), dao.observeActiveByKind(CategoryKind.EXPENSE).first().map { it.id })
            assertEquals(listOf(id), dao.observeAll().first().map { it.id })
        }
}
