package io.github.heyuch.hsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.util.containers.stream
import com.intellij.util.ui.GraphicsUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.event.FocusEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

@Service(Service.Level.APP)
class HighlightService : FocusChangeListener, SettingsListener, Disposable {

    private val settings = SettingsState.getInstance()

    private val overlayedEditors: MutableMap<Editor, OverlayDisposer> = ConcurrentHashMap()

    private val focusedEditor = AtomicReference<Editor?>(null)

    fun init() {
        val editorFactory = EditorFactory.getInstance()
        val multicaster = editorFactory.eventMulticaster

        if (multicaster is EditorEventMulticasterEx) {
            multicaster.addFocusChangeListener(this, this)
        }

        settings?.registerListener(this)
    }

    override fun focusGained(editor: Editor, event: FocusEvent) {
        if (shouldDeactivate(editor.project)) {
            removeAllOverlays()
            return
        }

        if (shouldIgnore(editor, event)) {
            return
        }

        focusedEditor.set(editor)

        addOverlayToUnfocusedEditors(editor.project)
        removeOverlay(editor)

        removeInvisibleOverlayedEditors()
    }

    private fun shouldDeactivate(project: Project?): Boolean {
        return !enabled() || !isSplitWindows(project)
    }

    private fun shouldIgnore(editor: Editor, event: FocusEvent): Boolean {
        if (editor.editorKind != EditorKind.MAIN_EDITOR) {
            return true
        }

        if (event.cause == FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER) {
            return true
        }

        return false
    }

    override fun focusLost(editor: Editor, event: FocusEvent) {
        if (shouldDeactivate(editor.project)) {
            removeAllOverlays()
        }

        if (shouldIgnore(editor, event)) {
            return
        }

        if (editor.isDisposed) {
            removeOverlay(editor)
            addOverlayToUnfocusedEditors(editor.project)
        }
    }

    override fun settingsChanged() {
        refreshOverlays()
    }

    override fun dispose() {
        settings?.unregisterListener(this)
        focusedEditor.set(null)

        removeAllOverlays()
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

    private fun addOverlayToUnfocusedEditors(project: Project?) {
        if (project == null) {
            return
        }

        val mgr = FileEditorManager.getInstance(project)
        val editors = mgr.selectedEditors

        editors
            .stream()
            .filter { editor -> editor is TextEditor }
            .map { editor -> editor as TextEditor }
            .map { editor -> editor.editor }
            .filter { editor -> !isFocused(editor) }
            .forEach { editor -> addOverlay(editor) }
    }

    private fun isFocused(editor: Editor): Boolean {
        val focused = focusedEditor.get()
        if (editor == focused) {
            return true
        }

        if (editor.component.isFocusOwner) {
            return true
        }

        return false
    }

    private fun isSplitWindows(project: Project?): Boolean {
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
        if (hasOverlay(editor) || isEditorInvisible(editor)) {
            return
        }

        val component = editor.component

        val painter = setOverlayPainter(component)
        if (painter == null) {
            return
        }

        component.repaint()

        overlayedEditors[editor] = OverlayDisposer(component, painter)
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

        val editors = ArrayList(overlayedEditors.keys)

        for (editor in editors) {
            if (predicate.test(editor)) {
                removeOverlay(editor)
            }
        }
    }

    private fun removeOverlay(editor: Editor) {
        val disposer = overlayedEditors[editor]

        if (disposer != null) {
            disposer.run()
            overlayedEditors.remove(editor)
        }
    }

    private fun removeInvisibleOverlayedEditors() {
        removeOverlaysIf(::isEditorInvisible)
    }

    private fun isEditorInvisible(editor: Editor): Boolean {
        return editor.isDisposed || !editor.component.isShowing
    }

    fun onFileClosed(project: Project, file: VirtualFile) {
        removeOverlaysIf { editor ->
            if (editor is EditorEx) {
                editor.virtualFile == file
            }
            false
        }

        if (shouldDeactivate(project)) {
            return
        }

        addOverlayToUnfocusedEditors(project)
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

    internal class OverlayDisposer(
        private val component: Component,
        private val disposable: Disposable,
    ) : Runnable {

        override fun run() {
            Disposer.dispose(disposable)
            component.repaint()
        }
    }
}
