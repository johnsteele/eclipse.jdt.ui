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
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;
import org.eclipse.jdt.ui.search.ISearchUIParticipant;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.swt.graphics.Image;


public class SortingLabelProvider extends SearchLabelProvider implements IColorProvider {
	public static final int SHOW_ELEMENT_CONTAINER= 1; // default
	public static final int SHOW_CONTAINER_ELEMENT= 2;
	public static final int SHOW_PATH= 3;
	
	public SortingLabelProvider(JavaSearchResultPage page) {
		super(page, new DecoratingJavaLabelProvider(new AppearanceAwareLabelProvider(), true));
	}	

	public Image getImage(Object element) {
		Image image= null;
		if (element instanceof IJavaElement || element instanceof IResource)
			image= getLabelProvider().getImage(element);
		if (image != null)
			return image;
		ISearchUIParticipant participant= ((JavaSearchResult)fPage.getInput()).getSearchParticpant(element);
		if (participant != null)
			return participant.getImage(element);
		return null;
	}
		
	public final String getText(Object element) {
		int matchCount= fPage.getInput().getMatchCount(element);
		String text= internalGetText(element);
		if (matchCount < 2)
			return text;
		return text + " (" + matchCount + " matches)";
	}

	private String internalGetText(Object o) {
		if (o instanceof IImportDeclaration)
			o= ((IImportDeclaration)o).getParent().getParent();

		String text= getLabelProvider().getText(o);
		if (text != null && (!"".equals(text))) //$NON-NLS-1$
			return text;
		ISearchUIParticipant participant= ((JavaSearchResult)fPage.getInput()).getSearchParticpant(o);
		if (participant != null)
			return participant.getText(o);
		return ""; //$NON-NLS-1$
	}

	public void setOrder(int orderFlag) {
		int flags= AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED;
		if (orderFlag == SHOW_ELEMENT_CONTAINER)
			flags |= JavaElementLabels.F_POST_QUALIFIED | JavaElementLabels.M_POST_QUALIFIED | JavaElementLabels.I_POST_QUALIFIED | JavaElementLabels.M_PARAMETER_TYPES
							| JavaElementLabels.T_POST_QUALIFIED | JavaElementLabels.D_POST_QUALIFIED | JavaElementLabels.CF_POST_QUALIFIED  | JavaElementLabels.CU_POST_QUALIFIED;
			
		else if (orderFlag == SHOW_CONTAINER_ELEMENT)
			flags |= JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.I_FULLY_QUALIFIED | JavaElementLabels.M_PARAMETER_TYPES
				| JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.D_QUALIFIED | JavaElementLabels.CF_QUALIFIED  | JavaElementLabels.CU_QUALIFIED;
		else if (orderFlag == SHOW_PATH) {
			flags |= JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.I_FULLY_QUALIFIED | JavaElementLabels.M_PARAMETER_TYPES
				| JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.D_QUALIFIED | JavaElementLabels.CF_QUALIFIED  | JavaElementLabels.CU_QUALIFIED;
			flags |= JavaElementLabels.PREPEND_ROOT_PATH;
		}
		DecoratingJavaLabelProvider lp= (DecoratingJavaLabelProvider) getLabelProvider();
		((AppearanceAwareLabelProvider)lp.getLabelProvider()).setTextFlags(flags);
	}
}
