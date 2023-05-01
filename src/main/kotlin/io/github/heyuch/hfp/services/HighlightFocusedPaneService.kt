package io.github.heyuch.hfp.services

import com.intellij.diagnostic.ActivityImpl.listener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.event.FocusEvent

@Service(Service.Level.APP)
class HighlightFocusedPaneService : FocusChangeListener, Disposable {

    private val overlayColor = JBUI.CurrentTheme.DefaultTabs.inactiveColoredTabBackground()

    private val editorDisposers: MutableMap<Editor, Runnable> = HashMap()

    private var lostFocusEditor: Editor? = null

    private var firstRun = true

    fun init() {
        val multicaster = EditorFactory.getInstance().eventMulticaster

        if (multicaster is EditorEventMulticasterEx) {
            multicaster.addFocusChangeListener(this, this)
        }
    }

    override fun focusGained(editor: Editor, event: FocusEvent) {
        if (firstRun) {
            addOverlayToUnfocusedEditors(editor)
            firstRun = false
        }

        // Editor lost focus, the next focused target may be the project view,
        // the tool window or user swapped the app... In such scenario we should
        // not add overlay to the editor until we can be sure that the newly
        // focused is another editor.
        if (lostFocusEditor != null) {
            addOverlayToLostFocusEditor(lostFocusEditor, editor)
            lostFocusEditor = null
        }

        removeOverlay(editor)

        // Editor close would not trigger focusLost event, we need check the
        // cached editorDisposers to manually to prevent memory leak.
        cleanupDisposedEditors()
    }

    override fun focusLost(editor: Editor, event: FocusEvent) {
        lostFocusEditor = editor
    }

    override fun dispose() {
        listener = null

        editorDisposers.forEach { (_, disposer) -> disposer.run() }
        editorDisposers.clear()
    }

    private fun addOverlayToUnfocusedEditors(focused: Editor) {
        ClientEditorManager.getCurrentInstance()
            .editors()
            .filter { editor -> editor != focused }
            .forEach { editor -> addOverlay(editor) }
    }

    private fun addOverlayToLostFocusEditor(lostFocused: Editor?, focused: Editor) {
        if (lostFocused == null) {
            return
        }

        if (lostFocused != focused) {
            addOverlay(lostFocused)
        }
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

        val painter = OverlayPainter(component, overlayColor)
        glassPane.addPainter(component, painter, painter)

        component.repaint()

        editorDisposers[editor] = Runnable {
            Disposer.dispose(painter)
            component.repaint()
        }
    }

    private fun removeOverlay(editor: Editor) {
        val disposer = editorDisposers[editor]

        if (disposer != null) {
            disposer.run()
            editorDisposers.remove(editor)
        }
    }

    private fun cleanupDisposedEditors() {
        val iter = editorDisposers.iterator()

        while (iter.hasNext()) {
            val (editor, disposer) = iter.next()

            if (editor.isDisposed) {
                disposer.run()
                iter.remove()
            }
        }
    }


    class OverlayPainter(private var target: Component?, private val color: Color) : AbstractPainter(), Disposable {

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
