/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.ui.search.ISearchUIParticipant;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.swt.graphics.Image;

public class PostfixLabelProvider extends SearchLabelProvider {
	private ITreeContentProvider fContentProvider;
	
	public PostfixLabelProvider(JavaSearchResultPage page) {
		super(page, new DecoratingJavaLabelProvider(new AppearanceAwareLabelProvider(), true));
		fContentProvider= new LevelTreeContentProvider.FastJavaElementProvider();
	}

	public Image getImage(Object element) {
		Image image= getLabelProvider().getImage(element);
		if (image != null)
			return image;
		ISearchUIParticipant participant= ((JavaSearchResult)fPage.getInput()).getSearchParticpant(element);
		if (participant != null)
			return participant.getImage(element);
		return null;
	}
	
	public String getText(Object element) {
		ITreeContentProvider provider= (ITreeContentProvider) fPage.getViewer().getContentProvider();
		Object visibleParent= provider.getParent(element);
		Object realParent= fContentProvider.getParent(element);
		Object lastElement= element;
		StringBuffer postfix= new StringBuffer();
		while (realParent != null && !(realParent instanceof IJavaModel) && !realParent.equals(visibleParent)) {
			if (!isSameInformation(realParent, lastElement))  {
				postfix.append(" - "); //$NON-NLS-1$
				postfix.append(internalGetText(realParent));
			}
			lastElement= realParent;
			realParent= fContentProvider.getParent(realParent);
		}
		int matchCount= fPage.getInput().getMatchCount(element);
		String text=internalGetText(element);
		if (matchCount == 0)
			return text+postfix;
		if (matchCount == 1)
			return text;
		return text + " (" + matchCount + " matches)"+postfix;
	}

	private String internalGetText(Object element) {
		String text= getLabelProvider().getText(element);
		if (text != null && !"".equals(text)) //$NON-NLS-1$
			return text;
		ISearchUIParticipant participant= ((JavaSearchResult)fPage.getInput()).getSearchParticpant(element);
		if (participant != null)
			return participant.getText(element);
		return ""; //$NON-NLS-1$
	}

	private boolean isSameInformation(Object realParent, Object lastElement) {
		if (lastElement instanceof IType) {
			IType type= (IType)lastElement;
			if (realParent instanceof IClassFile) {
				if (type.getClassFile().equals(realParent))
					return true;
			} else if (realParent instanceof ICompilationUnit) {
				if (type.getCompilationUnit().equals(realParent))
					return true;
			}
		}
		return false;
	}

}
