package io.github.heyuch.hfp.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import io.github.heyuch.hfp.services.HighlightFocusedPaneService

class HighlightFocusedPaneListener : AppLifecycleListener {

    override fun appStarted() {
        val app = ApplicationManager.getApplication()
        val service = app.getService(HighlightFocusedPaneService::class.java)

        service.init()
    }

}
