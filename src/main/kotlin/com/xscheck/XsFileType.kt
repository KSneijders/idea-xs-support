package com.xscheck

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object XsFileType : LanguageFileType(XsLanguage) {
    override fun getName() = "XS Script"
    override fun getDescription() = "AoE2:DE XS Script file"
    override fun getDefaultExtension() = "xs"
    override fun getIcon(): Icon? = null
}
