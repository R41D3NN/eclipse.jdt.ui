/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.text.correction;

import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Proposals for 'Assign to variable' quick assist
 * - Assign an expression from an ExpressionStatement to a local or field
 * - Assign a parameter to a field
 * */
public class AssignToVariableAssistProposal extends LinkedCorrectionProposal {

	public static final int LOCAL= 1;
	public static final int FIELD= 2;
	
	private final String KEY_NAME= "name";  //$NON-NLS-1$
	private final String KEY_TYPE= "type";  //$NON-NLS-1$

	private final int  fVariableKind;
	private final ASTNode fNodeToAssign; // ExpressionStatement or SingleVariableDeclaration
	private final ITypeBinding fTypeBinding;
		
	public AssignToVariableAssistProposal(ICompilationUnit cu, int variableKind, ExpressionStatement node, ITypeBinding typeBinding, int relevance) {
		super(null, cu, null, relevance, null);
	
		fVariableKind= variableKind;
		fNodeToAssign= node;
		fTypeBinding= typeBinding;
		if (variableKind == LOCAL) {
			setDisplayName(CorrectionMessages.getString("AssignToVariableAssistProposal.assigntolocal.description")); //$NON-NLS-1$
			setImage(JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_LOCAL));
		} else {
			setDisplayName(CorrectionMessages.getString("AssignToVariableAssistProposal.assigntofield.description")); //$NON-NLS-1$
			setImage(JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PRIVATE));
		}
	}
	
	public AssignToVariableAssistProposal(ICompilationUnit cu, SingleVariableDeclaration parameter, int relevance) {
		super(null, cu, null, relevance, null);
	
		fVariableKind= FIELD;
		fNodeToAssign= parameter;
		fTypeBinding= parameter.resolveBinding().getType();
		setDisplayName(CorrectionMessages.getString("AssignToVariableAssistProposal.assignparamtofield.description")); //$NON-NLS-1$
		setImage(JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PRIVATE));
	}	
				
	protected ASTRewrite getRewrite() throws CoreException {
		if (fVariableKind == FIELD) {
			return doAddField();
		} else { // LOCAL
			return doAddLocal();
		}
	}

	private ASTRewrite doAddLocal() throws CoreException {
		Expression expression= ((ExpressionStatement) fNodeToAssign).getExpression();
		ASTRewrite rewrite= new ASTRewrite(fNodeToAssign.getParent());
		AST ast= fNodeToAssign.getAST();

		String varName= suggestLocalVariableNames(fTypeBinding);
				
		VariableDeclarationFragment newDeclFrag= ast.newVariableDeclarationFragment();
		newDeclFrag.setName(ast.newSimpleName(varName));
		newDeclFrag.setInitializer((Expression) rewrite.createCopy(expression));
		
		VariableDeclarationStatement newDecl= ast.newVariableDeclarationStatement(newDeclFrag);
		
		Type type= evaluateType(ast);
		newDecl.setType(type);
		
		rewrite.markAsReplaced(fNodeToAssign, newDecl); 
		
		markAsLinked(rewrite, newDeclFrag.getName(), true, KEY_NAME); //$NON-NLS-1$
		markAsLinked(rewrite, newDecl.getType(), false, KEY_TYPE); //$NON-NLS-1$

		return rewrite;
	}

	private ASTRewrite doAddField() throws CoreException {
		boolean isParamToField= fNodeToAssign.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION;
		
		MethodDeclaration methodDecl= ASTResolving.findParentMethodDeclaration(fNodeToAssign);
		
		ASTNode newTypeDecl= ASTResolving.findParentType(methodDecl);
		Expression expression= isParamToField ? ((SingleVariableDeclaration) fNodeToAssign).getName() : ((ExpressionStatement) fNodeToAssign).getExpression();
		
		boolean isAnonymous= newTypeDecl.getNodeType() == ASTNode.ANONYMOUS_CLASS_DECLARATION;
		List decls= isAnonymous ?  ((AnonymousClassDeclaration) newTypeDecl).bodyDeclarations() :  ((TypeDeclaration) newTypeDecl).bodyDeclarations();
		
		ASTRewrite rewrite= new ASTRewrite(newTypeDecl);
		AST ast= newTypeDecl.getAST();
		
		boolean isStatic= Modifier.isStatic(methodDecl.getModifiers()) && !isAnonymous;
		int modifiers= Modifier.PRIVATE;
		if (isStatic) {
			modifiers |= Modifier.STATIC;
		}
		
		String varName= suggestFieldNames(fTypeBinding, expression, modifiers);
		VariableDeclarationFragment newDeclFrag= ast.newVariableDeclarationFragment();
		newDeclFrag.setName(ast.newSimpleName(varName));
				
		FieldDeclaration newDecl= ast.newFieldDeclaration(newDeclFrag);
		
		Type type= evaluateType(ast);
		newDecl.setType(type);
		newDecl.setModifiers(modifiers);
		
		Assignment assignment= ast.newAssignment();
		assignment.setRightHandSide((Expression) rewrite.createCopy(expression));

		boolean needsThis= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.CODEGEN_KEYWORD_THIS);
		if (isParamToField) {
			needsThis |= varName.equals(((SimpleName) expression).getIdentifier());
		}

		SimpleName accessName= ast.newSimpleName(varName);
		if (needsThis) {
			FieldAccess fieldAccess= ast.newFieldAccess();
			fieldAccess.setName(accessName);
			if (isStatic) {
				String typeName= ((TypeDeclaration) newTypeDecl).getName().getIdentifier();
				fieldAccess.setExpression(ast.newSimpleName(typeName));
			} else {
				fieldAccess.setExpression(ast.newThisExpression());
			}
			assignment.setLeftHandSide(fieldAccess);
		} else {
			assignment.setLeftHandSide(accessName);
		}
		
		decls.add(findFieldInsertIndex(decls, fNodeToAssign.getStartPosition()), newDecl);
		
		rewrite.markAsInserted(newDecl);

		if (isParamToField) {
			// assign parameter to field
			List statements= methodDecl.getBody().statements();
			ExpressionStatement statement= ast.newExpressionStatement(assignment);
			statements.add(findAssignmentInsertIndex(statements), statement);
			rewrite.markAsInserted(statement);
		} else {			
			rewrite.markAsReplaced(expression, assignment);
		} 
		
		markAsLinked(rewrite, newDeclFrag.getName(), false, KEY_NAME);
		markAsLinked(rewrite, newDecl.getType(), false, KEY_TYPE);
		markAsLinked(rewrite, accessName, true, KEY_NAME);
		
		return rewrite;		
	}

	private Type evaluateType(AST ast) throws CoreException {
		ITypeBinding[] proposals= ASTResolving.getRelaxingTypes(ast, fTypeBinding);
		for (int i= 0; i < proposals.length; i++) {
			addLinkedModeProposal(KEY_TYPE, proposals[i]);
		}
		String typeName= addImport(fTypeBinding);
		return ASTNodeFactory.newType(ast, typeName);
	}
	
	private String suggestLocalVariableNames(ITypeBinding binding) {
		IJavaProject project= getCompilationUnit().getJavaProject();
		ITypeBinding base= binding.isArray() ? binding.getElementType() : binding;
		IPackageBinding packBinding= base.getPackage();
		String packName= packBinding != null ? packBinding.getName() : ""; //$NON-NLS-1$
		
		String[] excludedNames= getUsedVariableNames();
		String typeName= base.getName();
		String[] names= NamingConventions.suggestLocalVariableNames(project, packName, typeName, binding.getDimensions(), excludedNames);
		if (names.length == 0) {
			return "class1"; // fix for pr, remoev after 20030127 //$NON-NLS-1$
		}
		for (int i= 0; i < names.length; i++) {
			addLinkedModeProposal(KEY_NAME, names[i]);
		}
		return names[0]; 
	}
	
	private String suggestFieldNames(ITypeBinding binding, Expression expression, int modifiers) {
		IJavaProject project= getCompilationUnit().getJavaProject();
		ITypeBinding base= binding.isArray() ? binding.getElementType() : binding;
		IPackageBinding packBinding= base.getPackage();
		String packName= packBinding != null ? packBinding.getName() : ""; //$NON-NLS-1$
		
		String[] excludedNames= getUsedVariableNames();
		String result= null;
		HashSet taken= new HashSet();
		
		if (expression instanceof SimpleName) {
			String name= ((SimpleName) expression).getIdentifier();
			// bug 38111
			String[] argname= StubUtility.getFieldNameSuggestions(project, name, modifiers, excludedNames);
			for (int i= 0; i < argname.length; i++) {
				String curr= argname[i];
				if (result == null || curr.length() > result.length()) {
					result= curr;
				}
				if (taken.add(curr)) {
					addLinkedModeProposal(KEY_NAME, curr);
				}
			}			
		}

		String typeName= base.getName();
		String[] names= NamingConventions.suggestFieldNames(project, packName, typeName, binding.getDimensions(), modifiers, excludedNames);
		if (names.length == 0) {
			return "class1"; // fix for pr, remoev after 20030127 //$NON-NLS-1$
		}
		for (int i= 0; i < names.length; i++) {
			String curr= names[i];
			if (taken.add(curr)) {
				addLinkedModeProposal(KEY_NAME, curr);
			}
		}
		if (result == null) {
			result= names[0];
		}
		return result;		
	}
	
	private String[] getUsedVariableNames() {
		CompilationUnit root= (CompilationUnit) fNodeToAssign.getRoot();
		IBinding[] bindings= (new ScopeAnalyzer(root)).getDeclarationsInScope(fNodeToAssign.getStartPosition(), ScopeAnalyzer.VARIABLES);
		String[] names= new String[bindings.length];
		for (int i= 0; i < names.length; i++) {
			names[i]= bindings[i].getName();
		}
		return names;
	}

	private int findAssignmentInsertIndex(List statements) {
		if (!statements.isEmpty()) {
			int nodeType= ((ASTNode) statements.get(0)).getNodeType();
			if (nodeType == ASTNode.CONSTRUCTOR_INVOCATION || nodeType == ASTNode.SUPER_CONSTRUCTOR_INVOCATION) {
				return 1;
			}
		}
		return 0;
	}
	
	private int findFieldInsertIndex(List decls, int currPos) {
		for (int i= decls.size() - 1; i >= 0; i--) {
			ASTNode curr= (ASTNode) decls.get(i);
			if (curr instanceof FieldDeclaration && currPos > curr.getStartPosition() + curr.getLength()) {
				return i + 1;
			}
		}
		return 0;
	}
		
	/**
	 * Returns the variable kind.
	 * @return int
	 */
	public int getVariableKind() {
		return fVariableKind;
	}
	

}
