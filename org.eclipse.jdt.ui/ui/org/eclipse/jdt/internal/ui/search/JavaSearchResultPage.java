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

import java.util.HashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.search.ui.IContextMenuConstants;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.SearchUI;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.texteditor.ITextEditor;

public class JavaSearchResultPage extends AbstractTextSearchViewPage {
	private NewSearchViewActionGroup fActionGroup;
	private JavaSearchContentProvider fContentProvider;
	private int fCurrentSortOrder;
	private SortAction fSortByNameAction;
	private SortAction fSortByParentName;
	private SortAction fSortByPathAction;
	
	private GroupAction fGroupTypeAction;
	private GroupAction fGroupFileAction;
	private GroupAction fGroupPackageAction;
	private GroupAction fGroupProjectAction;
	private int fCurrentGrouping;
	
	public JavaSearchResultPage() {
		fSortByNameAction= new SortAction(SearchMessages.getString("JavaSearchResultPage.sortByName"), this, JavaSearchResultLabelProvider.SHOW_ELEMENT_CONTAINER); //$NON-NLS-1$
		fSortByPathAction= new SortAction(SearchMessages.getString("JavaSearchResultPage.sortByPath"), this, JavaSearchResultLabelProvider.SHOW_PATH); //$NON-NLS-1$
		fSortByParentName= new SortAction(SearchMessages.getString("JavaSearchResultPage.sortByParentName"), this, JavaSearchResultLabelProvider.SHOW_CONTAINER_ELEMENT); //$NON-NLS-1$
		fCurrentSortOrder=  JavaSearchResultLabelProvider.SHOW_ELEMENT_CONTAINER;

		initGroupingActions();
	}

	private void initGroupingActions() {
		fGroupProjectAction= new GroupAction(SearchMessages.getString("JavaSearchResultPage.groupby_project"), this, LevelTreeContentProvider.LEVEL_PROJECT); //$NON-NLS-1$
		JavaPluginImages.setLocalImageDescriptors(fGroupProjectAction, "prj_mode.gif"); //$NON-NLS-1$
		fGroupPackageAction= new GroupAction(SearchMessages.getString("JavaSearchResultPage.groupby_package"), this, LevelTreeContentProvider.LEVEL_PACKAGE); //$NON-NLS-1$
		JavaPluginImages.setLocalImageDescriptors(fGroupPackageAction, "package_mode.gif"); //$NON-NLS-1$
		fGroupFileAction= new GroupAction(SearchMessages.getString("JavaSearchResultPage.groupby_file"), this, LevelTreeContentProvider.LEVEL_FILE); //$NON-NLS-1$
		JavaPluginImages.setLocalImageDescriptors(fGroupFileAction, "file_mode.gif"); //$NON-NLS-1$
		fGroupTypeAction= new GroupAction(SearchMessages.getString("JavaSearchResultPage.groupby_type"), this, LevelTreeContentProvider.LEVEL_TYPE); //$NON-NLS-1$
		JavaPluginImages.setLocalImageDescriptors(fGroupTypeAction, "class_obj.gif"); //$NON-NLS-1$
		fCurrentGrouping= LevelTreeContentProvider.LEVEL_PACKAGE;
	}

	public void setViewPart(ISearchResultViewPart part) {
		// TODO Auto-generated method stub
		super.setViewPart(part);
		fActionGroup= new NewSearchViewActionGroup(part);
	}
	
	public void showMatch(Object element, int offset, int length) throws PartInitException {
		IEditorPart editor= null;
		if (element instanceof IJavaElement) {
			IJavaElement javaElement= (IJavaElement) element;
			try {
				editor= EditorUtility.openInEditor(javaElement, false);
			} catch (PartInitException e1) {
				return;
			} catch (JavaModelException e1) {
				return;
			}
		} else if (element instanceof IFile) {
			editor= IDE.openEditor(JavaPlugin.getActivePage(), (IFile) element, false);
		}
		if (!(editor instanceof ITextEditor)) {
			if (element instanceof IFile) {
				IFile file= (IFile) element;
				showWithMarker(editor, file, offset, length);
			}
		} else {
			ITextEditor textEditor= (ITextEditor) editor;
			textEditor.selectAndReveal(offset, length);
		}
	}
	
