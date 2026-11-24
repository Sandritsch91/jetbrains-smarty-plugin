package com.smartyplugin.editor

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY
import com.smartyplugin.parser.SmartyElementTypes.CODE_TEXT
import com.smartyplugin.psi.SmartyCodeText
import com.smartyplugin.psi.SmartyPsiElement
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * The content of `{registerJs}...{/registerJs}` is JavaScript and of `{registerCss}...{/registerCss}`
 * CSS (yii2-smarty blocks). The text pieces of the block body ([SmartyCodeText]) form one injected script;
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

        val pieces = generateSequence(body.firstChildNode) { it.treeNext }
            .filter { it.elementType === CODE_TEXT }
            .mapNotNull { it.psi as? SmartyCodeText }
            .toList()
        if (pieces.isEmpty()) return
        registrar.startInjecting(language)
        for ((i, piece) in pieces.withIndex()) {
            // Smarty tags between the pieces print something at run time: a value ("var id = {$model->id};",
            // "width: {$w}px") or a brace ({ldelim}, {rdelim}). Stand in for them so the script parses instead of
            // showing "var id = ;" or unbalanced braces as errors.
            val prefix = if (i == 0) standIns(generateSequence(piece.node.treePrev) { it.treePrev }).reversed() else ""
            val suffix = standIns(generateSequence(piece.node.treeNext) { it.treeNext })
            registrar.addPlace(prefix.ifEmpty { null }, suffix.ifEmpty { null }, piece, TextRange.from(0, piece.textLength))
        }
        registrar.doneInjecting()
    }

    /** Stand-ins of the tags up to the next piece of code, in the order given. */
    private fun standIns(siblings: Sequence<ASTNode>): String =
        siblings.takeWhile { it.elementType !== CODE_TEXT }
            .filter { it.text.isNotBlank() }
            .joinToString("") { SmartyPsiUtil.codeStandIn(it) }

    private companion object {
        val BLOCK_LANGUAGES = mapOf(
            "registerJs" to "JavaScript",
            "registerCss" to "CSS",
        )
    }
}
