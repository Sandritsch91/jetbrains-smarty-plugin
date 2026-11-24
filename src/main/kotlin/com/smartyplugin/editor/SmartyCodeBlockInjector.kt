package com.smartyplugin.editor

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.smartyplugin.formatter.SmartyCodeLayout
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY
import com.smartyplugin.psi.SmartyCodeText
import com.smartyplugin.psi.SmartyPsiElement
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * The content of `{registerJs}...{/registerJs}` is JavaScript and of `{registerCss}...{/registerCss}`
 * CSS (yii2-smarty blocks). The text pieces of the block body ([SmartyCodeText]), also those in all branches of
 * nested blocks like {if}, form one injected script;
 * Smarty tags between them stay Smarty. The languages are looked up by id, so IDEs without JavaScript or CSS
 * support simply get no injection.
 */
class SmartyCodeBlockInjector : MultiHostInjector {
    /**
     * Registered on the block body, the common parent of all pieces: the platform asks the ancestors of every
     * piece, so each piece finds the one injection that spans them all.
     */
    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(SmartyPsiElement::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val body = context.node ?: return
        if (body.elementType !== BLOCK_BODY) return
        val block = body.treeParent ?: return
        val languageId = BLOCK_LANGUAGES[SmartyPsiUtil.blockName(block)] ?: return
        val language = Language.findLanguageByID(languageId) ?: return

        // Smarty tags between the pieces of code print something at run time: a value ("var id = {$model->id};",
        // "width: {$w}px") or a brace ({ldelim}, {rdelim}). Stand in for them so the script parses instead of
        // showing "var id = ;" or unbalanced braces as errors. The branches of a nested {if} follow each other.
        val pieces = SmartyCodeLayout.pieces(body) { _, _ -> "" }
        if (pieces.none { it.code }) return
        registrar.startInjecting(language)
        var pending = StringBuilder() // stand-ins since the last piece of code
        var host: SmartyCodeText? = null
        var prefix: String? = null
        for (piece in pieces) {
            if (!piece.code) {
                pending.append(piece.standIn)
                continue
            }
            val previous = host
            if (previous == null) {
                prefix = pending.toString()
            } else {
                registrar.addPlace(prefix?.ifEmpty { null }, pending.toString().ifEmpty { null }, previous, TextRange.from(0, previous.textLength))
                prefix = null
            }
            pending = StringBuilder()
            host = piece.node.psi as SmartyCodeText
        }
        host?.let { registrar.addPlace(prefix?.ifEmpty { null }, pending.toString().ifEmpty { null }, it, TextRange.from(0, it.textLength)) }
        registrar.doneInjecting()
    }

    private companion object {
        val BLOCK_LANGUAGES = mapOf(
            "registerJs" to "JavaScript",
            "registerCss" to "CSS",
        )
    }
}
