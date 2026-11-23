package com.smartyplugin.psi

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/** A navigation target at an offset of a file without useful PSI (.po, .conf): an entry named [label]. */
class SmartyFileLocation(private val file: PsiFile, private val offset: Int, private val label: String) : FakePsiElement() {
    override fun getParent(): PsiElement = file
    override fun getContainingFile(): PsiFile = file
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange(offset, offset)
    override fun getName(): String = label
    override fun getPresentableText(): String = label
    override fun getLocationString(): String = file.virtualFile?.path ?: file.name
    override fun getIcon(open: Boolean): Icon = AllIcons.FileTypes.Text
    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
    override fun navigate(requestFocus: Boolean) {
        val vf = file.virtualFile ?: return
        OpenFileDescriptor(file.project, vf, offset).navigate(requestFocus)
    }
}
