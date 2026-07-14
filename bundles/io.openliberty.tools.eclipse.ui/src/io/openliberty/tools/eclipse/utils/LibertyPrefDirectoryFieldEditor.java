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
package io.openliberty.tools.eclipse.utils;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.widgets.Composite;

public class LibertyPrefDirectoryFieldEditor extends DirectoryFieldEditor {

    /*
     * A class to provide keystroke validation when typing in location of
     * maven and gradle executables.
     * 
     * Need to subclass the DirectoryFieldEditor class in order to set the validation strategy to
     * VALIDATE_ON_KEY_STROKE
     */

    public LibertyPrefDirectoryFieldEditor(String name, String labelText, Composite parent) {
        init(name, labelText);
        setErrorMessage(JFaceResources.getString("DirectoryFieldEditor.errorMessage"));//$NON-NLS-1$
        setChangeButtonText(JFaceResources.getString("openBrowse"));//$NON-NLS-1$
        setValidateStrategy(VALIDATE_ON_KEY_STROKE);
        createControl(parent);
    }

}
