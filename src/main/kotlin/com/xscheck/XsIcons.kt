package com.xscheck

import com.intellij.openapi.util.IconLoader

object XsIcons {
    // IconLoader picks up xs_dark.svg automatically under dark themes.
    @JvmField
    val FILE = IconLoader.getIcon("/icons/xs.svg", XsIcons::class.java)
}