	private void showWithMarker(IEditorPart editor, IFile file, int offset, int length) throws PartInitException {
		try {
			IMarker marker= file.createMarker(SearchUI.SEARCH_MARKER);
			HashMap attributes= new HashMap(4);
			attributes.put(IMarker.CHAR_START, new Integer(offset));
			attributes.put(IMarker.CHAR_END, new Integer(offset + length));
			marker.setAttributes(attributes);
			IDE.gotoMarker(editor, marker);
			marker.delete();
		} catch (CoreException e) {
			throw new PartInitException(SearchMessages.getString("JavaSearchResultPage.error.marker"), e); //$NON-NLS-1$
		}
	}

	protected void fillContextMenu(IMenuManager mgr) {
		super.fillContextMenu(mgr);
		addSortActions(mgr);
		fActionGroup.setContext(new ActionContext(getSite().getSelectionProvider().getSelection()));
		fActionGroup.fillContextMenu(mgr);
	}
	
	private void addSortActions(IMenuManager mgr) {
		if (!isFlatLayout())
			return;
		MenuManager sortMenu= new MenuManager(SearchMessages.getString("JavaSearchResultPage.sortBylabel")); //$NON-NLS-1$
		sortMenu.add(fSortByNameAction);
		sortMenu.add(fSortByPathAction);
		sortMenu.add(fSortByParentName);
		
		fSortByNameAction.setChecked(fCurrentSortOrder == fSortByNameAction.getSortOrder());
		fSortByPathAction.setChecked(fCurrentSortOrder == fSortByPathAction.getSortOrder());
		fSortByParentName.setChecked(fCurrentSortOrder == fSortByParentName.getSortOrder());
		
		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, sortMenu);
	}
	
	protected void fillToolbar(IToolBarManager tbm) {
		super.fillToolbar(tbm);
		if (!isFlatLayout())
			addGroupActions(tbm);
	}
	
	private void addGroupActions(IToolBarManager mgr) {
		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, fGroupProjectAction);
		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, fGroupPackageAction);
		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, fGroupFileAction);
		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, fGroupTypeAction);
		
		updateGroupingActions();
	}


	private void updateGroupingActions() {
		fGroupProjectAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_PROJECT);
		fGroupPackageAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_PACKAGE);
		fGroupFileAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_FILE);
		fGroupTypeAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_TYPE);
	}

	public void dispose() {
		fActionGroup.dispose();
		super.dispose();
	}
	
	protected void elementsChanged(Object[] objects) {
		if (fContentProvider != null)
			fContentProvider.elementsChanged(objects);
	}

	protected void clear() {
		if (fContentProvider != null)
			fContentProvider.clear();
	}

	protected void configureViewer(StructuredViewer viewer) {
		if (viewer instanceof TreeViewer) {
			viewer.setSorter(new ViewerSorter());
			viewer.setLabelProvider(new PostfixLabelProvider(this));
			fContentProvider= new LevelTreeContentProvider((TreeViewer) viewer, fCurrentGrouping);
			viewer.setContentProvider(fContentProvider);
		} else {
			viewer.setLabelProvider(new DelegatingLabelProvider(this, new JavaSearchResultLabelProvider()));
			fContentProvider=new JavaSearchTableContentProvider((TableViewer) viewer);
			viewer.setContentProvider(fContentProvider);
			setSortOrder(fCurrentSortOrder);
		}
	}

	void setSortOrder(int order) {
		fCurrentSortOrder= order;
		StructuredViewer viewer= getViewer();
		DelegatingLabelProvider lpWrapper= (DelegatingLabelProvider) viewer.getLabelProvider();
		((JavaSearchResultLabelProvider)lpWrapper.getLabelProvider()).setOrder(order);
		if (order == JavaSearchResultLabelProvider.SHOW_ELEMENT_CONTAINER) {
			viewer.setSorter(new NameSorter());
		} else if (order == JavaSearchResultLabelProvider.SHOW_PATH) {
			viewer.setSorter(new PathSorter());
		} else
			viewer.setSorter(new ParentSorter());
	}

	public void init(IPageSite site) {
		super.init(site);
		fActionGroup.fillActionBars(site.getActionBars());
	}

	/**
	 * Precondition here: the viewer must be shwing a tree with a LevelContentProvider.
	 * @param order
	 */
	void setGrouping(int grouping) {
		fCurrentGrouping= grouping;
		StructuredViewer viewer= getViewer();
		LevelTreeContentProvider cp= (LevelTreeContentProvider) viewer.getContentProvider();
		cp.setLevel(grouping);
		updateGroupingActions();
	}
	
	protected StructuredViewer getViewer() {
		// override so that it's visible in the package.
		return super.getViewer();
	}
}
