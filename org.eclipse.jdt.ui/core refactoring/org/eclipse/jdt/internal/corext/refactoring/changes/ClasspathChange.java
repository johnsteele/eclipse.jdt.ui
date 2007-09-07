/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.changes;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JDTChange;

public class ClasspathChange extends JDTChange {
	
	public static ClasspathChange addEntryChange(IJavaProject project, IClasspathEntry entryToAdd) throws JavaModelException {
		IClasspathEntry[] rawClasspath= project.getRawClasspath();
		IClasspathEntry[] newClasspath= new IClasspathEntry[rawClasspath.length + 1];
		System.arraycopy(rawClasspath, 0, newClasspath, 0, rawClasspath.length);
		newClasspath[rawClasspath.length]= entryToAdd;
		
		IPath outputLocation= project.getOutputLocation();
		
		if (!JavaConventions.validateClasspath(project, newClasspath, outputLocation).matches(IStatus.ERROR)) {
			return new ClasspathChange(project, newClasspath, outputLocation);
		}
		return null;
	}
	
	public static ClasspathChange removeEntryChange(IJavaProject project, IClasspathEntry entryToRemove) throws JavaModelException {
		IClasspathEntry[] rawClasspath= project.getRawClasspath();
		ArrayList newClasspath= new ArrayList();
		for (int i= 0; i < rawClasspath.length; i++) {
			IClasspathEntry curr= rawClasspath[i];
			if (curr.getEntryKind() != entryToRemove.getEntryKind() || !curr.getPath().equals(entryToRemove.getPath())) {
				newClasspath.add(curr);
			}
		}
		IClasspathEntry[] entries= (IClasspathEntry[]) newClasspath.toArray(new IClasspathEntry[newClasspath.size()]);
		IPath outputLocation= project.getOutputLocation();
		if (!JavaConventions.validateClasspath(project, entries, outputLocation).matches(IStatus.ERROR)) {
			return new ClasspathChange(project, entries, outputLocation);
		}
		return null;
	}
	

	private IJavaProject fProject;
	private IClasspathEntry[] fNewClasspath;
	private final IPath fOutputLocation;

	public ClasspathChange(IJavaProject project, IClasspathEntry[] newClasspath, IPath outputLocation) {
		fProject= project;
		fNewClasspath= newClasspath;
		fOutputLocation= outputLocation;
	}
	
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		// .classpath file will be handled by JDT/Core.
		return super.isValid(pm, READ_ONLY | DIRTY);
	}
	
	public Change perform(IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.ClasspathChange_progress_message, 1);
		try {
			if (!JavaConventions.validateClasspath(fProject, fNewClasspath, fOutputLocation).matches(IStatus.ERROR)) {
				IClasspathEntry[] oldClasspath= fProject.getRawClasspath();
				IPath oldOutputLocation= fProject.getOutputLocation();
				
				fProject.setRawClasspath(fNewClasspath, fOutputLocation, new SubProgressMonitor(pm, 1));

				return new ClasspathChange(fProject, oldClasspath, oldOutputLocation);
			} else {
				return new NullChange();
			}
		} finally {
			pm.done();
		}		
	}
	
	public String getName() {
		return RefactoringCoreMessages.ClasspathChange_change_name;
	}

	public Object getModifiedElement() {
		return fProject;
	}
		
	
}