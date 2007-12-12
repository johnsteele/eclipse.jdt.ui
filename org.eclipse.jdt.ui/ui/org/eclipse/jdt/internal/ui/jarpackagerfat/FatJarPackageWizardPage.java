/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 211045 [jar application] program arguments are ignored
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarpackager.AbstractJarDestinationWizardPage;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackagerUtil;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;
import org.eclipse.jdt.internal.ui.util.MainMethodSearchEngine;

/**
 * First page for the runnable jar export wizard
 * @since 3.4
 */
public class FatJarPackageWizardPage extends AbstractJarDestinationWizardPage implements Listener {

	private abstract static class LaunchConfigurationElement {

		public abstract ILaunchConfiguration getLaunchConfiguration();

		public abstract String getLaunchConfigurationName();
		
		public abstract boolean hasProgramArguments();

		public abstract boolean hasVMArguments();
		
		public void dispose() {
			//do nothing
		}
	}

	private static class ExistingLaunchConfigurationElement extends LaunchConfigurationElement {

		private final ILaunchConfiguration fLaunchConfiguration;
		private final String fProjectName;

		public ExistingLaunchConfigurationElement(ILaunchConfiguration launchConfiguration, String projectName) {
			fLaunchConfiguration= launchConfiguration;
			fProjectName= projectName;
		}

		/**
		 * {@inheritDoc}
		 */
		public ILaunchConfiguration getLaunchConfiguration() {
			return fLaunchConfiguration;
		}

		/**
		 * {@inheritDoc}
		 */
		public String getLaunchConfigurationName() {
			StringBuffer result= new StringBuffer();
			
			result.append(fLaunchConfiguration.getName());
			result.append(" - "); //$NON-NLS-1$
			result.append(fProjectName);
			
			return result.toString();
		}

		public boolean hasProgramArguments() {
			try {
				return fLaunchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, (String) null) != null;
			} catch (CoreException e) {
				JavaPlugin.log(e);
				return false;
			}
		}

