package com.smartyplugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.smartyplugin.lexer.SmartyLexer;
import com.smartyplugin.lexer.SmartyTokenTypes;
import com.smartyplugin.psi.SmartyFile;
import com.smartyplugin.psi.SmartyPsiElement;
import com.smartyplugin.psi.SmartyStringLiteral;
import org.jetbrains.annotations.NotNull;

public final class SmartyParserDefinition implements ParserDefinition {
    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new SmartyLexer(com.smartyplugin.lang.SmartyDelimiters.of(project));
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new SmartyParser(com.smartyplugin.lang.SmartyDelimiters.of(project));
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return SmartyElementTypes.FILE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return SmartyTokenTypes.WHITESPACES;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return SmartyTokenTypes.COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return SmartyTokenTypes.STRINGS;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        if (node.getElementType() == SmartyElementTypes.STRING_LITERAL) return new SmartyStringLiteral(node);
        if (node.getElementType() == SmartyElementTypes.CODE_TEXT) return new com.smartyplugin.psi.SmartyCodeText(node);
        return new SmartyPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new SmartyFile(viewProvider);
    }
}
