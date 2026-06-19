/*******************************************************************************
* Copyright (c) 2022, 2026 IBM Corporation and others.
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

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Label provider for entries in the tree containing the dashboard content.
 * Uses IColorProvider for built-in Eclipse color support.
 */
public class DashboardEntryLabelProvider extends LabelProvider {

    /**
     * Image representing a Maven project.
     */
    private Image mavenImg;

    /**
     * Image representing a Gradle project.
     */
    private Image gradleImg;

    /**
     * Constructor.
     *
     * @param devModeOps    DevModeOperations instance.
     * @param dashboardView The dashboard view instance.
     */
    public DashboardEntryLabelProvider(DevModeOperations devModeOps, DashboardView dashboardView) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        mavenImg = Utils.getImage(display, DashboardView.MAVEN_IMG_TAG_PATH);
        gradleImg = Utils.getImage(display, DashboardView.GRADLE_IMG_TAG_PATH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image getImage(Object element) {
        Image img = null;
        if (element != null && element instanceof ProjectModel) {
            ProjectModel project = (ProjectModel) element;
            if (project.getBuildType() == ProjectModel.BuildType.GRADLE) {
                img = gradleImg;
            } else {
                img = mavenImg;
            }
        }

        return img;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getText(Object element) {
        String columnText = null;
        if (element != null && element instanceof ProjectModel) {
            ProjectModel project = (ProjectModel) element;
            columnText = project.getName();
        }

        return columnText;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        if (gradleImg != null) {
            gradleImg.dispose();
        }
        if (mavenImg != null) {
            mavenImg.dispose();
        }
        super.dispose();
    }
}