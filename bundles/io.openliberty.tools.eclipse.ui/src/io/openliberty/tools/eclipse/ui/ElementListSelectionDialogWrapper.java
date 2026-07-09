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
package io.openliberty.tools.eclipse.ui;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

/**
 * An ElementListSelectionDialog wrapper for customization.
 */
public class ElementListSelectionDialogWrapper extends ElementListSelectionDialog {

    /**
     * Constructor.
     *
     * @param parent        The parent shell.
     * @param labelProvider The label provider for list elements.
     */
    public ElementListSelectionDialogWrapper(Shell parent, ILabelProvider labelProvider) {
        super(parent, labelProvider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Label createMessageArea(Composite composite) {
        // Allow the message are text to wrap to fit the dialog width
        // while adjusting to the dialog contraction and expansion.
        Label label = super.createMessageArea(composite);

        String text = label.getText();
        label.dispose();

        Label wrapping = new Label(composite, SWT.WRAP);
        wrapping.setText(text);

        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.widthHint = convertWidthInCharsToPixels(60);
        wrapping.setLayoutData(gd);

        return wrapping;
    }
}