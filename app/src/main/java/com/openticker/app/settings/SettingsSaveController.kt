package com.openticker.app.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Main-thread editor save gate. A failed write leaves the caller's draft untouched. */
class SettingsSaveController(
    private val scope: CoroutineScope,
    private val persist: suspend (AppSettings) -> Unit,
    private val onSaving: (Boolean) -> Unit,
    private val onError: (String?) -> Unit
) {
    private var saving = false

    fun save(settings: AppSettings, onSaved: () -> Unit) {
        // Set before launching: repeated clicks cannot enqueue a second write.
        if (saving) return
        saving = true
        onSaving(true)
        onError(null)
        scope.launch {
            val succeeded = try {
                persist(settings)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onError("Could not save settings. Your changes are still here. Please try again.")
                false
            } finally {
                saving = false
                onSaving(false)
            }
            if (succeeded) onSaved()
        }
    }
}
