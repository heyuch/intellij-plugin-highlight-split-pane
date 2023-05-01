package io.github.heyuch.hfp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.util.containers.stream
import com.intellij.util.ui.GraphicsUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.event.FocusEvent
import java.util.function.Predicate

@Service(Service.Level.APP)
class HighlightService : FocusChangeListener, SettingsListener, Disposable {

    private var settings = SettingsState.getInstance()


    private val editorDisposers: MutableMap<Editor, Runnable> = HashMap()

    private var lostFocusEditor: Editor? = null

    private var firstRun = true

    fun init() {
        val editorFactory = EditorFactory.getInstance()
        val multicaster = editorFactory.eventMulticaster

        if (multicaster is EditorEventMulticasterEx) {
            multicaster.addFocusChangeListener(this, this)
        }

        settings?.registerListener(this)
    }

    override fun focusGained(editor: Editor, event: FocusEvent) {
        if (!enabled()) {
            removeAllOverlays()
            return
        }

        if (editor.editorKind != EditorKind.MAIN_EDITOR) {
            return
        }

        if (firstRun) {
            addOverlayToUnfocusedEditors(editor)
            firstRun = false
        }

        // Editor lost focus, the next focusing target may be the project view,
        // the tool window or user swapped the app... In such scenario we should
        // not add overlay to the editor until we can be sure that the newly
        // focused is another editor.
        if (lostFocusEditor != null) {
            if (lostFocusEditor != editor) {
                addOverlay(lostFocusEditor!!)
            }
            lostFocusEditor = null
        }

        removeOverlay(editor)

        // Editor close would not trigger focusLost event, we need check the
        // cached editorDisposers to manually to prevent memory leak.
        cleanupDisposedEditors()
    }

    override fun focusLost(editor: Editor, event: FocusEvent) {
        if (!enabled()) {
            removeAllOverlays()
            return
        }

        if (editor.editorKind != EditorKind.MAIN_EDITOR) {
            return
        }

        lostFocusEditor = editor
    }

    override fun settingsChanged() {
        // The settings takes effect immediately, so that users can view the
        // effect in real time.
        refreshOverlays()
    }

    override fun dispose() {
        settings?.unregisterListener(this)

        editorDisposers.forEach { (_, disposer) -> disposer.run() }
        editorDisposers.clear()
    }

    private fun enabled(): Boolean {
        val s = settings
        if (s == null) {
            return false
        }

        return s.enabled
    }

    private fun refreshOverlays() {
        if (editorDisposers.isEmpty()) {
            return
        }

        if (!enabled()) {
            removeAllOverlays()
            return
        }

        val editors = ArrayList(editorDisposers.keys)

        for (editor in editors) {
            removeOverlay(editor)
            addOverlay(editor)
        }
    }

    private fun removeAllOverlays() {
        removeOverlaysIf { _ -> true }
    }

    private fun removeOverlaysIf(predicate: Predicate<Editor>) {
        if (editorDisposers.isEmpty()) {
            return
        }

        val iter = editorDisposers.iterator()

        while (iter.hasNext()) {
            val (editor, disposer) = iter.next()

            if (predicate.test(editor)) {
                disposer.run()
                iter.remove()
            }
        }
    }

    private fun addOverlayToUnfocusedEditors(focused: Editor) {
        val project = focused.project
        if (project == null) {
            return
        }

        val fileEditorManager = FileEditorManager.getInstance(project)
        val fileEditors = fileEditorManager.allEditors

        fileEditors.stream()
            .filter { editor -> editor is TextEditor }
            .map { editor -> editor as TextEditor }
            .map { editor -> editor.editor }
            .filter { editor -> editor != focused }
            .forEach { editor -> addOverlay(editor) }
    }

    private fun addOverlay(editor: Editor) {
        removeOverlay(editor)

        val component = editor.component

        val glassPane = try {
            IdeGlassPaneUtil.find(component)
        } catch (ignored: IllegalArgumentException) {
            // Sometimes the invisible editors may be passed in as argument,
            // (eg. addOverlayToUnfocusedEditorsAtFirstRun()) which will cause
            // this exception. It's ok, we just ignore it and return.
            return
        }

        val color = getOverlayColor()
        val painter = OverlayPainter(component, color)
        glassPane.addPainter(component, painter, painter)

        component.repaint()

        editorDisposers[editor] = Runnable {
            Disposer.dispose(painter)
            component.repaint()
        }
    }

    private fun getOverlayColor(): Color {
        val s = settings
        if (s == null) {
            return SettingsState.defaultOverlayColor
        }

        return s.getOverlayColor()
    }

    private fun removeOverlay(editor: Editor) {
        val disposer = editorDisposers[editor]

        if (disposer != null) {
            disposer.run()
            editorDisposers.remove(editor)
        }
    }

    private fun cleanupDisposedEditors() {
        removeOverlaysIf { editor -> editor.isDisposed }
    }

    internal class OverlayPainter(private var target: Component?, private val color: Color) : AbstractPainter(),
        Disposable {

        override fun executePaint(component: Component?, g: Graphics2D?) {
            if (component == null || g == null || target == null) {
                return
            }

            GraphicsUtil.setupAAPainting(g)

            g.color = color
            g.fill(component.bounds)
        }

        override fun needsRepaint(): Boolean {
            return target != null
        }

        override fun dispose() {
            target = null
        }

    }

}
