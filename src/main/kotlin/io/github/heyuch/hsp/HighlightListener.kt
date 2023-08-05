package io.github.heyuch.hsp

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFrame
import java.util.concurrent.atomic.AtomicBoolean

class HighlightListener : ApplicationActivationListener, FileEditorManagerListener {

    private var initialized = AtomicBoolean(false)

    override fun applicationActivated(ideFrame: IdeFrame) {
        if (initialized.get()) {
            return
        }

        val highlightService = getHighlightService()
        highlightService.init()

        initialized.set(true)
    }

    private fun getHighlightService(): HighlightService {
        val app = ApplicationManager.getApplication()
        return app.getService(HighlightService::class.java)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (initialized.get()) {
            return
        }

        val highlightService = getHighlightService()

        highlightService.onFileClosed(source.project, file)
    }
}
