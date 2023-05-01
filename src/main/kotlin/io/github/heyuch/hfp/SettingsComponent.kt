package io.github.heyuch.hfp

import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel


class SettingsComponent {

    private var panel: JPanel? = null
    private val enableStatus = JBCheckBox("Enable")
    private val overlayLightColor = ColorPanel()
    private val overlayDarkColor = ColorPanel()

    init {
        overlayLightColor.setSupportTransparency(true)
        overlayDarkColor.setSupportTransparency(true)

        panel = FormBuilder.createFormBuilder()
            .addComponent(enableStatus, 1)
            .addLabeledComponent(JBLabel("Overlay color(light): "), overlayLightColor, 1, false)
            .addLabeledComponent(JBLabel("Overlay color(dark): "), overlayDarkColor, 1, false)
            .panel
    }

    fun getPanel(): JPanel? {
        return panel
    }

    fun getPreferredFocusedComponent(): JComponent {
        return enableStatus
    }

    fun getEnableStatus(): Boolean {
        return enableStatus.isSelected
    }

    fun setEnableStatus(enabled: Boolean) {
        enableStatus.isSelected = enabled
    }

    fun getOverlayLightColor(): Color? {
        return overlayLightColor.selectedColor
    }

    fun setOverlayLightColor(color: Color) {
        overlayLightColor.selectedColor = color
    }

    fun getOverlayDarkColor(): Color? {
        return overlayDarkColor.selectedColor
    }

    fun setOverlayDarkColor(color: Color) {
        overlayDarkColor.selectedColor = color
    }

}
