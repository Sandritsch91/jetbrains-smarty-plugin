package com.smartyplugin.parser

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenType

object SmartyElementTypes {
    @JvmField val FILE: IFileElementType = IFileElementType("SMARTY_FILE", SmartyLanguage)

    /** {if ...} ... {elseif} ... {else} ... {/if} */
    @JvmField val BLOCK: IElementType = SmartyTokenType("BLOCK")

    /** Content between the tags of a block. */
    @JvmField val BLOCK_BODY: IElementType = SmartyTokenType("BLOCK_BODY")
    @JvmField val OPEN_TAG: IElementType = SmartyTokenType("OPEN_TAG")
    @JvmField val MIDDLE_TAG: IElementType = SmartyTokenType("MIDDLE_TAG")
    @JvmField val CLOSE_TAG: IElementType = SmartyTokenType("CLOSE_TAG")

    /** A function tag without a body: {include file="x.tpl"} */
    @JvmField val TAG: IElementType = SmartyTokenType("TAG")

    /** Output tag: {$var|escape} */
    @JvmField val PRINT_TAG: IElementType = SmartyTokenType("PRINT_TAG")
    @JvmField val ATTRIBUTE: IElementType = SmartyTokenType("ATTRIBUTE")
    @JvmField val MODIFIER: IElementType = SmartyTokenType("MODIFIER")
    @JvmField val STRING_LITERAL: IElementType = SmartyTokenType("STRING_LITERAL")

    /** Text inside {registerJs} / {registerCss}: hosts the injected JavaScript / CSS. */
    @JvmField val CODE_TEXT: IElementType = SmartyTokenType("CODE_TEXT")

    @JvmField val TAGS: TokenSet = TokenSet.create(OPEN_TAG, MIDDLE_TAG, CLOSE_TAG, TAG, PRINT_TAG)
    @JvmField val BLOCK_PARTS: TokenSet = TokenSet.create(OPEN_TAG, MIDDLE_TAG, CLOSE_TAG)
}
