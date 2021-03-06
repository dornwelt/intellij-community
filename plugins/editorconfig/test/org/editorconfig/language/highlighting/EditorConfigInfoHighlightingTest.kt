// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.highlighting

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

class EditorConfigInfoHighlightingTest : LightPlatformCodeInsightFixtureTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testSrc/org/editorconfig/language/highlighting/"

  fun testRootDeclaration() = doTest()
  fun testSimpleHeader() = doTest()
  fun testCharClass() = doTest()
  fun testPatternVariants() = doTest()
  fun testComplexSection() = doTest()
  fun testSingleOption() = doTest()
  fun testComplexFile() = doTest()

  private fun doTest() {
    myFixture.testHighlighting(false, true, false, "${getTestName(true)}/.editorconfig")
  }
}
