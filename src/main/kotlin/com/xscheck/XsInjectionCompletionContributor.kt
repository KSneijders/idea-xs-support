package com.xscheck

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager

/**
 * Provides completion for XS **injected** into other files, by proxying to an isolated
 * xs-check server. Real `.xs` files are handled by the LSP4IJ-managed server, so this
 * contributor bails on anything that isn't an injected fragment (to avoid duplicates).
 */
class XsInjectionCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val project = file.project
        if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) return

        val items = XsInjectionServer.getInstance(project)
            .completion(file.text, offsetToPosition(file.text, parameters.offset))

        for (item in items) {
            var element = LookupElementBuilder.create(item.label)
            item.detail?.let { element = element.withTypeText(it) }
            result.addElement(element)
        }
    }
}
