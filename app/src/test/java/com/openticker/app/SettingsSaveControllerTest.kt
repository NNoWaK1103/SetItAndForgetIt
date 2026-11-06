package com.openticker.app

import com.openticker.app.settings.AppSettings
import com.openticker.app.settings.SettingsSaveController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSaveControllerTest {
    @Test fun `success closes only after persistence and repeated clicks do not duplicate writes`() = runTest {
        val completed = CompletableDeferred<Unit>()
        var writes = 0
        var closed = 0
        var saving = false
        val controller = SettingsSaveController(backgroundScope, { writes++; completed.await() }, { saving=it }, {})
        controller.save(AppSettings()) { closed++ }
        controller.save(AppSettings()) { closed++ }
        assertTrue(saving)
        runCurrent()
        assertEquals(1,writes)
        assertEquals(0,closed)
        completed.complete(Unit)
        runCurrent()
        assertFalse(saving)
        assertEquals(1,closed)
    }

    @Test fun `failure retains submitted values and permits retry without dismissing`() = runTest {
        var fail = true
        var saving = false
        var error: String? = null
        var closed = false
        val writes = mutableListOf<AppSettings>()
        val controller = SettingsSaveController(backgroundScope, {
            writes += it
            if(fail) throw IOException("disk full")
        }, { saving=it }, { error=it })
        val draft = AppSettings(celsius=true,slideshowIntervalSeconds=30)
        controller.save(draft) { closed=true }
        runCurrent()
        assertFalse(saving)
        assertFalse(closed)
        assertNotNull(error)
        fail=false
        controller.save(draft) { closed=true }
        assertNull(error)
        runCurrent()
        assertTrue(closed)
        assertEquals(listOf(draft,draft),writes)
    }

    @Test fun `cancelled write does not report success and releases save gate`() = runTest {
        var saving = false
        var closed = false
        var error: String? = null
        val controller = SettingsSaveController(backgroundScope, { throw CancellationException() },
            { saving=it }, { error=it })
        controller.save(AppSettings()) { closed=true }
        runCurrent()
        assertFalse(saving)
        assertFalse(closed)
        assertNull(error)
    }

}
