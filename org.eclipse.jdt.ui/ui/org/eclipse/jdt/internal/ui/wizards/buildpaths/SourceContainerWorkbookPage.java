/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceSorter;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TreeListDialogField;

public class SourceContainerWorkbookPage extends BuildPathBasePage {

	private ListDialogField fClassPathList;
	private IJavaProject fCurrJProject;
	private IPath fProjPath;
	
	private Control fSWTControl;
	
	private IWorkspaceRoot fWorkspaceRoot;
	
//	private SelectionButtonDialogField fProjectRadioButton;
//	private SelectionButtonDialogField fFolderRadioButton;
	private TreeListDialogField fFoldersList;
	
//	private List fProjectCPEntry;
//	private List fFolderCPEntries;
	
	private StringDialogField fOutputLocationField;
	
//	private boolean fIsProjSelected;
	
	private final int IDX_ADDNEW= 0;
	private final int IDX_ADDEXIST= 1;
	private final int IDX_EDIT= 3;
	
	private final int IDX_REMOVE= 5;	

	public SourceContainerWorkbookPage(IWorkspaceRoot root, ListDialogField classPathList, StringDialogField outputLocationField) {
		fWorkspaceRoot= root;
		fClassPathList= classPathList;
	
		fOutputLocationField= outputLocationField;
		
		fSWTControl= null;
				
		SourceContainerAdapter adapter= new SourceContainerAdapter();
				
//		fProjectRadioButton= new SelectionButtonDialogField(SWT.RADIO);
//		fProjectRadioButton.setDialogFieldListener(adapter);
//		fProjectRadioButton.setLabelText(NewWizardMessages.getString("SourceContainerWorkbookPage.rb1.label")); //$NON-NLS-1$
//						
//		fFolderRadioButton= new SelectionButtonDialogField(SWT.RADIO);
//		fFolderRadioButton.setDialogFieldListener(adapter);
//		fFolderRadioButton.setLabelText(NewWizardMessages.getString("SourceContainerWorkbookPage.rb2.label")); //$NON-NLS-1$
		
		String[] buttonLabels;
		int removeIndex;
		buttonLabels= new String[] { 
			/* 0 = IDX_ADDNEW */ NewWizardMessages.getString("SourceContainerWorkbookPage.folders.addnew.button"), //$NON-NLS-1$
			/* 1 = IDX_ADDEXIST */ NewWizardMessages.getString("SourceContainerWorkbookPage.folders.addnew.addexisting.button"), //$NON-NLS-1$
			/* 2 */ null,
			/* 3 = IDX_EDIT */ NewWizardMessages.getString("SourceContainerWorkbookPage.folders.edit.button"), //$NON-NLS-1$
			/* 4 */ null,			
			/* 5 = IDX_REMOVE */ NewWizardMessages.getString("SourceContainerWorkbookPage.folders.remove.button") //$NON-NLS-1$
		};
		removeIndex= IDX_REMOVE;
		
		fFoldersList= new TreeListDialogField(adapter, buttonLabels, new CPListLabelProvider());
		fFoldersList.setDialogFieldListener(adapter);
		fFoldersList.setLabelText(NewWizardMessages.getString("SourceContainerWorkbookPage.folders.label")); //$NON-NLS-1$
		fFoldersList.setRemoveButtonIndex(removeIndex);
		fFoldersList.setTreeExpansionLevel(3);
		
		fFoldersList.setViewerSorter(new CPListElementSorter());
		
//		fFolderRadioButton.setSelection(true);
//		fProjectRadioButton.setSelection(false);
	}
	
	public void init(IJavaProject jproject) {
		fCurrJProject= jproject;
		fProjPath= fCurrJProject.getProject().getFullPath();	
		updateFoldersList();
	}
	
	private void updateFoldersList() {	
		fFoldersList.removeAllElements();
//		fProjectCPEntry= new ArrayList(1);
//		fFolderCPEntries= new ArrayList(4);		
		
		List cpelements= fClassPathList.getElements();
		for (int i= 0; i < cpelements.size(); i++) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			if (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				if (fProjPath.equals(cpe.getPath())) {
					// remember the entry to ensure a unique CPListElement for the project-cpentry
					//fProjectCPEntry.add(cpe);
					fFoldersList.addElement(cpe);
					break;
				} else {
					fFoldersList.addElement(cpe);
//					fFolderCPEntries.add(cpe);
				}
			}
		}
//		boolean isFoldersSelected= fProjectCPEntry.isEmpty();
//		if (isFoldersSelected) {
//			fProjectCPEntry.add(newCPSourceElement(fCurrJProject.getResource()));
//		}
		
