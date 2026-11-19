package com.example.notesapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesAppCrudTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.getInstance(context).noteDao()
        runBlocking {
            dao.getAll().first().forEach { dao.delete(it) }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Belum ada catatan").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun roomDaoCrudAndOrdering() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.noteDao()
            assertTrue(dao.getAll().first().isEmpty())

            dao.insert(Note(title = "catatan 1"))
            dao.insert(Note(title = "catatan bagus"))

            val afterInsert = dao.getAll().first()
            assertEquals(2, afterInsert.size)
            assertEquals("catatan bagus", afterInsert[0].title)
            assertEquals("catatan 1", afterInsert[1].title)
            assertTrue(afterInsert[0].id > afterInsert[1].id)

            val target = afterInsert[0]
            dao.update(target.copy(title = "catatan diubah"))
            val afterUpdate = dao.getAll().first()
            assertEquals(target.id, afterUpdate[0].id)
            assertEquals("catatan diubah", afterUpdate[0].title)

            dao.delete(afterUpdate[0])
            val afterDelete = dao.getAll().first()
            assertEquals(1, afterDelete.size)
            assertEquals("catatan 1", afterDelete[0].title)
        } finally {
            db.close()
        }
    }

    @Test
    fun uiCrudAndInlineEditFlow() {
        composeRule.onNodeWithText("Catatan Saya").assertIsDisplayed()
        composeRule.onNodeWithText("Belum ada catatan").assertIsDisplayed()
        composeRule.onNodeWithText("Jumlah catatan: 0").assertIsDisplayed()

        // Blank input must be ignored by ViewModel validation.
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Catatan baru")).performTextInput("   ")
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.onNodeWithText("Jumlah catatan: 0").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction() and hasText("Catatan baru")).performTextReplacement("catatan 1")
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("catatan 1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasSetTextAction() and hasText("Catatan baru")).performTextReplacement("catatan 2")
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Jumlah catatan: 2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("catatan 2").assertIsDisplayed()

        // Clicking Edit on top item enters inline edit mode for that item only.
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onAllNodesWithText("Ubah catatan").assertCountEquals(1)
        composeRule.onNode(hasSetTextAction() and hasText("Ubah catatan"))
            .performTextReplacement("catatan bagus")
        composeRule.onNodeWithContentDescription("Simpan").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("catatan bagus").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Ubah catatan").assertCountEquals(0)

        // Switching Edit to another item keeps only one inline editor open.
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onAllNodesWithText("Ubah catatan").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Batal").performClick()
        composeRule.onAllNodesWithText("Ubah catatan").assertCountEquals(0)

        // Cancel must discard unsaved changes.
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Ubah catatan"))
            .performTextReplacement("tidak disimpan")
        composeRule.onNodeWithContentDescription("Batal").performClick()
        composeRule.onNodeWithText("catatan bagus").assertIsDisplayed()
        composeRule.onNodeWithText("tidak disimpan").assertDoesNotExist()

        // Blank edit must keep previous title intact.
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Ubah catatan")).performTextClearance()
        composeRule.onNodeWithContentDescription("Simpan").performClick()
        composeRule.onNodeWithText("catatan bagus").assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription("Hapus")[0].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Jumlah catatan: 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("catatan bagus").assertDoesNotExist()
        composeRule.onNodeWithText("catatan 1").assertIsDisplayed()
    }
}
