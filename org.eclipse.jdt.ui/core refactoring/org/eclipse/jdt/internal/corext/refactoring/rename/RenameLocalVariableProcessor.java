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
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jface.text.Region;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.GenericRefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.ltk.core.refactoring.participants.ValidateEditChecker;
import org.eclipse.osgi.util.NLS;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaRefactorings;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class RenameLocalVariableProcessor extends JavaRenameProcessor implements INameUpdating, IReferenceUpdating {

	private static final String ID_RENAME_LOCAL_VARIABLE= "org.eclipse.jdt.ui.rename.local.variable"; //$NON-NLS-1$
	private static final String ATTRIBUTE_HANDLE= "handle"; //$NON-NLS-1$
	private static final String ATTRIBUTE_RANGE= "variable"; //$NON-NLS-1$
	private static final String ATTRIBUTE_NAME= "name"; //$NON-NLS-1$
	private static final String ATTRIBUTE_REFERENCES= "references"; //$NON-NLS-1$

	private static class ProblemNodeFinder {
	
		private ProblemNodeFinder() {
			//static
		}
		
		public static SimpleName[] getProblemNodes(ASTNode methodNode, TextEdit[] edits, TextChange change, String key) {
			NameNodeVisitor visitor= new NameNodeVisitor(edits, change, key);
			methodNode.accept(visitor);
			return visitor.getProblemNodes();
		}
		
		private static class NameNodeVisitor extends ASTVisitor {
	
			private Collection fRanges;
			private Collection fProblemNodes;
			private String fKey;
	
			public NameNodeVisitor(TextEdit[] edits, TextChange change, String key) {
				Assert.isNotNull(edits);
				Assert.isNotNull(key);
				fRanges= new HashSet(Arrays.asList(RefactoringAnalyzeUtil.getNewRanges(edits, change)));
				fProblemNodes= new ArrayList(0);
				fKey= key;
			}
	
			public SimpleName[] getProblemNodes() {
				return (SimpleName[]) fProblemNodes.toArray(new SimpleName[fProblemNodes.size()]);
			}
	
			private static VariableDeclaration getVariableDeclaration(Name node) {
				IBinding binding= node.resolveBinding();
				if (binding == null && node.getParent() instanceof VariableDeclaration)
					return (VariableDeclaration) node.getParent();
	
				if (binding != null && binding.getKind() == IBinding.VARIABLE) {
					CompilationUnit cu= (CompilationUnit) ASTNodes.getParent(node, CompilationUnit.class);
					return ASTNodes.findVariableDeclaration(((IVariableBinding) binding), cu);
				}
				return null;
			}
	
			//----- visit methods 
	
			public boolean visit(SimpleName node) {
				VariableDeclaration decl= getVariableDeclaration(node);
				if (decl == null)
					return super.visit(node);
				boolean keysEqual= fKey.equals(RefactoringAnalyzeUtil.getFullBindingKey(decl));
				boolean rangeInSet= fRanges.contains(new Region(node.getStartPosition(), node.getLength()));
	
				if (keysEqual && !rangeInSet)
					fProblemNodes.add(node);
	
				if (!keysEqual && rangeInSet)
					fProblemNodes.add(node);
	
				return super.visit(node);
			}
		}
	}
	
	private ILocalVariable fLocalVariable;
	private ICompilationUnit fCu;
	
	//the following fields are set or modified after the construction
	private boolean fUpdateReferences;
	private String fCurrentName;
	private String fNewName;
	private CompilationUnit fCompilationUnitNode;
	private VariableDeclaration fTempDeclarationNode;
	private TextChange fChange;
	
	private boolean fIsDerived;
	private GroupCategorySet fCategorySet;
	private TextChangeManager fChangeManager;

	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renameLocalVariableProcessor"; //$NON-NLS-1$
	
	public RenameLocalVariableProcessor(ILocalVariable localVariable) {
		fLocalVariable= localVariable;
		fUpdateReferences= true;
		if (localVariable != null)
			fCu= (ICompilationUnit) localVariable.getAncestor(IJavaElement.COMPILATION_UNIT);
		fNewName= ""; //$NON-NLS-1$
		fIsDerived= false;
	}
	
	protected RenameLocalVariableProcessor(ILocalVariable localVariable, TextChangeManager manager, CompilationUnit compilUnit, GroupCategorySet categorySet) {
		this(localVariable);
		fChangeManager= manager;
		fCategorySet= categorySet;
		fCompilationUnitNode= compilUnit;
		fIsDerived= true;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor#needsSavedEditors()
	 */
	public boolean needsSavedEditors() {
		return false;
	}
	
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor#loadDerivedParticipants(org.eclipse.ltk.core.refactoring.RefactoringStatus, java.util.List, java.lang.String[], org.eclipse.ltk.core.refactoring.participants.SharableParticipants)
	 */
	protected final void loadDerivedParticipants(final RefactoringStatus status, final List result, final String[] natures, final SharableParticipants shared) throws CoreException {
		// Do nothing
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor#getAffectedProjectNatures()
	 */
	protected final String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fLocalVariable);
	}
	
	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getElements()
	 */
	public Object[] getElements() {
		return new Object[] { fLocalVariable };
	}
	
	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getIdentifier()
	 */
	public String getIdentifier() {
		return IDENTIFIER;
	}
	
	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getProcessorName()
	 */
	public String getProcessorName() {
		return RefactoringCoreMessages.RenameTempRefactoring_rename; 
	}
	
	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#isApplicable()
	 */
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameAvailable(fLocalVariable);
	}
	
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating#canEnableUpdateReferences()
	 */
	public boolean canEnableUpdateReferences() {
		return true;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor#getUpdateReferences()
	 */
	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating#setUpdateReferences(boolean)
	 */
	public void setUpdateReferences(boolean updateReferences) {
		fUpdateReferences= updateReferences;
	}
	
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating#getCurrentElementName()
	 */
	public String getCurrentElementName() {
		return fCurrentName;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating#getNewElementName()
	 */
	public String getNewElementName() {
		return fNewName;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating#setNewElementName(java.lang.String)
	 */
	public void setNewElementName(String newName) {
		Assert.isNotNull(newName);
		fNewName= newName;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating#getNewElement()
	 */
	public Object getNewElement(){
		return null; //cannot create an ILocalVariable
	}
	
	
	/* non java-doc
	 * @see Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		initAST();
		if (fTempDeclarationNode == null || fTempDeclarationNode.resolveBinding() == null)
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.RenameTempRefactoring_must_select_local); 
		if (! Checks.isDeclaredIn(fTempDeclarationNode, MethodDeclaration.class) 
		 && ! Checks.isDeclaredIn(fTempDeclarationNode, Initializer.class))
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.RenameTempRefactoring_only_in_methods_and_initializers); 
				
		initNames();			
		return new RefactoringStatus();
	}

	private void initAST() throws JavaModelException {
		if (!fIsDerived)
			fCompilationUnitNode= new RefactoringASTParser(AST.JLS3).parse(fCu, true);
		ISourceRange sourceRange= fLocalVariable.getNameRange();
		ASTNode name= NodeFinder.perform(fCompilationUnitNode, sourceRange);
		if (name == null)
			return;
		if (name.getParent() instanceof VariableDeclaration)
			fTempDeclarationNode= (VariableDeclaration) name.getParent();
	}
	
	private void initNames(){
		fCurrentName= fTempDeclarationNode.getName().getIdentifier();
	}
	
	
	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkFinalConditions(org.eclipse.core.runtime.IProgressMonitor, org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext)
	 */
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws CoreException, OperationCanceledException {
		try {
			pm.beginTask("", 1);	 //$NON-NLS-1$

			ValidateEditChecker checker= (ValidateEditChecker) context.getChecker(ValidateEditChecker.class);
			checker.addFile(ResourceUtil.getFile(fCu));
			
			RefactoringStatus result= checkNewElementName(fNewName);
			if (result.hasFatalError())
				return result;
			result.merge(analyzeAST());
			return result;
		} finally {
			pm.done();
			if (fIsDerived) {
				// end of life cycle for this processor
				fChange= null;
				fCompilationUnitNode= null;
				fTempDeclarationNode= null;
			}
		}	
	}
		
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating#checkNewElementName(java.lang.String)
	 */
	public RefactoringStatus checkNewElementName(String newName) throws JavaModelException {
		RefactoringStatus result= Checks.checkFieldName(newName);
		if (! Checks.startsWithLowerCase(newName))
			if (fIsDerived) {
				final String nameOfParent= (fLocalVariable.getParent() instanceof IMethod) ? fLocalVariable.getParent().getElementName() : RefactoringCoreMessages.JavaElementUtil_initializer;
				final String nameOfType= fLocalVariable.getAncestor(IJavaElement.TYPE).getElementName();
				result.addWarning(Messages.format(RefactoringCoreMessages.RenameTempRefactoring_lowercase2, new String[] { newName, nameOfParent, nameOfType }));
			} else {
				result.addWarning(RefactoringCoreMessages.RenameTempRefactoring_lowercase);
			}
		return result;		
	}
		
	private RefactoringStatus analyzeAST() throws CoreException{
		TextEdit declarationEdit= createRenameEdit(fTempDeclarationNode.getName().getStartPosition());
		TextEdit[] allRenameEdits= getAllRenameEdits(declarationEdit);
		fChange= new CompilationUnitChange(RefactoringCoreMessages.RenameTempRefactoring_rename, fCu); 
		MultiTextEdit rootEdit= new MultiTextEdit();
		fChange.setEdit(rootEdit);
		fChange.setKeepPreviewEdits(true);

		String changeName= Messages.format(RefactoringCoreMessages.RenameTempRefactoring_changeName, new String[]{fCurrentName, fNewName}); 
		for (int i= 0; i < allRenameEdits.length; i++) {
			if (fIsDerived) {
				// Add a copy of the text edit (text edit may only have one
				// parent) to keep problem reporting code clean
				TextChangeCompatibility.addTextEdit(fChangeManager.get(fCu), changeName, allRenameEdits[i].copy(), fCategorySet);
			}
			rootEdit.addChild(allRenameEdits[i]);
			fChange.addTextEditGroup(new TextEditGroup(changeName, allRenameEdits[i]));
		}
		String newCuSource= fChange.getPreviewContent(new NullProgressMonitor());
		ASTParser p= ASTParser.newParser(AST.JLS3);
		p.setSource(newCuSource.toCharArray());
		p.setUnitName(fCu.getElementName());
		p.setProject(fCu.getJavaProject());
		p.setCompilerOptions(RefactoringASTParser.getCompilerOptions(fCu));
		CompilationUnit newCUNode= (CompilationUnit) p.createAST(null);

		RefactoringStatus result= new RefactoringStatus();
		result.merge(analyzeCompileErrors(newCuSource, newCUNode));
		if (result.hasError())
			return result;
		
		String fullKey= RefactoringAnalyzeUtil.getFullBindingKey(fTempDeclarationNode);	
		ASTNode enclosing= getEnclosingBlockOrMethod(declarationEdit, fChange, newCUNode);
		SimpleName[] problemNodes= ProblemNodeFinder.getProblemNodes(enclosing, allRenameEdits, fChange, fullKey);
		result.merge(RefactoringAnalyzeUtil.reportProblemNodes(newCuSource, problemNodes));
		return result;
	}

	private TextEdit[] getAllRenameEdits(TextEdit declarationEdit) {
		if (! fUpdateReferences)
			return new TextEdit[] { declarationEdit };
		
		TempOccurrenceAnalyzer fTempAnalyzer= new TempOccurrenceAnalyzer(fTempDeclarationNode, true);
		fTempAnalyzer.perform();
		int[] referenceOffsets= fTempAnalyzer.getReferenceAndJavadocOffsets();

		TextEdit[] allRenameEdits= new TextEdit[referenceOffsets.length + 1];
		for (int i= 0; i < referenceOffsets.length; i++)
			allRenameEdits[i]= createRenameEdit(referenceOffsets[i]);
		allRenameEdits[referenceOffsets.length]= declarationEdit;
		return allRenameEdits;
	}

	private TextEdit createRenameEdit(int offset) {
		return new ReplaceEdit(offset, fCurrentName.length(), fNewName);
	}
	
	private ASTNode getEnclosingBlockOrMethod(TextEdit declarationEdit, TextChange change, CompilationUnit newCUNode) {
		ASTNode enclosing= RefactoringAnalyzeUtil.getBlock(declarationEdit, change, newCUNode);
		if (enclosing == null)	
			enclosing= RefactoringAnalyzeUtil.getMethodDeclaration(declarationEdit, change, newCUNode);
		return enclosing;
	}
	
    private RefactoringStatus analyzeCompileErrors(String newCuSource, CompilationUnit newCUNode) {
    	RefactoringStatus result= new RefactoringStatus();
    	IProblem[] newProblems= RefactoringAnalyzeUtil.getIntroducedCompileProblems(newCUNode, fCompilationUnitNode);
    	for (int i= 0; i < newProblems.length; i++) {
            IProblem problem= newProblems[i];
            if (problem.isError())
            	result.addEntry(JavaRefactorings.createStatusEntry(problem, newCuSource));
        }
        return result;
    }

	public Change createChange(IProgressMonitor monitor) throws CoreException {
		try {
			Change change= fChange;
			if (change != null) {
				final CompositeChange composite= new CompositeChange("", new Change[] { change}) { //$NON-NLS-1$

					public RefactoringDescriptor getRefactoringDescriptor() {
						final Map arguments= new HashMap();
						arguments.put(ATTRIBUTE_HANDLE, fCu.getHandleIdentifier());
						arguments.put(ATTRIBUTE_NAME, getNewElementName());
						final ISourceRange range= fLocalVariable.getNameRange();
						arguments.put(ATTRIBUTE_RANGE, new Integer(range.getOffset()).toString() + " " + new Integer(range.getLength()).toString()); //$NON-NLS-1$
						arguments.put(ATTRIBUTE_REFERENCES, Boolean.valueOf(fUpdateReferences).toString());
						String project= null;
						IJavaProject javaProject= fCu.getJavaProject();
						if (javaProject != null)
							project= javaProject.getElementName();
						return new RefactoringDescriptor(ID_RENAME_LOCAL_VARIABLE, project, MessageFormat.format(RefactoringCoreMessages.RenameLocalVariableProcessor_descriptor_description, new String[] { fCurrentName, JavaElementLabels.getElementLabel(fLocalVariable.getParent(), JavaElementLabels.ALL_FULLY_QUALIFIED), fNewName}), null, arguments, RefactoringDescriptor.NONE);
					}
				};
				composite.markAsSynthetic();
				change= composite;
			}
			return change;
		} finally {
			monitor.done();
		}
	}

	public RefactoringStatus initialize(RefactoringArguments arguments) {
		if (arguments instanceof GenericRefactoringArguments) {
			final GenericRefactoringArguments generic= (GenericRefactoringArguments) arguments;
			final String handle= generic.getAttribute(ATTRIBUTE_HANDLE);
			if (handle != null) {
				final IJavaElement element= JavaCore.create(handle);
				if (element == null || !element.exists())
					return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_input_not_exists, getIdentifier()));
				else
					fCu= (ICompilationUnit) element;
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_HANDLE));
			final String name= generic.getAttribute(ATTRIBUTE_NAME);
			if (name != null) {
				RefactoringStatus status= new RefactoringStatus();
				try {
					status= checkNewElementName(name);
				} catch (CoreException exception) {
					JavaPlugin.log(exception);
				}
				if (!status.hasError())
					setNewElementName(name);
				else
					return status;
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_NAME));
			if (fCu != null) {
				final String range= generic.getAttribute(ATTRIBUTE_RANGE);
				if (range != null) {
					int offset= 0;
					int length= 0;
					final StringTokenizer tokenizer= new StringTokenizer(range);
					if (tokenizer.hasMoreTokens())
						offset= Integer.valueOf(tokenizer.nextToken()).intValue();
					if (tokenizer.hasMoreTokens())
						length= Integer.valueOf(tokenizer.nextToken()).intValue();
					if (offset >= 0 && length >= 0) {
						try {
							final IJavaElement[] elements= fCu.codeSelect(offset, length);
							if (elements != null) {
								for (int index= 0; index < elements.length; index++) {
									final IJavaElement element= elements[index];
									if (element instanceof ILocalVariable)
										fLocalVariable= (ILocalVariable) element;
								}
							}
							if (fLocalVariable == null)
								return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_input_not_exists, getIdentifier()));
						} catch (JavaModelException exception) {
							JavaPlugin.log(exception);
						}
					} else
						return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { range, ATTRIBUTE_RANGE}));
				} else
					return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_RANGE));
			}
			final String references= generic.getAttribute(ATTRIBUTE_REFERENCES);
			if (references != null) {
				fUpdateReferences= Boolean.valueOf(references).booleanValue();
			} else
				return RefactoringStatus.createFatalErrorStatus(NLS.bind(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REFERENCES));
		} else
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments);
		return new RefactoringStatus();
	}
}
