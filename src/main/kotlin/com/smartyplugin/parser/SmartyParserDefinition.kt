package com.smartyplugin.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lexer.SmartyLexer
import com.smartyplugin.lexer.SmartyTokenTypes
import com.smartyplugin.psi.SmartyCodeText
import com.smartyplugin.psi.SmartyFile
import com.smartyplugin.psi.SmartyPsiElement
import com.smartyplugin.psi.SmartyStringLiteral

class SmartyParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = SmartyLexer(SmartyDelimiters.of(project))

    override fun createParser(project: Project?): PsiParser = SmartyParser(SmartyDelimiters.of(project))

    override fun getFileNodeType(): IFileElementType = SmartyElementTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = SmartyTokenTypes.WHITESPACES

    override fun getCommentTokens(): TokenSet = SmartyTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = SmartyTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        SmartyElementTypes.STRING_LITERAL -> SmartyStringLiteral(node)
        SmartyElementTypes.CODE_TEXT -> SmartyCodeText(node)
        else -> SmartyPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = SmartyFile(viewProvider)
}
