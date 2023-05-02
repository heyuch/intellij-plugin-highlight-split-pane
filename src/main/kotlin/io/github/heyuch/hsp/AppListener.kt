package io.github.heyuch.hsp

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFrame

class AppListener : ApplicationActivationListener {

    private var initialized = false

    override fun applicationActivated(ideFrame: IdeFrame) {
        if (initialized) {
            return
        }

        val app = ApplicationManager.getApplication()
        val highlightService = app.getService(HighlightService::class.java)

        highlightService.init()

        initialized = true
    }
}
