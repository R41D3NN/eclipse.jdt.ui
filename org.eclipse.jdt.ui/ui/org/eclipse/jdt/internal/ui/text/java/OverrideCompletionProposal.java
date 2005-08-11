/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportsStructure;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class OverrideCompletionProposal extends JavaTypeCompletionProposal implements ICompletionProposalExtension4 {

	private IJavaProject fJavaProject;
	private String fMethodName;
	private String[] fParamTypes;

	public OverrideCompletionProposal(IJavaProject jproject, ICompilationUnit cu, String methodName, String[] paramTypes, int start, int length, String displayName, String completionProposal) {
		super(completionProposal, cu, start, length, null, displayName, 0);
		Assert.isNotNull(jproject);
		Assert.isNotNull(methodName);
		Assert.isNotNull(paramTypes);
		Assert.isNotNull(cu);

		fParamTypes= paramTypes;
		fMethodName= methodName;

		fJavaProject= jproject;
		
		StringBuffer buffer= new StringBuffer();
		buffer.append(completionProposal);
		buffer.append(" {};"); //$NON-NLS-1$
		
		setReplacementString(buffer.toString());
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposalExtension3#getPrefixCompletionText(org.eclipse.jface.text.IDocument,int)
	 */
	public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
		return fMethodName;
	}

	/*
	 * @see JavaTypeCompletionProposal#updateReplacementString(IDocument,char,int,ImportsStructure)
	 */
	protected boolean updateReplacementString(IDocument document, char trigger, int offset, ImportsStructure structure) throws CoreException, BadLocationException {
		final IDocument buffer= new Document(document.get());
		int index= offset - 1;
		while (index >= 0 && Character.isJavaIdentifierPart(buffer.getChar(index)))
			index--;
		final int length= offset - index - 1;
		buffer.replace(index + 1, length, " "); //$NON-NLS-1$
		final ASTParser parser= ASTParser.newParser(AST.JLS3);
		parser.setResolveBindings(true);
		parser.setSource(buffer.get().toCharArray());
		parser.setUnitName(fCompilationUnit.getElementName());
		parser.setProject(fCompilationUnit.getJavaProject());
		final CompilationUnit unit= (CompilationUnit) parser.createAST(new NullProgressMonitor());
		ITypeBinding binding= null;
		ChildListPropertyDescriptor descriptor= null;
		ASTNode node= NodeFinder.perform(unit, index + 1, 0);
		if (node instanceof AnonymousClassDeclaration) {
			switch(node.getParent().getNodeType()) {
			case ASTNode.CLASS_INSTANCE_CREATION :
				binding= ((ClassInstanceCreation) node.getParent()).resolveTypeBinding();
				break;
			case ASTNode.ENUM_CONSTANT_DECLARATION :
				IMethodBinding methodBinding = ((EnumConstantDeclaration) node.getParent()).resolveConstructorBinding();
				if (methodBinding != null) {
					binding = methodBinding.getDeclaringClass();
				}
			}
			descriptor= AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY;
		} else if (node instanceof AbstractTypeDeclaration) {
			final AbstractTypeDeclaration declaration= ((AbstractTypeDeclaration) node);
			descriptor= declaration.getBodyDeclarationsProperty();
			binding= declaration.resolveBinding();
		}
		if (binding != null) {
			ASTRewrite rewrite= ASTRewrite.create(unit.getAST());
			IMethodBinding[] bindings= StubUtility2.getOverridableMethods(rewrite.getAST(), binding, true);
			if (bindings != null && bindings.length > 0) {
				List candidates= new ArrayList(bindings.length);
				IMethodBinding method= null;
				for (index= 0; index < bindings.length; index++) {
					method= bindings[index];
					if (method.getName().equals(fMethodName) && method.getParameterTypes().length == fParamTypes.length)
						candidates.add(method);
				}
				if (candidates.size() > 1) {
					method= Bindings.findMethodInHierarchy(rewrite.getAST().resolveWellKnownType("java.lang.Object"), binding, fMethodName, fParamTypes); //$NON-NLS-1$
					if (method == null)
						method= (IMethodBinding) candidates.get(0);
				} else if (!candidates.isEmpty())
					method= (IMethodBinding) candidates.get(0);
				if (method != null) {
					CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(fJavaProject);
					ListRewrite rewriter= rewrite.getListRewrite(node, descriptor);
					String key= method.getKey();
					MethodDeclaration stub= null;
					for (index= 0; index < bindings.length; index++) {
						if (key.equals(bindings[index].getKey())) {
							stub= StubUtility2.createImplementationStub(fCompilationUnit, rewrite, structure, bindings[index], binding.getName(), binding.isInterface(), settings);
							if (stub != null)
								rewriter.insertFirst(stub, null);
							break;
						}
					}
					if (stub != null) {
						IDocument contents= new Document(fCompilationUnit.getBuffer().getContents());
						IRegion region= contents.getLineInformationOfOffset(getReplacementOffset());
						ITrackedNodePosition position= rewrite.track(stub);
						String indent= Strings.getIndentString(contents.get(region.getOffset(), region.getLength()), settings.tabWidth, settings.indentWidth);
						try {
							rewrite.rewriteAST(contents, fJavaProject.getOptions(true)).apply(contents, TextEdit.UPDATE_REGIONS);
						} catch (MalformedTreeException exception) {
							JavaPlugin.log(exception);
						} catch (BadLocationException exception) {
							JavaPlugin.log(exception);
						}
						setReplacementString(Strings.changeIndent(Strings.trimIndentation(contents.get(position.getStartPosition(), position.getLength()), settings.tabWidth, settings.indentWidth, false), 0, settings.tabWidth, settings.indentWidth, indent, TextUtilities.getDefaultLineDelimiter(contents)));
					}
				}
			}
		}
		return true;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposalExtension4#isAutoInsertable()
	 */
	public boolean isAutoInsertable() {
		return false;
	}
}