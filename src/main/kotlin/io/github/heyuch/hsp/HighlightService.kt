package io.github.heyuch.hsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.util.containers.stream
import com.intellij.util.ui.GraphicsUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.event.FocusEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

@Service(Service.Level.APP)
class HighlightService : FocusChangeListener, SettingsListener, Disposable {

    private val settings = SettingsState.getInstance()

    private val editorDisposers: MutableMap<Editor, Runnable> = ConcurrentHashMap()

    private val lostFocusEditor = AtomicReference<Editor?>(null)

    private val firstRun = AtomicBoolean(true)

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

        if (firstRun.get()) {
            addOverlayToUnfocusedEditors(editor)
            firstRun.set(false)
        }

        addOverlayToLostFocusEditor(editor)
        removeOverlay(editor)

        cleanupInvisibleEditors()
    }

    override fun focusLost(editor: Editor, event: FocusEvent) {
        if (!enabled()) {
            removeAllOverlays()
            return
        }

        if (editor.editorKind != EditorKind.MAIN_EDITOR) {
            return
        }
        if (event.cause == FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER) {
            return
        }
        if (!hasSplitWindows(editor)) {
            return
        }

        lostFocusEditor.set(editor)
    }

    override fun settingsChanged() {
        refreshOverlays()

        if (!enabled()) {
            firstRun.set(true)
        }
    }

    override fun dispose() {
        settings?.unregisterListener(this)

        lostFocusEditor.set(null)

        editorDisposers.forEach { (_, disposer) -> disposer.run() }
        editorDisposers.clear()
    }

    private fun enabled(): Boolean {
        return settings?.enabled ?: false
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
            addOverlay(editor)
        }
    }

    private fun addOverlayToUnfocusedEditors(focused: Editor) {
        if (!hasSplitWindows(focused)) {
            return
        }

        val project = focused.project
        if (project == null) {
            return
        }

        val mgr = FileEditorManager.getInstance(project)
        val fileEditors = mgr.allEditors

        fileEditors.stream()
            .filter { editor -> editor is TextEditor }
            .map { editor -> editor as TextEditor }
            .map { editor -> editor.editor }
            .filter { editor -> editor != focused }
            .filter { editor -> editor.component.isShowing }
            .forEach { editor -> addOverlay(editor) }
    }

    private fun hasSplitWindows(editor: Editor): Boolean {
        val project = editor.project
        if (project == null) {
            return false
        }

        val mgr = FileEditorManager.getInstance(project)
        if (mgr is FileEditorManagerEx) {
            return mgr.hasSplitOrUndockedWindows()
        }

        return false
    }

    private fun addOverlayToLostFocusEditor(focused: Editor) {
        val lostFocused = lostFocusEditor.get()
        if (lostFocused == null) {
            return
        }

        if (lostFocused == focused) {
            lostFocusEditor.set(null)
            return
        }

        if (!hasSplitWindows(focused)) {
            lostFocusEditor.set(null)
            return
        }

        addOverlay(lostFocused)

        lostFocusEditor.set(null)
    }


    private fun addOverlay(editor: Editor) {
        removeOverlay(editor)

        if (editor.isDisposed) {
            return
        }

        val component = editor.component
        if (!component.isShowing) {
            return
        }

        val glassPane = try {
            IdeGlassPaneUtil.find(component)
        } catch (ignored: IllegalArgumentException) {
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
        return settings?.getOverlayColor() ?: SettingsState.defaultOverlayColor
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

    private fun removeOverlay(editor: Editor) {
        val disposer = editorDisposers[editor]

        if (disposer != null) {
            disposer.run()
            editorDisposers.remove(editor)
        }
    }

    private fun cleanupInvisibleEditors() {
        removeOverlaysIf { editor -> editor.isDisposed || !editor.component.isShowing }
    }

    internal class OverlayPainter(
        private var target: Component?, private val color: Color
    ) : AbstractPainter(), Disposable {

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
