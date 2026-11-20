package com.smartyplugin.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.smartyplugin.lexer.SmartyTokenTypes

class SmartyQuoteHandler : SimpleTokenSetQuoteHandler(SmartyTokenTypes.STRING)
