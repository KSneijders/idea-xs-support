package com.xscheck

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.redhat.devtools.lsp4ij.LanguageServerManager
import javax.swing.JComponent
import javax.swing.JPanel

class XsSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var extraPreludeField: TextFieldWithBrowseButton
    private lateinit var includeDirectoriesArea: JBTextArea
    private lateinit var ignoredWarningsArea: JBTextArea

    override fun getDisplayName() = "XS Check"

    override fun createComponent(): JComponent {
        extraPreludeField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Extra Prelude File", null, project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("xs")
            )
        }
        includeDirectoriesArea = JBTextArea(6, 50).apply { lineWrap = false }
        ignoredWarningsArea   = JBTextArea(3, 50).apply { lineWrap = false }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Extra prelude path:", extraPreludeField)
            .addSeparator()
            .addLabeledComponent("Include directories (one per line):", JBScrollPane(includeDirectoriesArea))
            .addSeparator()
            .addLabeledComponent("Ignored warnings (one per line):", JBScrollPane(ignoredWarningsArea))
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { reset() }
    }

    override fun isModified(): Boolean {
        val s = XsSettings.getInstance(project).state
        return extraPreludeField.text != s.extraPreludePath
            || parseLines(includeDirectoriesArea.text) != s.includeDirectories
            || parseLines(ignoredWarningsArea.text) != s.ignores
    }

    override fun apply() {
        val s = XsSettings.getInstance(project).state
        s.extraPreludePath    = extraPreludeField.text.trim()
        s.includeDirectories  = parseLines(includeDirectoriesArea.text)
        s.ignores             = parseLines(ignoredWarningsArea.text)

        // Restart the server so it re-requests configuration (workspace/configuration,
        // answered by XsLanguageClient) and rebuilds its prelude with the new settings.
        LanguageServerManager.getInstance(project).stop("com.xscheck.server")
        LanguageServerManager.getInstance(project).start("com.xscheck.server")
    }

    override fun reset() {
        val s = XsSettings.getInstance(project).state
        extraPreludeField.text     = s.extraPreludePath
        includeDirectoriesArea.text = s.includeDirectories.joinToString("\n")
        ignoredWarningsArea.text   = s.ignores.joinToString("\n")
    }

    private fun parseLines(text: String): MutableList<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
}
