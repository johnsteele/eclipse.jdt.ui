/*******************************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jsp;

import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.indexsearch.*;
import org.eclipse.core.indexsearch.IIndexQuery;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.index.IIndex;
import org.eclipse.jdt.internal.core.index.IQueryResult;
import org.eclipse.jdt.internal.core.search.PathCollector;

/**
 * Implementation for a JSP type query.
 */
public class JspTypeQuery implements IIndexQuery {

	private IType fType;
	private JspMatchLocatorParser fParser;
	
	public JspTypeQuery(IType type) {
		fType= type;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.search.ISearch#computePathsKeyingIndexFiles(java.util.ArrayList)
	 */
	public void computePathsKeyingIndexFiles(ArrayList requiredIndexKeys) {
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IProject[] projects= workspace.getRoot().getProjects();
		try {
			for (int i= 0; i < projects.length; i++) {
				IProject project= projects[i];
				if (!project.isAccessible() || !project.hasNature(JavaCore.NATURE_ID))
					continue;
				IPath path= project.getFullPath();
				if (requiredIndexKeys.indexOf(path) == -1) {
					requiredIndexKeys.add(path);
				}
			}
		} catch (CoreException ex) {
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.search.ISearch#findIndexMatches(org.eclipse.jdt.internal.core.index.IIndex, org.eclipse.jdt.internal.core.search.PathCollector, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void findIndexMatches(IIndex index, PathCollector pathCollector, IProgressMonitor progressMonitor) throws IOException {

		String typeName= fType.getFullyQualifiedName();
		String s= JspIndexParser.JSP_TYPE_REF + "/" + typeName;

		IQueryResult[] result= index.queryPrefix(s.toCharArray());
		if (result != null)
			for (int i= 0; i < result.length; i++)
				pathCollector.acceptTypeReference(result[i].getPath(), typeName.toCharArray());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.search.ISearch#locateMatches(org.eclipse.core.resources.IFile, org.eclipse.jsp.IJspSearchResultCollector)
	 */
	public void locateMatches(IFile candidate, ISearchResultCollector resultCollector) {
		if (fParser== null)
			fParser= new JspMatchLocatorParser();
		String typeName= fType.getFullyQualifiedName();
		fParser.match(candidate, typeName, resultCollector);
	}
}
