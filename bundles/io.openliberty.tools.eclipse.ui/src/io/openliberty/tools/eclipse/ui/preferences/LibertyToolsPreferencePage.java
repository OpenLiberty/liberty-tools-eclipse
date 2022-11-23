/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial implementation
*******************************************************************************/
package io.openliberty.tools.eclipse.ui.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class LibertyToolsPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public LibertyToolsPreferencePage() {
        super(GRID);
    }

    public void createFieldEditors() {
        addField(new DirectoryFieldEditor("MVNPATH", "&Maven Install Location:", getFieldEditorParent()));
        addField(new DirectoryFieldEditor("GRADLEPATH", "&Gradle Install Location:", getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
        // second parameter is typically the plug-in id
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, "io.openliberty.tools.eclipse.ui"));
        setDescription("Specify the Maven and Gradle installation locations to be used for starting the application in dev mode");
    }
}
