package org.eclipse.jdt.internal.corext.refactoring.reorg;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRegion;

/**
 * Utility class used to get better source ranges for <code>ISourceReference</code>.
 */
public class SourceReferenceSourceRangeComputer {
	
	private ISourceReference fSourceReference;
	private ICompilationUnit fCu;
	
	private SourceReferenceSourceRangeComputer(ISourceReference element, ICompilationUnit cu){
		Assert.isTrue(cu.exists());
		fCu= cu;
		Assert.isTrue(((IJavaElement)element).exists());
		fSourceReference= element;
	}
	
	/**
	 * Returns the computed source of the elements.
	 * @see SourceReferenceSourceRangeComputer#computeSourceRange(ISourceReference, ICompilationUnit)
	 */
	public static String computeSource(ISourceReference elem) throws JavaModelException{
		ICompilationUnit cu= SourceReferenceUtil.getCompilationUnit(elem);
		ISourceRange range= SourceReferenceSourceRangeComputer.computeSourceRange(elem, cu);
		int endIndex= range.getOffset() + range.getLength();
		return cu.getSource().substring(range.getOffset(), endIndex);
	}
	
	public static ISourceRange computeSourceRange(ISourceReference element, ICompilationUnit cu) throws JavaModelException{
		try{
			if ((element instanceof IJavaElement) && ! ((IJavaElement)element).exists())
				return element.getSourceRange();
			if (! cu.exists())
				return element.getSourceRange();
			
		 	SourceReferenceSourceRangeComputer inst= new SourceReferenceSourceRangeComputer(element, cu);
		 	int offset= inst.computeOffset();
		 	int end= inst.computeEnd();
		 	int length= end - offset;
		 	return new SourceRange(offset, length);
		}	catch(CoreException e){
			//fall back to the default
			return element.getSourceRange();
		}	
	}
	
	private int computeEnd() throws CoreException{
		int end= fSourceReference.getSourceRange().getOffset() + fSourceReference.getSourceRange().getLength();
		try{	
			IScanner scanner= ToolFactory.createScanner(true, true, false, true);
			String source= fCu.getSource();
			scanner.setSource(source.toCharArray());
			scanner.resetTo(end, Integer.MAX_VALUE);
			TextBuffer buff= TextBuffer.create(source);
			int startLine= buff.getLineOfOffset(scanner.getCurrentTokenEndPosition() + 1);
			
			int token= scanner.getNextToken();
			while (token != ITerminalSymbols.TokenNameEOF) {
				switch (token) {
					case ITerminalSymbols.TokenNameWHITESPACE:
						break;
					case ITerminalSymbols.TokenNameSEMICOLON:
						break;	
					case ITerminalSymbols.TokenNameCOMMENT_LINE :
						break;
					default:{
						int currentLine= buff.getLineOfOffset(scanner.getCurrentTokenEndPosition() + 1);
						if (startLine == currentLine)
							return scanner.getCurrentTokenEndPosition() - scanner.getCurrentTokenSource().length + 1;
						TextRegion nextLine= buff.getLineInformation(startLine + 1);
						if (nextLine != null)
							return nextLine.getOffset();
						else
							return end; //fallback	
					}	
				}
				token= scanner.getNextToken();
			}
			return end;
		} catch (InvalidInputException e){
			return end;//fallback
		}
	}
	
	private int computeOffset() throws CoreException{
		int offset= fSourceReference.getSourceRange().getOffset();
		try{
			TextBuffer buff= TextBuffer.create(fCu.getSource());
			String lineSource= buff.getLineContentOfOffset(offset);
			int lineOffset= buff.getLineInformationOfOffset(offset).getOffset();
			int offsetDiff= offset- lineOffset;
			
			IScanner scanner= ToolFactory.createScanner(true, true, false, true);
			scanner.setSource(lineSource.toCharArray());
			scanner.resetTo(0, Integer.MAX_VALUE);
			
			int token= scanner.getNextToken();
			while (token != ITerminalSymbols.TokenNameEOF) {
				switch (token) {
					case ITerminalSymbols.TokenNameWHITESPACE:
						break;
					case ITerminalSymbols.TokenNameSEMICOLON:
						break;	
					case ITerminalSymbols.TokenNameCOMMENT_LINE :
						break;
					case ITerminalSymbols.TokenNameCOMMENT_JAVADOC :
						break;		
					case ITerminalSymbols.TokenNameCOMMENT_BLOCK :
						break;			
					default:
						if (offsetDiff == scanner.getCurrentTokenEndPosition() - scanner.getCurrentTokenSource().length + 1)
							return lineOffset;
						else
							return offset;	
				}
				token= scanner.getNextToken();
			}
			return offset;	//should never get here really
		} catch (InvalidInputException e){
			return offset;//fallback
		}
	}
}

