/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.felix.sigil.eclipse.ui.internal.editors.project;

import java.util.Iterator;
import java.util.List;

import org.apache.felix.sigil.common.model.ModelElementFactory;
import org.apache.felix.sigil.common.model.ModelElementFactoryException;
import org.apache.felix.sigil.common.model.eclipse.ISigilBundle;
import org.apache.felix.sigil.common.model.osgi.IBundleModelElement;
import org.apache.felix.sigil.common.model.osgi.IPackageExport;
import org.apache.felix.sigil.common.model.osgi.IPackageImport;
import org.apache.felix.sigil.common.osgi.VersionRange;
import org.apache.felix.sigil.eclipse.SigilCore;
import org.apache.felix.sigil.eclipse.model.project.ISigilProjectModel;
import org.apache.felix.sigil.eclipse.model.util.ModelHelper;
import org.apache.felix.sigil.eclipse.ui.internal.form.SigilPage;
import org.apache.felix.sigil.eclipse.ui.internal.preferences.OptionalPrompt;
import org.apache.felix.sigil.eclipse.ui.util.DefaultTableProvider;
import org.apache.felix.sigil.eclipse.ui.util.ResourcesDialogHelper;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.osgi.framework.Version;

public class ExportPackagesSection extends BundleDependencySection
{

    public ExportPackagesSection(SigilPage page, Composite parent, ISigilProjectModel project) throws CoreException
    {
        super(page, parent, project);
    }

    @Override
    protected String getTitle()
    {
        return "Export Packages";
    }

    @Override
    protected Label createLabel(Composite parent, FormToolkit toolkit)
    {
        return toolkit.createLabel(parent,
            "Specify which packages this bundle shares with other bundles.");
    }

    @Override
    protected IContentProvider getContentProvider()
    {
        return new DefaultTableProvider()
        {
            public Object[] getElements(Object inputElement)
            {
                return getBundle().getBundleInfo().getExports().toArray();
            }
        };
    }

    @Override
    protected void handleAdd()
    {
        NewPackageExportDialog dialog = ResourcesDialogHelper.createNewExportDialog(
            getSection().getShell(), "Add Exported Package", null, getProjectModel(),
            true);

        if (dialog.open() == Window.OK)
        {
            try
            {
                // Add selected exports
                boolean exportsAdded = false;

                List<IPackageFragment> newPkgFragments = dialog.getSelectedElements();
                for (IPackageFragment pkgFragment : newPkgFragments)
                {
                    IPackageExport pkgExport = ModelElementFactory.getInstance().newModelElement(
                        IPackageExport.class);
                    pkgExport.setPackageName(pkgFragment.getElementName());
                    pkgExport.setVersion(dialog.getVersion());
                    getBundle().getBundleInfo().addExport(pkgExport);

                    exportsAdded = true;
                }

                // Add corresponding imports (maybe)
                boolean importsAdded = false;

                boolean shouldAddImports = OptionalPrompt.optionallyPrompt(
                    SigilCore.PREFERENCES_ADD_IMPORT_FOR_EXPORT, "Add Exports",
                    "Should corresponding imports be added?", getSection().getShell());
                if (shouldAddImports)
                {
                    for (IPackageFragment pkgFragment : newPkgFragments)
                    {
                        IPackageImport pkgImport = ModelElementFactory.getInstance().newModelElement(
                            IPackageImport.class);
                        pkgImport.setPackageName(pkgFragment.getElementName());
                        Version version = dialog.getVersion();
                        if (version == null)
                        {
                            version = getBundle().getVersion();
                        }
                        VersionRange versionRange = ModelHelper.getDefaultRange(version);
                        pkgImport.setVersions(versionRange);

                        getBundle().getBundleInfo().addImport(pkgImport);

                        importsAdded = true;
                    }
                }

                if (importsAdded)
                {
                    refreshAllPages();
                    markDirty();
                }
                else if (exportsAdded)
                {
                    refresh();
                    markDirty();
                }
            }
            catch (ModelElementFactoryException e)
            {
                SigilCore.error("Failed to buiild model element for package export", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void handleEdit()
    {
        IStructuredSelection selection = (IStructuredSelection) getSelection();

        boolean changed = false;

        if (!selection.isEmpty())
        {
            for (Iterator<IPackageExport> i = selection.iterator(); i.hasNext();)
            {
                IPackageExport packageExport = i.next();
                NewPackageExportDialog dialog = ResourcesDialogHelper.createNewExportDialog(
                    getSection().getShell(), "Edit Imported Package", packageExport,
                    getProjectModel(), false);
                if (dialog.open() == Window.OK)
                {
                    changed = true;
                    IPackageFragment pkgFragment = dialog.getSelectedElement();
                    packageExport.setPackageName(pkgFragment.getElementName());
                    packageExport.setVersion(dialog.getVersion());
                }
            }
        }

        if (changed)
        {
            refresh();
            markDirty();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void handleRemoved()
    {
        IStructuredSelection selection = (IStructuredSelection) getSelection();

        if (!selection.isEmpty())
        {
            boolean importsRemoved = false;

            boolean shouldRemoveImports = OptionalPrompt.optionallyPrompt(
                SigilCore.PREFERENCES_REMOVE_IMPORT_FOR_EXPORT, "Remove Exports",
                "Should corresponding imports be removed?", getSection().getShell());

            // TODO should also prompt to remove corresponding imports from other
            // projects in workspace??
            
            IBundleModelElement info = getBundle().getBundleInfo();

            for (Iterator<IPackageExport> i = selection.iterator(); i.hasNext();)
            {
                IPackageExport pe = i.next();
                info.removeExport(pe);

                // Remove corresponding imports (maybe)
                if (shouldRemoveImports)
                {
                    for (IPackageImport pi : info.getImports())
                    {
                        if (pi.getPackageName().equals(pe.getPackageName()))
                        {
                            importsRemoved = true;
                            info.removeImport(pi);
                        }
                    }
                }
            }

            if (importsRemoved)
            {
                refreshAllPages();
            }
            else
            {
                refresh();
            }

            markDirty();
        }
    }

    /**
     * 
     */
    private void refreshAllPages()
    {
        ((SigilProjectEditorPart) getPage().getEditor()).refreshAllPages();
    }

    private ISigilBundle getBundle()
    {
        return getProjectModel().getBundle();
    }

}
