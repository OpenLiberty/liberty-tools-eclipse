/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.model;

import java.util.List;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective;

/**
 * E4 model processor registered via org.eclipse.e4.workbench.model extension
 * point. Runs synchronously on every startup during model assembly. Recursively
 * walks the entire model tree to find every MPerspective and adds the Liberty
 * Starter wizard shortcut tag so the "Create new Liberty starter project" link
 * appears in the empty Project/Package Explorer without a perspective reset.
 */
public class LibertyPerspectiveProcessor {

    private static final String LIBERTY_WIZARD_TAG =
            "persp.newWizSC:io.openliberty.tools.eclipse.ui.wizard.LibertyStarter"; //$NON-NLS-1$

    @Execute
    public void process(MApplication application) {
        // Recursively walk the entire model tree — perspectives can be nested
        // at any depth (e.g. TrimmedWindow → PartSashContainer → PerspectiveStack → Perspective)
        walkAndTag(application);
    }

    private void walkAndTag(MUIElement element) {
        if (element instanceof MPerspective perspective) {
            addTagIfMissing(perspective);
        }
        if (element instanceof MElementContainer<?> container) {
            for (Object child : container.getChildren()) {
                if (child instanceof MUIElement childEl) {
                    walkAndTag(childEl);
                }
            }
        }
    }

    private void addTagIfMissing(MPerspective perspective) {
        List<String> tags = perspective.getTags();
        if (!tags.contains(LIBERTY_WIZARD_TAG)) {
            tags.add(LIBERTY_WIZARD_TAG);
        }
    }
}
