package io.github.heyuch.hsp

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class SettingsConfigurable : Configurable {

    private var component: SettingsComponent? = null

    override fun createComponent(): JComponent? {
        component = SettingsComponent()
        return component!!.getPanel()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        val c = component
        if (c == null) {
            return null
        }

        return c.getPreferredFocusedComponent()
    }

    override fun isModified(): Boolean {
        val settings = SettingsState.getInstance()
        if (settings == null) {
            return false
        }

        val newSettings = getSettingsFromComponent()
        if (newSettings == null) {
            return false
        }

        return settings.isModified(newSettings)
    }

    private fun getSettingsFromComponent(): SettingsState? {
        val c = component
        if (c == null) {
            return null
        }

        val s = SettingsState()

        s.enabled = c.getEnableStatus()

        val lightColor = c.getOverlayLightColor()
        if (lightColor != null) {
            s.overlayLightColor = lightColor
        }

        val darkColor = c.getOverlayDarkColor()
        if (darkColor != null) {
            s.overlayDarkColor = darkColor
        }

        return s
    }

    override fun apply() {
        val settings = SettingsState.getInstance()
        if (settings == null) {
            return
        }

        val newSettings = getSettingsFromComponent()
        if (newSettings == null) {
            return
        }

        settings.accept(newSettings)
    }

    override fun reset() {
        val c = component
        if (c == null) {
            return
        }

        val s = SettingsState.getInstance()
        if (s == null) {
            return
        }

        c.setEnableStatus(s.enabled)
        c.setOverlayLightColor(s.overlayLightColor)
        c.setOverlayDarkColor(s.overlayDarkColor)
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Highlight Split Pane"
    }

    override fun disposeUIResources() {
        component = null
    }
}
