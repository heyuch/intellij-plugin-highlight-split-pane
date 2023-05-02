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

    private val overlayedEditors: MutableMap<Editor, Runnable> = ConcurrentHashMap()

    private val lostFocusEditorHolder = AtomicReference<Editor?>(null)

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
        if (!enabled() || !isSplitWindows(editor)) {
            removeAllOverlays()
            return
        }

        if (editor.editorKind != EditorKind.MAIN_EDITOR) {
            return
        }

        if (firstRun.get()) {
            firstRun.set(false)
            addOverlayToUnfocusedEditors(editor)
        }

        val lostFocusEditor = lostFocusEditorHolder.get()
        if (lostFocusEditor != null) {
            lostFocusEditorHolder.set(null)

            if (lostFocusEditor != editor) {
                addOverlay(lostFocusEditor)
            }
        }

        removeOverlay(editor)

        cleanupInvisibleOverlayedEditors()
    }

    override fun focusLost(editor: Editor, event: FocusEvent) {
        if (!enabled() || !isSplitWindows(editor)) {
            removeAllOverlays()
            return
        }

        if (editor.editorKind != EditorKind.MAIN_EDITOR) {
            return
        }
        if (event.cause == FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER) {
            return
        }

        lostFocusEditorHolder.set(editor)
    }

    override fun settingsChanged() {
        refreshOverlays()

        // Reset firstRun state when users disabled plugin, so when plugin enabled
        // later, we can add overlays to all unfocused editors.
        if (!enabled()) {
            firstRun.set(true)
        }
    }

    override fun dispose() {
        settings?.unregisterListener(this)

        lostFocusEditorHolder.set(null)

        overlayedEditors.forEach { (_, overlayDisposer) -> overlayDisposer.run() }
        overlayedEditors.clear()
    }

    private fun enabled(): Boolean {
        return settings?.enabled ?: false
    }

    private fun refreshOverlays() {
        if (overlayedEditors.isEmpty()) {
            return
        }

        if (!enabled()) {
            removeAllOverlays()
            return
        }

        val editors = ArrayList(overlayedEditors.keys)

        for (editor in editors) {
            removeOverlay(editor)
            addOverlay(editor)
        }
    }

    private fun addOverlayToUnfocusedEditors(focused: Editor) {
        val project = focused.project
        if (project == null) {
            return
        }

        val fileEditorMgr = FileEditorManager.getInstance(project)
        val fileEditors = fileEditorMgr.allEditors

        fileEditors
            .stream()
            .filter { editor -> editor is TextEditor }
            .map { editor -> editor as TextEditor }
            .map { editor -> editor.editor }
            .filter { editor -> editor != focused }
            .filter { editor -> editor.component.isShowing }
            .forEach { editor -> addOverlay(editor) }
    }

    private fun isSplitWindows(editor: Editor): Boolean {
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

    private fun addOverlay(editor: Editor) {
        if (hasOverlay(editor) || editor.isDisposed || !editor.component.isShowing) {
            return
        }

        val component = editor.component
        val disposable = setOverlayPainter(component)
        if (disposable == null) {
            return
        }

        component.repaint()

        overlayedEditors[editor] = Runnable {
            Disposer.dispose(disposable)
            component.repaint()
        }
    }

    private fun setOverlayPainter(component: Component): Disposable? {
        val glassPane =
            try {
                IdeGlassPaneUtil.find(component)
            } catch (ignored: IllegalArgumentException) {
                return null
            }

        val color = getOverlayColor()
        val painter = OverlayPainter(component, color)

        glassPane.addPainter(component, painter, painter)

        return painter
    }

    private fun hasOverlay(editor: Editor): Boolean {
        return overlayedEditors.containsKey(editor)
    }

    private fun getOverlayColor(): Color {
        return settings?.getOverlayColor() ?: SettingsState.defaultOverlayColor
    }

    private fun removeAllOverlays() {
        removeOverlaysIf { _ -> true }
    }

    private fun removeOverlaysIf(predicate: Predicate<Editor>) {
        if (overlayedEditors.isEmpty()) {
            return
        }

        val iter = overlayedEditors.iterator()

        while (iter.hasNext()) {
            val (editor, overlayDisposer) = iter.next()

            if (predicate.test(editor)) {
                overlayDisposer.run()
                iter.remove()
            }
        }
    }

    private fun removeOverlay(editor: Editor) {
        val overlayDisposer = overlayedEditors[editor]

        if (overlayDisposer != null) {
            overlayDisposer.run()
            overlayedEditors.remove(editor)
        }
    }

    private fun cleanupInvisibleOverlayedEditors() {
        removeOverlaysIf { editor -> editor.isDisposed || !editor.component.isShowing }
    }

    internal class OverlayPainter(private var target: Component?, private val color: Color) :
        AbstractPainter(), Disposable {

        override fun executePaint(component: Component?, g: Graphics2D?) {
            if (component == null || g == null) {
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
