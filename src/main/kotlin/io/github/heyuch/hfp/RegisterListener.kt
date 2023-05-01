package io.github.heyuch.hfp

import com.intellij.ide.FrameStateListener
import com.intellij.openapi.application.ApplicationManager

class RegisterListener : FrameStateListener {

    private var initialized = false

    override fun onFrameActivated() {
        if (initialized) {
            return
        }

        val app = ApplicationManager.getApplication()
        val highlightService = app.getService(HighlightService::class.java)

        highlightService.init()

        initialized = true
    }

}
