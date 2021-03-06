// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.codeinsight.linemarker.EditorConfigOverridingHeaderFinder
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigHeader

class EditorConfigGotoSuperHandler : GotoTargetHandler() {
  override fun getFeatureUsedKey() = GotoSuperAction.FEATURE_ID

  override fun getSourceAndTargetElements(editor: Editor, file: PsiFile): GotoData? {
    val source = findSource(editor, file) ?: return null
    val targets = findTargets(source)
    return GotoData(source, targets.toTypedArray(), emptyList())
  }

  override fun getChooserTitle(sourceElement: PsiElement, name: String?, length: Int, finished: Boolean) = when (sourceElement) {
    is EditorConfigHeader -> "Please, select supercase header"
    is EditorConfigFlatOptionKey -> "Please, select overridden option"
    else -> "Please, select parent"
  }

  override fun getNotFoundMessage(project: Project, editor: Editor, file: PsiFile) = when (findSource(editor, file)) {
    is EditorConfigHeader -> "No supercase header found"
    is EditorConfigFlatOptionKey -> "No overridden option found"
    else -> "No parent found"
  }

  private companion object {
    private fun findSource(editor: Editor, file: PsiFile): PsiElement? {
      val element = file.findElementAt(editor.caretModel.offset) ?: return null
      return PsiTreeUtil.getParentOfType(
        element,
        EditorConfigHeader::class.java,
        EditorConfigFlatOptionKey::class.java
      )
    }

    private fun findTargets(element: PsiElement) = when (element) {
      is EditorConfigHeader -> EditorConfigOverridingHeaderFinder().getMatchingHeaders(element)
      is EditorConfigFlatOptionKey -> element.reference.findParents()
      else -> emptyList()
    }
  }
}
