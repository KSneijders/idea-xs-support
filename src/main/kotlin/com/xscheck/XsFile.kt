package com.xscheck

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

/** PSI file for the XS language, backed by the flat parse in [XsParser]. */
class XsFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, XsLanguage) {
    override fun getFileType(): FileType = XsFileType
    override fun toString(): String = "XS file"
}
