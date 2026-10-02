package com.example.smartmoney

import android.content.Context
import com.example.smartmoney.data.local.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UserProfileManagerTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        UserProfileManager.resetForTesting()
        tempDir = Files.createTempDirectory("smartmoney_profile_test").toFile()
    }

    @Test
    fun initialProfileBitmap_isNull() {
        assertNull(UserProfileManager.profileBitmap.value)
    }

    @Test
    fun removeProfilePicture_whenFileDoesNotExist_returnsTrueAndClearsState() = runBlocking {
        // Create an empty mock-like temporary directory structure
        val emptyDir = File(tempDir, "files").apply { mkdirs() }
        
        // Use a test context wrapper
        val result = UserProfileManager.removeProfilePicture(
            context = TestContext(emptyDir),
            dispatcher = Dispatchers.Unconfined
        )

        assertTrue(result)
        assertNull(UserProfileManager.profileBitmap.value)
    }

    @Test
    fun initialize_whenFileDoesNotExist_keepsProfileBitmapNull() = runBlocking {
        val emptyDir = File(tempDir, "files").apply { mkdirs() }
        
        UserProfileManager.initialize(
            context = TestContext(emptyDir),
            dispatcher = Dispatchers.Unconfined
        )

        assertNull(UserProfileManager.profileBitmap.value)
    }

    /**
     * Minimal Context implementation providing only filesDir for unit testing.
     */
    private class TestContext(private val baseDir: File) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
    }
}
