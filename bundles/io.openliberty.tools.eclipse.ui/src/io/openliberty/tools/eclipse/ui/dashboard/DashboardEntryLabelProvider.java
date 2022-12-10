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
package io.openliberty.tools.eclipse.ui.dashboard;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Table label provider for entries in the table containing the dashboard content.
 */
public class DashboardEntryLabelProvider extends LabelProvider implements ITableLabelProvider {

    /**
     * Image representing a Maven project.
     */
    private Image mavenImg;

    /**
     * Image representing a Gradle project.
     */
    private Image gradleImg;

    /**
     * DevModeOperations reference.
     */
    private DevModeOperations devModeOps;

    /**
     * Constructor.
     * 
     * @param devModeOps DevModeOperations instance.
     */
    public DashboardEntryLabelProvider(DevModeOperations devModeOps) {
        this.devModeOps = devModeOps;
        Display display = PlatformUI.getWorkbench().getDisplay();
        mavenImg = Utils.getImage(display, DashboardView.MAVEN_IMG_TAG_PATH);
        gradleImg = Utils.getImage(display, DashboardView.GRADLE_IMG_TAG_PATH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
        // Currently, the table under which the dashboard content is organized consists of a single
        // column and a single row. The content is the string containing the name of the project.
        String projectName = null;
        Image img = null;
        if (element != null && element instanceof String) {
            projectName = (String) element;
            Project project = devModeOps.getProjectModel().getProject(projectName);

            if (project != null) {
                if (project.getBuildType() == Project.BuildType.GRADLE) {
                    img = gradleImg;
                } else {
                    img = mavenImg;
                }
            }
        }

        return img;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnText(Object element, int columnIndex) {
        // Currently the table consists of a single column and a single row, and the
        // content is a string containing the name of the project.
        // Therefore, the columnIndex is not used.
        String columnText = null;
        if (element != null && element instanceof String) {
            columnText = element.toString();
        }

        return columnText;
    }

    @Override
    public void dispose() {
        if (gradleImg != null) {
            gradleImg.dispose();
        }
        if (mavenImg != null) {
            mavenImg.dispose();
        }
    }

}