		public boolean hasVMArguments() {
			try {
				return fLaunchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, (String) null) != null;
			} catch (CoreException e) {
				JavaPlugin.log(e);
				return false;
			}
		}
		
	}

	private static final String PAGE_NAME= "FatJarPackageWizardPage"; //$NON-NLS-1$
	private static final String STORE_LAUNCH_CONFIGURATION_SELECTION_NAME= PAGE_NAME + ".LAUNCH_CONFIGURATION_SELECTION_NAME"; //$NON-NLS-1$
	private static final String STORE_DESTINATION_ELEMENT= PAGE_NAME + ".DESTINATION_PATH_SELECTION"; //$NON-NLS-1$

	private final JarPackageData fJarPackage;
	/**
	 * Model for the launch combo box. Element: {@link LaunchConfigurationElement}
	 */
	private final ArrayList fLauchConfigurationModel;

	private Combo fLaunchConfigurationCombo;

	public FatJarPackageWizardPage(JarPackageData jarPackage, IStructuredSelection selection) {
		super(PAGE_NAME, selection, jarPackage);
		setTitle(FatJarPackagerMessages.JarPackageWizardPage_title);
		setDescription(FatJarPackagerMessages.FatJarPackageWizardPage_description);
		fJarPackage= jarPackage;
		fLauchConfigurationModel= new ArrayList();
	}

	/**
	 * {@inheritDoc}
	 */
	public void createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		composite.setLayout(new GridLayout(1, false));

		Label description= new Label(composite, SWT.NONE);
		GridData gridData= new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		description.setLayoutData(gridData);
		description.setText(FatJarPackagerMessages.FatJarPackageWizardPage_launchConfigGroupTitle);

		createLaunchConfigSelectionGroup(composite);

		Label label= new Label(composite, SWT.NONE);
		label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		label.setText(FatJarPackagerMessages.FatJarPackageWizardPage_destinationGroupTitle);

		createDestinationGroup(composite);

		restoreWidgetValues();

		update();

		Dialog.applyDialogFont(composite);
		setControl(composite);
	}

	protected String getDestinationLabel() {
		return null;
	}

	private void createLaunchConfigSelectionGroup(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout layout= new GridLayout(1, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		composite.setLayout(layout);

		fLaunchConfigurationCombo= new Combo(composite, SWT.DROP_DOWN | SWT.READ_ONLY);
		fLaunchConfigurationCombo.setVisibleItemCount(20);
		fLaunchConfigurationCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		fLauchConfigurationModel.addAll(Arrays.asList(getLaunchConfigurations()));
		String[] names= new String[fLauchConfigurationModel.size()];
		for (int i= 0, size= fLauchConfigurationModel.size(); i < size; i++) {
			LaunchConfigurationElement element= (LaunchConfigurationElement) fLauchConfigurationModel.get(i);
			names[i]= element.getLaunchConfigurationName();
		}
		fLaunchConfigurationCombo.setItems(names);

		fLaunchConfigurationCombo.addListener(SWT.Selection, this);
		fLaunchConfigurationCombo.addListener(SWT.Modify, this);
	}

	protected void updateWidgetEnablements() {
	}

	public boolean isPageComplete() {
		clearMessages();
		boolean complete= validateDestinationGroup();
		complete= validateLaunchConfigurationGroup() && complete;
		return complete;
	}

	private boolean validateLaunchConfigurationGroup() {
		int index= fLaunchConfigurationCombo.getSelectionIndex();
		if (index == -1)
			return false;
		
		LaunchConfigurationElement element= (LaunchConfigurationElement) fLauchConfigurationModel.get(index);
		if (element.hasProgramArguments())
			setWarningMessage(FatJarPackagerMessages.FatJarPackageWizardPage_warning_launchConfigContainsProgramArgs);
		
		if (element.hasVMArguments())
			setWarningMessage(FatJarPackagerMessages.FatJarPackageWizardPage_warning_launchConfigContainsVMArgs);
		
		return true;
	}

	/**
	 * clear all previously set messages and error-messages 
	 */
	private void clearMessages() {
		if (getErrorMessage() != null)
			setErrorMessage(null);
		if (getMessage() != null)
			setMessage(null);
	}

	/**
	 * set message to newMessage with severity WARNING.
	 * overwrite existing message only if it is beyond severity WARNING
	 * @param newMessage the warning to be set
	 */
	private void setWarningMessage(String newMessage) {
		if (getMessage() == null || getMessageType() < IMessageProvider.WARNING)
			setMessage(newMessage, IMessageProvider.WARNING);
	}
	
	private LaunchConfigurationElement[] getLaunchConfigurations() {
		ArrayList result= new ArrayList();

		try {
			ILaunchManager manager= DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type= manager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
			ILaunchConfiguration[] launchconfigs= manager.getLaunchConfigurations(type);

			for (int i= 0; i < launchconfigs.length; i++) {
				ILaunchConfiguration launchconfig= launchconfigs[i];

				String projectName= launchconfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
				result.add(new ExistingLaunchConfigurationElement(launchconfig, projectName));
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}

		return (LaunchConfigurationElement[]) result.toArray(new LaunchConfigurationElement[result.size()]);
	}

	public Object[] getSelectedElementsWithoutContainedChildren(MultiStatus status) {
		try {
			LaunchConfigurationElement element= (LaunchConfigurationElement) fLauchConfigurationModel.get(fLaunchConfigurationCombo.getSelectionIndex());
			ILaunchConfiguration launchconfig= element.getLaunchConfiguration();
			fJarPackage.setLaunchConfigurationName(element.getLaunchConfigurationName());

			return getSelectedElementsWithoutContainedChildren(launchconfig, fJarPackage, getContainer(), status);
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}

		return null;
	}

	private static IJavaProject[] getProjectSearchOrder(String projectName) {

		ArrayList projectNames= new ArrayList();
		projectNames.add(projectName);

		int nextProject= 0;
		while (nextProject < projectNames.size()) {
			String nextProjectName= (String) projectNames.get(nextProject);
			IJavaProject jproject= getJavaProject(nextProjectName);

			if (jproject != null) {
				try {
					String[] childProjectNames= jproject.getRequiredProjectNames();
					for (int i= 0; i < childProjectNames.length; i++) {
						if (!projectNames.contains(childProjectNames[i])) {
							projectNames.add(childProjectNames[i]);
						}
					}
				} catch (JavaModelException e) {
					JavaPlugin.log(e);
				}
			}
			nextProject+= 1;
		}

		ArrayList result= new ArrayList();
		for (int i= 0, size= projectNames.size(); i < size; i++) {
			String name= (String) projectNames.get(i);
			IJavaProject project= getJavaProject(name);
			if (project != null)
				result.add(project);
		}

		return (IJavaProject[]) result.toArray(new IJavaProject[result.size()]);
	}

	private static IJavaProject getJavaProject(String projectName) {
		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project == null)
			return null;

		IJavaProject result= JavaCore.create(project);
		if (result == null)
			return null;

		if (!result.exists())
			return null;

		return result;
	}

	private static IPath[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
		IRuntimeClasspathEntry[] entries= JavaRuntime.computeUnresolvedRuntimeClasspath(configuration);
		entries= JavaRuntime.resolveRuntimeClasspath(entries, configuration);

		ArrayList userEntries= new ArrayList(entries.length);
		for (int i= 0; i < entries.length; i++) {
			if (entries[i].getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {

				String location= entries[i].getLocation();
				if (location != null) {
					IPath entry= Path.fromOSString(location);
					if (!userEntries.contains(entry)) {
						userEntries.add(entry);
					}
				}
			}
		}
		return (IPath[]) userEntries.toArray(new IPath[userEntries.size()]);
	}

	private static String getMainClass(ILaunchConfiguration launchConfig, MultiStatus status) {
		String result= null;
		if (launchConfig != null) {
			try {
				result= launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String) null);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
		if (result == null) {
			status.add(new Status(IStatus.WARNING, JavaUI.ID_PLUGIN, FatJarPackagerMessages.FatJarPackageWizardPage_LaunchConfigurationWithoutMainType_warning));
			result= ""; //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * @param classpathEntries the path to the package fragment roots
	 * @param projectName the root of the project dependency tree
	 * @param status a status to report problems to
	 * @return all package fragment roots corresponding to each classpath entry start the search at project with projectName
	 */
	private static IPackageFragmentRoot[] getRequiredPackageFragmentRoots(IPath[] classpathEntries, final String projectName, MultiStatus status) {
		HashSet result= new HashSet();

		IJavaProject[] searchOrder= getProjectSearchOrder(projectName);

		for (int i= 0; i < classpathEntries.length; i++) {
			IPath entry= classpathEntries[i];
			IPackageFragmentRoot[] elements= findRootsForClasspath(entry, searchOrder);
			if (elements == null) {
				status.add(new Status(IStatus.WARNING, JavaUI.ID_PLUGIN, Messages.format(FatJarPackagerMessages.FatJarPackageWizardPage_error_missingClassFile, entry)));
			} else {
				for (int j= 0; j < elements.length; j++) {
					result.add(elements[j]);
				}
			}
		}

		return (IPackageFragmentRoot[]) result.toArray(new IPackageFragmentRoot[result.size()]);
	}

	private static IPackageFragmentRoot[] findRootsForClasspath(IPath entry, IJavaProject[] searchOrder) {
		for (int i= 0; i < searchOrder.length; i++) {
			IPackageFragmentRoot[] elements= findRootsInProject(entry, searchOrder[i]);
			if (elements.length != 0) {
				return elements;
			}
		}
		return null;
	}

	private static IPackageFragmentRoot[] findRootsInProject(IPath entry, IJavaProject project) {
		ArrayList result= new ArrayList();

		try {
			IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
			for (int i= 0; i < roots.length; i++) {
				IPackageFragmentRoot packageFragmentRoot= roots[i];
				if (isRootAt(packageFragmentRoot, entry))
					result.add(packageFragmentRoot);
			}
		} catch (Exception e) {
			JavaPlugin.log(e);
		}

		return (IPackageFragmentRoot[]) result.toArray(new IPackageFragmentRoot[result.size()]);
	}

	private static boolean isRootAt(IPackageFragmentRoot root, IPath entry) {
		try {
			IClasspathEntry cpe= root.getRawClasspathEntry();
			if (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				IPath outputLocation= cpe.getOutputLocation();
				if (outputLocation == null)
					outputLocation= root.getJavaProject().getOutputLocation();

				IPath location= ResourcesPlugin.getWorkspace().getRoot().findMember(outputLocation).getLocation();
				if (entry.equals(location))
					return true;
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}

		IResource resource= root.getResource();
		if (resource != null && entry.equals(resource.getLocation()))
			return true;

		IPath path= root.getPath();
		if (path != null && entry.equals(path))
			return true;

		return false;
	}

	private static IType findMainMethodByName(String name, IPackageFragmentRoot[] classpathResources, IRunnableContext context) {

		List resources= JarPackagerUtil.asResources(classpathResources);
		if (resources == null) {
			return null;
		}

		for (Iterator iterator= resources.iterator(); iterator.hasNext();) {
			IResource element= (IResource) iterator.next();
			if (element == null)
				iterator.remove();
		}

		IJavaSearchScope searchScope= JavaSearchScopeFactory.getInstance().createJavaSearchScope((IResource[]) resources.toArray(new IResource[resources.size()]), true);
		MainMethodSearchEngine engine= new MainMethodSearchEngine();
		try {
			IType[] mainTypes= engine.searchMainMethods(context, searchScope, 0);
			for (int i= 0; i < mainTypes.length; i++) {
				if (mainTypes[i].getFullyQualifiedName().equals(name))
					return mainTypes[i];
			}
		} catch (InvocationTargetException ex) {
			JavaPlugin.log(ex);
		} catch (InterruptedException e) {
			// null
		}

		return null;
	}

	public void dispose() {
		super.dispose();
		if (fLauchConfigurationModel != null) {
			for (int i= 0, size= fLauchConfigurationModel.size(); i < size; i++) {
				LaunchConfigurationElement element= (LaunchConfigurationElement) fLauchConfigurationModel.get(i);
				element.dispose();
			}
		}
	}

	protected void restoreWidgetValues() {

		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			String name= settings.get(STORE_LAUNCH_CONFIGURATION_SELECTION_NAME);
			if (name != null) {
				String[] items= fLaunchConfigurationCombo.getItems();
				for (int i= 0; i < items.length; i++) {
					if (name.equals(items[i])) {
						fLaunchConfigurationCombo.select(i);
					}
				}
			}

			String destinationPath= settings.get(STORE_DESTINATION_ELEMENT);
			if (destinationPath != null && destinationPath.length() > 0) {
				fJarPackage.setJarLocation(Path.fromOSString(destinationPath));
			}
		}

		super.restoreWidgetValues();
	}

	/**
	 * {@inheritDoc}
	 */
	protected void saveWidgetValues() {
		super.saveWidgetValues();

		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			int index= fLaunchConfigurationCombo.getSelectionIndex();
			if (index == -1) {
				settings.put(STORE_LAUNCH_CONFIGURATION_SELECTION_NAME, ""); //$NON-NLS-1$
			} else {
				String selectedItem= fLaunchConfigurationCombo.getItem(index);
				settings.put(STORE_LAUNCH_CONFIGURATION_SELECTION_NAME, selectedItem);
			}

			IPath location= fJarPackage.getJarLocation();
			if (location == null) {
				settings.put(STORE_DESTINATION_ELEMENT, ""); //$NON-NLS-1$
			} else {
				settings.put(STORE_DESTINATION_ELEMENT, location.toOSString());
			}
		}
	}

	/*
	 * For internal use only (testing), clients must not call.
	 */
	public static Object[] getSelectedElementsWithoutContainedChildren(ILaunchConfiguration launchconfig, JarPackageData data, IRunnableContext context, MultiStatus status) throws CoreException {
		if (launchconfig == null)
			return new Object[0];

		String projectName= launchconfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""); //$NON-NLS-1$

		IPath[] classpath= getClasspath(launchconfig);
		IPackageFragmentRoot[] classpathResources= getRequiredPackageFragmentRoots(classpath, projectName, status);

		String mainClass= getMainClass(launchconfig, status);
		IType mainType= findMainMethodByName(mainClass, classpathResources, context);
		if (mainType == null) {
			status.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, FatJarPackagerMessages.FatJarPackageWizardPage_error_noMainMethod));
		}
		data.setManifestMainClass(mainType);

		return classpathResources;
	}

}