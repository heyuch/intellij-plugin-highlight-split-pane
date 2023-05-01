package io.github.heyuch.hfp

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager

class RegisterListener : AppLifecycleListener {

    override fun appStarted() {
        val app = ApplicationManager.getApplication()
        val highlightService = app.getService(HighlightService::class.java)

        highlightService.init()
    }

}