		// fix for 1G47IYV: ITPJUI:WINNT - Both radio buttons get selected in Project properties
//		fFolderRadioButton.setSelection(isFoldersSelected);
//		fProjectRadioButton.setSelection(!isFoldersSelected);
	}			
	
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);
		
		Composite composite= new Composite(parent, SWT.NONE);
		
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;		
		composite.setLayout(layout);
		
//		fProjectRadioButton.doFillIntoGrid(composite, 2);
//		fFolderRadioButton.doFillIntoGrid(composite, 2);
		
		Control control= fFoldersList.getListControl(composite);
		GridData gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint= converter.convertWidthInCharsToPixels(50);
		gd.heightHint= converter.convertWidthInCharsToPixels(15);
		gd.grabExcessHorizontalSpace= true;
		control.setLayoutData(gd);
		
		control= fFoldersList.getButtonBox(composite);
		gd= new GridData(GridData.VERTICAL_ALIGN_FILL + GridData.HORIZONTAL_ALIGN_FILL);
		control.setLayoutData(gd);
		
		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fFoldersList.setButtonsMinWidth(buttonBarWidth);
			
		fSWTControl= composite;
		
		return composite;
	}
	
	private Shell getShell() {
		if (fSWTControl != null) {
			return fSWTControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}
	
	
	private class SourceContainerAdapter implements ITreeListAdapter, IDialogFieldListener {
	
		private final Object[] EMPTY_ARR= new Object[0];
		
		// -------- IListAdapter --------
		public void customButtonPressed(TreeListDialogField field, int index) {
			sourcePageCustomButtonPressed(field, index);
		}
		
		public void selectionChanged(TreeListDialogField field) {
			sourcePageSelectionChanged(field);
		}
		
		public Object[] getChildren(TreeListDialogField field, Object element) {
			if (element instanceof CPListElement) {
				return ((CPListElement) element).getChildren();
			}
			return EMPTY_ARR;
		}

		public Object getParent(TreeListDialogField field, Object element) {
			if (element instanceof CPListElementAttribute) {
				return ((CPListElementAttribute) element).getParent();
			}
			return null;
		}

		public boolean hasChildren(TreeListDialogField field, Object element) {
			return (element instanceof CPListElement);
		}		
		
		// ---------- IDialogFieldListener --------
		public void dialogFieldChanged(DialogField field) {
			sourcePageDialogFieldChanged(field);
		}
	}
	
	private void sourcePageCustomButtonPressed(DialogField field, int index) {
		if (field == fFoldersList) {
			List elementsToAdd= new ArrayList(10);
			switch (index) {
			case IDX_ADDNEW: /* add new */
				CPListElement srcentry= openNewSourceContainerDialog(null);
				if (srcentry != null) {
					elementsToAdd.add(srcentry);
				}
				break;
			case IDX_ADDEXIST: /* add existing */
				CPListElement[] srcentries= openSourceContainerDialog(null);
				if (srcentries != null) {
					for (int i= 0; i < srcentries.length; i++) {
						elementsToAdd.add(srcentries[i]);
					}
				}
				break;
			case IDX_EDIT: /* add existing */
				editEntry();
				return;
			}
			if (!elementsToAdd.isEmpty()) {
				fFoldersList.addElements(elementsToAdd);
				fFoldersList.postSetSelection(new StructuredSelection(elementsToAdd));
				// if no source folders up to now
				if (fFoldersList.getSize() == elementsToAdd.size()) {
					askForChangingBuildPathDialog();
				}
			}
		}
	}

	private void editEntry() {
		List selElements= fFoldersList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		Object elem= selElements.get(0);
		if (fFoldersList.getIndexOfElement(elem) != -1) {
			editElementEntry((CPListElement) elem);
		} else if (elem instanceof CPListElementAttribute) {
			editAttributeEntry((CPListElementAttribute) elem);
		}
	}

	private void editElementEntry(CPListElement elem) {
		CPListElement res= null;
		
		IResource resource= elem.getResource();
		if (resource.exists()) {
			CPListElement[] arr= openSourceContainerDialog(elem);
			if (arr != null) {
				res= arr[0];
			}
		} else {
			res= openNewSourceContainerDialog(elem);
		}
		
		if (res != null) {
			fFoldersList.replaceElement(elem, res);
		}
	}

	private void editAttributeEntry(CPListElementAttribute elem) {
		String key= elem.getKey();
		if (key.equals(CPListElement.OUTPUT)) {
			CPListElement selElement= (CPListElement) elem.getParent();
			OutputLocationDialog dialog= new OutputLocationDialog(getShell(), selElement);
			if (dialog.open() == OutputLocationDialog.OK) {
				selElement.setAttribute(CPListElement.OUTPUT, dialog.getOutputLocation());
				fFoldersList.refresh();
				fClassPathList.dialogFieldChanged(); // validate
			}
		} else if (key.equals(CPListElement.EXCLUSION)) {
			CPListElement selElement= (CPListElement) elem.getParent();
			ExclusionPatternDialog dialog= new ExclusionPatternDialog(getShell(), selElement);
			if (dialog.open() == OutputLocationDialog.OK) {
				selElement.setAttribute(CPListElement.EXCLUSION, dialog.getExclusionPattern());
				fFoldersList.refresh();
				fClassPathList.dialogFieldChanged(); // validate
			}		
		}
	}


	
	private void sourcePageSelectionChanged(DialogField field) {
		List selected= fFoldersList.getSelectedElements();
		fFoldersList.enableButton(IDX_EDIT, canEdit(selected));
	}
	
	private boolean canEdit(List selElements) {
		if (selElements.size() != 1) {
			return false;
		}
		Object elem= selElements.get(0);
		if (fFoldersList.getIndexOfElement(elem) != -1) {
			return true;
		}
		if (elem instanceof CPListElementAttribute) {
			return true;
		}
		return false;
	}	
	
	private void sourcePageDialogFieldChanged(DialogField field) {
		if (fCurrJProject == null) {
			// not initialized
			return;
		}
		
//		if (field == fFolderRadioButton) {
//			if (fFolderRadioButton.isSelected()) {
//				fFoldersList.setElements(fFolderCPEntries);
//			}
//			updateClasspathList();
//			if (fFoldersList.getSize() > 0) {
//				askForChangingBuildPathDialog();
//			}
//		} else if (field == fProjectRadioButton) {
//			if (fProjectRadioButton.isSelected()) {
//				fFolderCPEntries= fFoldersList.getElements();
//				fFoldersList.setElements(fProjectCPEntry);
//				updateClasspathList();
//			}
//		} else
		if (field == fFoldersList) {
			updateClasspathList();
		}
	}	
	
		
	private void updateClasspathList() {
		List cpelements= fClassPathList.getElements();
		List srcelements= fFoldersList.getElements();

		boolean changeDone= false;
		CPListElement lastSourceFolder= null;
		// backwards, as entries will be deleted
		for (int i= cpelements.size() - 1; i >= 0 ; i--) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			if (isEntryKind(cpe.getEntryKind())) {
				// if it is a source folder, but not one of the accepted entries, remove it
				// at the same time, for the entries seen, remove them from the accepted list
				if (!srcelements.remove(cpe)) {
					cpelements.remove(i);
					changeDone= true;
				} else if (lastSourceFolder == null) {
					lastSourceFolder= cpe;
				}
			}
		}

		if (!srcelements.isEmpty()) {
			int insertIndex= (lastSourceFolder == null) ? 0 : cpelements.indexOf(lastSourceFolder) + 1;
			cpelements.addAll(insertIndex, srcelements);
			changeDone= true;
		}

		if (changeDone) {
			fClassPathList.setElements(cpelements);
		}
	}
		
	private CPListElement openNewSourceContainerDialog(CPListElement existing) {	
		String title= (existing == null) ? NewWizardMessages.getString("SourceContainerWorkbookPage.NewSourceFolderDialog.new.title") : NewWizardMessages.getString("SourceContainerWorkbookPage.NewSourceFolderDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$

		IProject proj= fCurrJProject.getProject();
		NewContainerDialog dialog= new NewContainerDialog(getShell(), title, proj, getExistingContainers(existing), existing);
		dialog.setMessage(NewWizardMessages.getFormattedString("SourceContainerWorkbookPage.NewSourceFolderDialog.description", fProjPath.toString())); //$NON-NLS-1$
		if (dialog.open() == NewContainerDialog.OK) {
			IFolder folder= dialog.getFolder();
			return newCPSourceElement(folder);
		}
		return null;
	}
	
	
	
	/**
	 * Asks to change the output folder to 'proj/bin' when no source folders were existing
	 */ 
	private void askForChangingBuildPathDialog() {
		IPath outputFolder= new Path(fOutputLocationField.getText());
		if (outputFolder.segmentCount() == 1) {
			String outputFolderName= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME);
			IPath newPath= outputFolder.append(outputFolderName);
			String title= NewWizardMessages.getString("SourceContainerWorkbookPage.ChangeOutputLocationDialog.title"); //$NON-NLS-1$
			String message= NewWizardMessages.getFormattedString("SourceContainerWorkbookPage.ChangeOutputLocationDialog.message", newPath); //$NON-NLS-1$
			if (MessageDialog.openQuestion(getShell(), title, message)) {
				fOutputLocationField.setText(newPath.toString());
			}
		}
	}
	
			
			
	private CPListElement[] openSourceContainerDialog(CPListElement existing) {
		
		Class[] acceptedClasses= new Class[] { IProject.class, IFolder.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, existing == null);
		
		IProject[] allProjects= fWorkspaceRoot.getProjects();
		ArrayList rejectedElements= new ArrayList(allProjects.length);
		IProject currProject= fCurrJProject.getProject();
		for (int i= 0; i < allProjects.length; i++) {
			if (!allProjects[i].equals(currProject)) {
				rejectedElements.add(allProjects[i]);
			}
		}
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());
		
		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		String title= (existing == null) ? NewWizardMessages.getString("SourceContainerWorkbookPage.ExistingSourceFolderDialog.new.title") : NewWizardMessages.getString("SourceContainerWorkbookPage.ExistingSourceFolderDialog.edit.title"); //$NON-NLS-1$ //$NON-NLS-2$
		String message= (existing == null) ? NewWizardMessages.getString("SourceContainerWorkbookPage.ExistingSourceFolderDialog.new.description") : NewWizardMessages.getString("SourceContainerWorkbookPage.ExistingSourceFolderDialog.edit.description"); //$NON-NLS-1$ //$NON-NLS-2$

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), lp, cp);
		dialog.setValidator(validator);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(fCurrJProject.getProject().getParent());
		dialog.setSorter(new ResourceSorter(ResourceSorter.NAME));
		if (existing == null) {
			dialog.setInitialSelection(fCurrJProject.getProject());
		} else {
			dialog.setInitialSelection(existing.getResource());
		}		
		if (dialog.open() == ElementTreeSelectionDialog.OK) {
			Object[] elements= dialog.getResult();
			CPListElement[] res= new CPListElement[elements.length];
			for (int i= 0; i < res.length; i++) {
				IResource elem= (IResource)elements[i];
				res[i]= newCPSourceElement(elem);
			}
			return res;
		}
		return null;
	}
	
	private IContainer[] getExistingContainers(CPListElement existing) {
		List res= new ArrayList();
		List cplist= fFoldersList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= (CPListElement)cplist.get(i);
			if (elem != existing) {
				IResource resource= elem.getResource();
				if (resource instanceof IContainer) { // defensive code
					res.add(resource);	
				}
			}
		}
		return (IContainer[]) res.toArray(new IContainer[res.size()]);
	}
	
	private CPListElement newCPSourceElement(IResource res) {
		Assert.isNotNull(res);
		return new CPListElement(fCurrJProject, IClasspathEntry.CPE_SOURCE, res.getFullPath(), res);
	}
	
	/*
	 * @see BuildPathBasePage#getSelection
	 */
	public List getSelection() {
		return fFoldersList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */	
	public void setSelection(List selElements) {
		fFoldersList.selectElements(new StructuredSelection(selElements));
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	public boolean isEntryKind(int kind) {
		return kind == IClasspathEntry.CPE_SOURCE;
	}	

}