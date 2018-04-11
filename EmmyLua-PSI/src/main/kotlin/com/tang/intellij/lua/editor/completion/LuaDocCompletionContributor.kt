/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.editor.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.psi.LuaFuncBodyOwner
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.ITyClass
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * doc 相关代码完成
 * Created by tangzx on 2016/12/2.
 */
class LuaDocCompletionContributor : CompletionContributor() {
    val DOC_TAG_TOKENS = TokenSet.create(
            LuaDocTypes.TAG_PARAM,
            LuaDocTypes.TAG_RETURN,
            LuaDocTypes.TAG_CLASS,
            LuaDocTypes.TAG_MODULE,
            LuaDocTypes.TAG_TYPE,
            LuaDocTypes.TAG_FIELD,
            LuaDocTypes.TAG_LANGUAGE,
            LuaDocTypes.TAG_OVERLOAD,
            LuaDocTypes.TAG_PRIVATE,
            LuaDocTypes.TAG_PROTECTED,
            LuaDocTypes.TAG_PUBLIC,
            LuaDocTypes.TAG_SEE,
            LuaDocTypes.TAG_GENERIC
    )

    init {
        extend(CompletionType.BASIC, SHOW_DOC_TAG, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                val set = DOC_TAG_TOKENS
                for (type in set.types) {
                    completionResultSet.addElement(LookupElementBuilder.create(type.toString()))
                }
                completionResultSet.stopHere()
            }
        })

        extend(CompletionType.BASIC, SHOW_OPTIONAL, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                completionResultSet.addElement(LookupElementBuilder.create("optional"))
            }
        })

        extend(CompletionType.BASIC, AFTER_PARAM, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                var element = completionParameters.position//completionParameters.originalFile.findElementAt(completionParameters.offset - 1)
                if (element !is LuaDocPsiElement)
                    element = element.parent

                if (element is LuaDocPsiElement) {
                    val owner = LuaCommentUtil.findOwner(element)
                    if (owner is LuaFuncBodyOwner) {
                        val body = owner.funcBody
                        if (body != null) {
                            val parDefList = body.paramNameDefList
                            for (def in parDefList) {
                                val item = CompletionItem(def.text)
                                item.kind = CompletionItemKind.Unit
                                completionResultSet.addElement(item)
                            }
                        }
                    }
                }
            }
        })

        extend(CompletionType.BASIC, SHOW_CLASS, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                val project = completionParameters.position.project
                LuaClassIndex.processKeys(project, Processor{
                    val item = CompletionItem(it)
                    item.kind = CompletionItemKind.Class
                    completionResultSet.addElement(item)
                    true
                })
                completionResultSet.stopHere()
            }
        })

        extend(CompletionType.BASIC, SHOW_ACCESS_MODIFIER, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                completionResultSet.addElement(LookupElementBuilder.create("protected"))
                completionResultSet.addElement(LookupElementBuilder.create("public"))
            }
        })

        // 属性提示
        extend(CompletionType.BASIC, SHOW_FIELD, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                val position = completionParameters.position
                val comment = PsiTreeUtil.getParentOfType(position, LuaComment::class.java)
                val classDef = PsiTreeUtil.findChildOfType(comment, LuaDocClassDef::class.java)
                if (classDef != null) {
                    val classType = classDef.type
                    /*classType.processMembers(SearchContext(classDef.project)) { _, member ->
                        if (member is LuaClassField)
                            completionResultSet.addElement(LookupElementBuilder.create(member.name!!).withIcon(LuaIcons.CLASS_FIELD))
                        Unit
                    }*/
                }
            }
        })

        // @see member completion
        extend(CompletionType.BASIC, SHOW_SEE_MEMBER, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                val position = completionParameters.position
                val seeRefTag = PsiTreeUtil.getParentOfType(position, LuaDocSeeRefTag::class.java)
                if (seeRefTag != null) {
                    val classType = seeRefTag.classNameRef?.resolveType() as? ITyClass
                    classType?.processMembers(SearchContext(seeRefTag.project)) { _, member ->
                        completionResultSet.addElement(LookupElementBuilder.create(member.name!!))
                        Unit
                    }
                }
                completionResultSet.stopHere()
            }
        })

        /*extend(CompletionType.BASIC, SHOW_LAN, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
                Language.getRegisteredLanguages().forEach {
                    val fileType = it.associatedFileType
                    var lookupElement = LookupElementBuilder.create(it.id)
                    if (fileType != null)
                        lookupElement = lookupElement.withIcon(fileType.icon)
                    completionResultSet.addElement(lookupElement)
                }
                completionResultSet.stopHere()
            }
        })*/
    }

    companion object {

        // 在 @ 之后提示 param class type ...
        private val SHOW_DOC_TAG = psiElement(LuaDocTypes.TAG_NAME)

        // 在 @param 之后提示方法的参数
        private val AFTER_PARAM = psiElement().withParent(LuaDocParamNameRef::class.java)

        // 在 @param 之后提示 optional
        private val SHOW_OPTIONAL = psiElement().afterLeaf(
                psiElement(LuaDocTypes.TAG_PARAM))

        // 在 extends 之后提示类型
        private val SHOW_CLASS = psiElement().withParent(LuaDocClassNameRef::class.java)

        // 在 @field 之后提示 public / protected
        private val SHOW_ACCESS_MODIFIER = psiElement().afterLeaf(
                psiElement().withElementType(LuaDocTypes.TAG_FIELD)
        )

        private val SHOW_FIELD = psiElement(LuaDocTypes.ID).inside(LuaDocFieldDef::class.java)

        //@see type#MEMBER
        private val SHOW_SEE_MEMBER = psiElement(LuaDocTypes.ID).inside(LuaDocSeeRefTag::class.java)

        private val SHOW_LAN = psiElement(LuaDocTypes.ID).inside(LuaDocLanDef::class.java)
    }
}