/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective;

/**
 * E4 model processor registered via org.eclipse.e4.workbench.model extension
 * point. Runs synchronously on every startup during model assembly. Recursively
 * walks the model tree to find targeted MPerspective elements and adds the
 * Liberty Starter wizard shortcut tag so the create new Liberty starter project link
 * appears in the empty Project or Package Explorer without a perspective reset.
 */
public class LibertyPerspectiveProcessor {

    private static final String LIBERTY_WIZARD_TAG =
            "persp.newWizSC:io.openliberty.tools.eclipse.ui.wizard.LibertyStarter";

    /**
     * List of perspectives to tag. This list must remain in sync with the list
     * outlined in the org.eclipse.ui.perspectiveExtensions extension point in
     * plugin.xml.
     */
    private static final Set<String> TARGET_PERSPECTIVES = new HashSet<>(Arrays.asList(
            "org.eclipse.jdt.ui.JavaPerspective",
            "org.eclipse.ui.resourcePerspective",
            "org.eclipse.jdt.ui.JavaBrowsingPerspective",
            "org.eclipse.jst.j2ee.J2EEPerspective",
            "org.eclipse.jpt.ui.jpaPerspective"
    ));

    @Execute
    public void process(MApplication application) {
        // Recursively walk the entire model tree. Perspectives can be nested at any depth.
        walkAndTag(application);
    }

    private void walkAndTag(MUIElement element) {
        if (element instanceof MPerspective perspective) {
            String perspectiveId = perspective.getElementId();
            if (perspectiveId != null && TARGET_PERSPECTIVES.contains(perspectiveId)) {
                addTagIfMissing(perspective);
            }
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
