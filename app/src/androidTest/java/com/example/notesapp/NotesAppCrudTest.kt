package com.example.notesapp

import android.database.sqlite.SQLiteDatabase
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

            dao.insert(
                Note(
                    title = "Belajar Kotlin",
                    note = "Mempelajari dasar Kotlin dan pemrograman Android."
                )
            )
            dao.insert(
                Note(
                    title = "Tugas PPPB",
                    note = "Menyelesaikan modifikasi aplikasi Notes App."
                )
            )

            val afterInsert = dao.getAll().first()
            assertEquals(2, afterInsert.size)
            assertEquals("Tugas PPPB", afterInsert[0].title)
            assertEquals("Menyelesaikan modifikasi aplikasi Notes App.", afterInsert[0].note)
            assertEquals("Belajar Kotlin", afterInsert[1].title)
            assertEquals("Mempelajari dasar Kotlin dan pemrograman Android.", afterInsert[1].note)
            assertTrue(afterInsert[0].id > afterInsert[1].id)

            val target = afterInsert[1]
            dao.update(
                target.copy(
                    title = "Belajar Jetpack Compose",
                    note = "Mempelajari Compose dan Room Database."
                )
            )
            val afterUpdate = dao.getAll().first()
            assertEquals(target.id, afterUpdate[1].id)
            assertEquals("Belajar Jetpack Compose", afterUpdate[1].title)
            assertEquals("Mempelajari Compose dan Room Database.", afterUpdate[1].note)

            dao.delete(afterUpdate[0])
            val afterDelete = dao.getAll().first()
            assertEquals(1, afterDelete.size)
            assertEquals("Belajar Jetpack Compose", afterDelete[0].title)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration1To2PreservesExistingNotes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "notes_migration_test.db"
        context.deleteDatabase(dbName)

        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL)"
        )
        rawDb.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
        )
        rawDb.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c6d89942e48ccb724710185d2ccf62b8')"
        )
        rawDb.execSQL("INSERT INTO `notes` (`title`) VALUES ('Catatan Lama V1')")
        rawDb.version = 1
        rawDb.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            val dao = migratedDb.noteDao()
            val migratedNotes = dao.getAll().first()
            assertEquals(1, migratedNotes.size)
            assertEquals(1L, migratedNotes[0].id)
            assertEquals("Catatan Lama V1", migratedNotes[0].title)
            assertEquals("", migratedNotes[0].note)

            dao.insert(Note(title = "Catatan Baru V2", note = "Isi catatan v2"))
            val afterInsert = dao.getAll().first()
            assertEquals(2, afterInsert.size)
            assertEquals("Catatan Baru V2", afterInsert[0].title)
            assertEquals("Isi catatan v2", afterInsert[0].note)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun uiCrudAndInlineEditFlow() {
        composeRule.onNodeWithText("Catatan Saya").assertIsDisplayed()
        composeRule.onNodeWithText("Belum ada catatan").assertIsDisplayed()
        composeRule.onNodeWithText("Jumlah catatan: 0").assertIsDisplayed()

        // Blank title must be ignored even when note body is non-empty.
        composeRule.onNode(hasSetTextAction() and hasText("Title")).performTextInput("   ")
        composeRule.onNode(hasSetTextAction() and hasText("Note")).performTextInput("Isi tanpa judul")
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.onNodeWithText("Jumlah catatan: 0").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction() and hasText("Title"))
            .performTextReplacement("Belajar Kotlin")
        composeRule.onNode(hasSetTextAction() and hasText("Note"))
            .performTextReplacement("Mempelajari dasar Kotlin dan pemrograman Android.")
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Belajar Kotlin").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Mempelajari dasar Kotlin dan pemrograman Android.").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction() and hasText("Title"))
            .performTextReplacement("Tugas PPPB")
        composeRule.onNode(hasSetTextAction() and hasText("Note"))
            .performTextReplacement("Menyelesaikan modifikasi aplikasi Notes App.")
        composeRule.onNodeWithText("Tambah").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Jumlah catatan: 2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tugas PPPB").assertIsDisplayed()
        composeRule.onNodeWithText("Menyelesaikan modifikasi aplikasi Notes App.").assertIsDisplayed()

        // Switching Edit to another item keeps only one inline editor open.
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Simpan").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Simpan").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Batal").performClick()
        composeRule.onAllNodesWithContentDescription("Simpan").assertCountEquals(0)

        // Edit the older note ("Belajar Kotlin" at index 1 in DESC order).
        composeRule.onAllNodesWithContentDescription("Edit")[1].performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Belajar Kotlin"))
            .performTextReplacement("Belajar Jetpack Compose")
        composeRule.onNode(
            hasSetTextAction() and hasText("Mempelajari dasar Kotlin dan pemrograman Android.")
        ).performTextReplacement("Mempelajari Compose dan Room Database.")
        composeRule.onNodeWithContentDescription("Simpan").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Belajar Jetpack Compose").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Mempelajari Compose dan Room Database.").assertIsDisplayed()

        // Cancel must discard unsaved changes.
        composeRule.onAllNodesWithContentDescription("Edit")[1].performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Belajar Jetpack Compose"))
            .performTextReplacement("Tidak Disimpan")
        composeRule.onNodeWithContentDescription("Batal").performClick()
        composeRule.onNodeWithText("Belajar Jetpack Compose").assertIsDisplayed()
        composeRule.onNodeWithText("Tidak Disimpan").assertDoesNotExist()

        // Blank edit title must keep previous title and note intact.
        composeRule.onAllNodesWithContentDescription("Edit")[1].performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Belajar Jetpack Compose"))
            .performTextClearance()
        composeRule.onNodeWithContentDescription("Simpan").performClick()
        composeRule.onNodeWithText("Belajar Jetpack Compose").assertIsDisplayed()
        composeRule.onNodeWithText("Mempelajari Compose dan Room Database.").assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription("Hapus")[0].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Jumlah catatan: 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tugas PPPB").assertDoesNotExist()
        composeRule.onNodeWithText("Belajar Jetpack Compose").assertIsDisplayed()
    }
}
