/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.actions.WorkspaceModifyOperation;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.JavaPluginImages;

public class JavaSearchOperation extends WorkspaceModifyOperation {
	
	private IWorkspace fWorkspace;
	private IJavaElement fElementPattern;
	private int fLimitTo;
	private String fStringPattern;
	private int fSearchFor;
	private IJavaSearchScope fScope;
	private String fScopeDescription;
	private JavaSearchResultCollector fCollector;
	
	protected JavaSearchOperation(
				IWorkspace workspace,
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				JavaSearchResultCollector collector) {
		fWorkspace= workspace;
		fLimitTo= limitTo;
		fScope= scope;
		fScopeDescription= scopeDescription;
		fCollector= collector;
		fCollector.setOperation(this);
	}
	
	public JavaSearchOperation(
				IWorkspace workspace,
				IJavaElement pattern,
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				JavaSearchResultCollector collector) {
		this(workspace, limitTo, scope, scopeDescription, collector);
		fElementPattern= pattern;
	}
	
	public JavaSearchOperation(
				IWorkspace workspace,
				String pattern,
				int searchFor, 
				int limitTo,
				IJavaSearchScope scope,
				String scopeDescription,
				JavaSearchResultCollector collector) {
		this(workspace, limitTo, scope, scopeDescription, collector);
		fStringPattern= pattern;
		fSearchFor= searchFor;
	}
	
	protected void execute(IProgressMonitor monitor) throws CoreException {
		fCollector.setProgressMonitor(monitor);
		SearchEngine engine= new SearchEngine();
		if (fElementPattern != null)
			engine.search(fWorkspace, fElementPattern, fLimitTo, fScope, fCollector);
		else
			engine.search(fWorkspace, fStringPattern, fSearchFor, fLimitTo, fScope, fCollector);
	}

	String getDescription() {
		String desc= null;
		if (fElementPattern != null) {
			if (fLimitTo == IJavaSearchConstants.REFERENCES
			&& fElementPattern.getElementType() == IJavaElement.METHOD)
				desc= PrettySignature.getUnqualifiedMethodSignature((IMethod)fElementPattern);
			else
				desc= fElementPattern.getElementName();
			if ("".equals(desc) && fElementPattern.getElementType() == IJavaElement.PACKAGE_FRAGMENT) //$NON-NLS-1$
				desc= SearchMessages.getString("JavaSearchOperation.default_package"); //$NON-NLS-1$
		}
		else
			desc= fStringPattern;

		String[] args= new String[] {desc, "{0}", fScopeDescription}; //$NON-NLS-1$
		switch (fLimitTo) {
			case IJavaSearchConstants.IMPLEMENTORS:
				return SearchMessages.getFormattedString("JavaSearchOperation.implementorsPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.DECLARATIONS:
				return SearchMessages.getFormattedString("JavaSearchOperation.declarationsPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.REFERENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.referencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.ALL_OCCURRENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.occurrencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.READ_REFERENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.readReferencesPostfix", args); //$NON-NLS-1$
			case IJavaSearchConstants.WRITE_REFERENCES:
				return SearchMessages.getFormattedString("JavaSearchOperation.writeReferencesPostfix", args); //$NON-NLS-1$
			default:
				return SearchMessages.getFormattedString("JavaSearchOperation.occurrencesPostfix", args); //$NON-NLS-1$;
		}
	}
	
	ImageDescriptor getImageDescriptor() {
		if (fLimitTo == IJavaSearchConstants.IMPLEMENTORS || fLimitTo == IJavaSearchConstants.DECLARATIONS)
			return JavaPluginImages.DESC_OBJS_SEARCH_DECL;
		else
			return JavaPluginImages.DESC_OBJS_SEARCH_REF;
	}
}