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
package org.eclipse.jdt.ui.text.java;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
/**
  */
public interface IInvocationContext {

	/**
	 * Returns the current compilation unit
	 */
	ICompilationUnit getCompilationUnit();

	/**
	 * Returns the length of the current selection
	 * @return int
	 */
	int getSelectionLength();
	
	/**
	 * Returns the offset of the current selection
	 * @return int
	 */
	int getSelectionOffset();
		
	/**
	 * Creates an partial AST on the compilation unit and the selection offset. Te returned AST is shared and must
	 * not be modified (or modifications must be reverted back again)
	 * @return CompilationUnit
	 */
	CompilationUnit getASTRoot();

}
