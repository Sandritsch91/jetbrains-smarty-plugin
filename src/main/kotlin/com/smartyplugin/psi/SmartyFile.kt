package com.smartyplugin.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.smartyplugin.lang.SmartyFileType
import com.smartyplugin.lang.SmartyLanguage

class SmartyFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, SmartyLanguage) {
    override fun getFileType(): FileType = SmartyFileType

    override fun toString(): String = "Smarty file"
}
