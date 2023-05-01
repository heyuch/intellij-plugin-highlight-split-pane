package io.github.heyuch.hfp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.awt.Color

@State(
    name = "io.github.heyuch.hfp.AppSettingsState",
    storages = [Storage("HighlightFocusedPane.xml")]
)
class SettingsState : PersistentStateComponent<SettingsState> {

    var enabled: Boolean = true

    @OptionTag(converter = RgbaColorConverter::class)
    var overlayLightColor: Color = defaultOverlayLightColor
        set(value) {
            colorChanged = true
            field = value
        }

    @OptionTag(converter = RgbaColorConverter::class)
    var overlayDarkColor: Color = defaultOverlayDarkColor
        set(value) {
            colorChanged = true
            field = value
        }

    @Transient
    private var colorChanged = false

    @Transient
    private var overlayColor: JBColor? = null

    private val listeners: MutableList<SettingsListener> = ArrayList()

    override fun getState(): SettingsState {
        return this
    }

    override fun loadState(state: SettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getOverlayColor(): JBColor {
        if (colorChanged) {
            colorChanged = false
            overlayColor = JBColor(overlayLightColor, overlayDarkColor)
        }

        if (overlayColor == null) {
            overlayColor = JBColor(overlayLightColor, overlayDarkColor)
        }

        return overlayColor!!
    }

    fun isModified(s: SettingsState): Boolean {
        return enabled != s.enabled
                || overlayLightColor != s.overlayLightColor
                || overlayDarkColor != s.overlayDarkColor
    }

    fun accept(s: SettingsState) {
        enabled = s.enabled
        overlayLightColor = s.overlayLightColor
        overlayDarkColor = s.overlayDarkColor

        triggerSettingsChangedListeners()
    }

    fun registerListener(listener: SettingsListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: SettingsListener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener)
        }
    }

    private fun triggerSettingsChangedListeners() {
        if (listeners.isEmpty()) {
            return
        }

        for (listener in listeners) {
            listener.settingsChanged()
        }
    }

    companion object {

        val defaultOverlayLightColor: Color = ColorUtil.withAlpha(JBColor.BLACK, .20)

        val defaultOverlayDarkColor: Color = ColorUtil.withAlpha(JBColor.BLACK, .20)

        val defaultOverlayColor = JBColor(defaultOverlayLightColor, defaultOverlayDarkColor)

        fun getInstance(): SettingsState? {
            return ApplicationManager.getApplication().getService(SettingsState::class.java)
        }

    }

}
