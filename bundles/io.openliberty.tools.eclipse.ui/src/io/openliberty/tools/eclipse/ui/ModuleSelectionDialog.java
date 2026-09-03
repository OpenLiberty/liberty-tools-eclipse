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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;

/**
 * A selection dialog for choosing Liberty modules.
 *
 * When multi-selection capable actions are selected such as Start, Start in Container,
 * Stop, and Run Tests, the dialog shows a checkbox table with Select All and
 * Deselect All buttons, allowing the user to pick one or more modules.
 *
 * When single-selection capable actions are selected such as Start..., Debug*,
 * and Open Test Report, the dialog shows a plain list with no additional buttons.
 * The highlighted row is the selection.
 *
 * Both modes include a live filter text field and an OK button that is disabled when
 * nothing is selected.
 */
public class ModuleSelectionDialog extends Dialog {

    /** The dialog title. */
    private final String title;

    /** The descriptive message shown above the filter box. */
    private final String message;

    /** The full unfiltered list of candidate modules. */
    private final List<ProjectModel> candidates;

    /** Label provider supplying text and icons for each row. */
    private final ILabelProvider labelProvider;

    /**
     * When true a checkbox table with Select All and Deselect All buttons is shown.
     * When false a plain single-selection list is shown without those buttons.
     */
    private final boolean multiSelect;

    /**
     * The module names that should be pre-checked when the dialog opens.
     */
    private final List<String> initialSelections;

    /**
     * The checkbox viewer used in multi-select mode.
     * Null when multiSelect is false.
     */
    private CheckboxTableViewer checkViewer;

    /**
     * The plain table viewer used in single-select mode.
     * Null when multiSelect is true.
     */
    private TableViewer singleViewer;

    /** The user's final selection, empty until OK is pressed. */
    private List<ProjectModel> result = Collections.emptyList();

    /**
     * Creates a dialog with explicit single-select or multi-select behaviour and no
     * pre-checked items.
     *
     * @param parent        The parent shell.
     * @param title         The dialog title.
     * @param message       The descriptive message shown above the filter field.
     * @param candidates    The full list of selectable modules.
     * @param labelProvider The label provider for text and icons.
     * @param multiSelect   True to show a checkbox table with Select All and Deselect All
     *                          buttons. False to show a plain single-selection list without
     *                          those buttons.
     */
    public ModuleSelectionDialog(Shell parent, String title, String message,
                                 List<ProjectModel> candidates, ILabelProvider labelProvider, boolean multiSelect) {
        this(parent, title, message, candidates, labelProvider, multiSelect, Collections.emptyList());
    }

    /**
     * Creates a dialog with explicit single-select or multi-select behaviour and the
     * given items pre-checked.
     *
     * @param parent            The parent shell.
     * @param title             The dialog title.
     * @param message           The descriptive message shown above the filter field.
     * @param candidates        The full list of selectable modules.
     * @param labelProvider     The label provider for text and icons.
     * @param multiSelect       True to show a checkbox table with Select All and Deselect All
     *                              buttons. False to show a plain single-selection list without
     *                              those buttons.
     * @param initialSelections The list of module names to pre-check on open. Used only
     *                              in multi-select mode. Items not present in candidates are
     *                              silently ignored.
     */
    public ModuleSelectionDialog(Shell parent, String title, String message,
                                 List<ProjectModel> candidates, ILabelProvider labelProvider,
                                 boolean multiSelect, List<String> initialSelections) {
        super(parent);
        this.title = title;
        this.message = message;
        this.candidates = candidates;
        this.labelProvider = labelProvider;
        this.multiSelect = multiSelect;
        this.initialSelections = (initialSelections != null) ? initialSelections : Collections.emptyList();
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Point getInitialSize() {
        return new Point(480, 420);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        GridLayout layout = (GridLayout) area.getLayout();
        layout.verticalSpacing = 8;

        // Message label with wrapping.
        Label msgLabel = new Label(area, SWT.WRAP);
        msgLabel.setText(message);
        GridData msgGd = new GridData(GridData.FILL_HORIZONTAL);
        msgGd.widthHint = convertWidthInCharsToPixels(60);
        msgLabel.setLayoutData(msgGd);

        // Filter text field.
        Text filterText = new Text(area, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        filterText.setMessage(Messages.getMessage("search_filter_hint"));
        filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        final NameFilter nameFilter = new NameFilter();

        if (multiSelect) {
            createMultiSelectTable(area, nameFilter);
        } else {
            createSingleSelectTable(area, nameFilter);
        }

        // Wire up filter text changes to refresh the active viewer.
        filterText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                nameFilter.setFilterString(filterText.getText());
                if (multiSelect) {
                    checkViewer.refresh();
                } else {
                    singleViewer.refresh();
                }
                updateOkButton();
            }
        });

        return area;
    }

    /**
     * Builds the checkbox table and Select All / Deselect All buttons used in multi-select mode.
     *
     * @param area       The dialog content area composite.
     * @param nameFilter The viewer filter wired to the filter text field.
     */
    private void createMultiSelectTable(Composite area, NameFilter nameFilter) {
        checkViewer = CheckboxTableViewer.newCheckList(area, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData tableGd = new GridData(GridData.FILL_BOTH);
        tableGd.heightHint = convertHeightInCharsToPixels(10);
        tableGd.grabExcessVerticalSpace = true;
        checkViewer.getTable().setLayoutData(tableGd);

        checkViewer.setContentProvider(ArrayContentProvider.getInstance());

        checkViewer.setLabelProvider(labelProvider);
        checkViewer.addFilter(nameFilter);
        checkViewer.setInput(candidates);

        // Pre-check items whose names match the initial selection list.
        if (!initialSelections.isEmpty()) {
            for (ProjectModel candidate : candidates) {
                if (initialSelections.contains(candidate.getName())) {
                    checkViewer.setChecked(candidate, true);
                }
            }
        }

        checkViewer.addCheckStateListener(event -> updateOkButton());

        // Double-clicking a row toggles its checkbox, matching standard Eclipse convention.
        checkViewer.addDoubleClickListener(event -> {
            Object element = checkViewer.getStructuredSelection().getFirstElement();
            if (element != null) {
                boolean current = checkViewer.getChecked(element);
                checkViewer.setChecked(element, !current);
                updateOkButton();
            }
        });

        // Spacer between the table and the Select All / Deselect All buttons.
        Label spacer = new Label(area, SWT.NONE);
        GridData spacerGd = new GridData(GridData.FILL_HORIZONTAL);
        spacerGd.heightHint = 2;
        spacer.setLayoutData(spacerGd);

        // Row containing the Select All and Deselect All buttons, left-aligned.
        Composite buttonRow = new Composite(area, SWT.NONE);
        GridLayout buttonRowLayout = new GridLayout(2, false);
        buttonRowLayout.marginWidth = 0;
        buttonRowLayout.marginHeight = 0;
        buttonRowLayout.horizontalSpacing = 8;
        buttonRow.setLayout(buttonRowLayout);
        buttonRow.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Select All operates only on currently visible (filtered) items.
        Button selectAllBtn = new Button(buttonRow, SWT.PUSH);
        selectAllBtn.setText(Messages.getMessage("checkbox_dialog_select_all"));
        selectAllBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        selectAllBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (Object item : getVisibleItems()) {
                    checkViewer.setChecked(item, true);
                }
                updateOkButton();
            }
        });

        // Deselect All operates only on currently visible (filtered) items.
        Button deselectAllBtn = new Button(buttonRow, SWT.PUSH);
        deselectAllBtn.setText(Messages.getMessage("checkbox_dialog_deselect_all"));
        deselectAllBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        deselectAllBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (Object item : getVisibleItems()) {
                    checkViewer.setChecked(item, false);
                }
                updateOkButton();
            }
        });
    }

    /**
     * Builds the plain single-selection table used in single-select mode.
     *
     * @param area       The dialog content area composite.
     * @param nameFilter The viewer filter wired to the filter text field.
     */
    private void createSingleSelectTable(Composite area, NameFilter nameFilter) {
        singleViewer = new TableViewer(area, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData tableGd = new GridData(GridData.FILL_BOTH);
        tableGd.heightHint = convertHeightInCharsToPixels(10);
        tableGd.grabExcessVerticalSpace = true;
        singleViewer.getTable().setLayoutData(tableGd);

        singleViewer.setContentProvider(ArrayContentProvider.getInstance());
        singleViewer.setLabelProvider(labelProvider);
        singleViewer.addFilter(nameFilter);
        singleViewer.setInput(candidates);

        // Do not pre-select any items.
        if (!candidates.isEmpty()) {
            singleViewer.getTable().select(0);
        }

        singleViewer.addSelectionChangedListener(event -> updateOkButton());

        // Double-clicking a row immediately confirms the selection and closes the dialog.
        singleViewer.addDoubleClickListener(event -> okPressed());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        updateOkButton();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void okPressed() {
        if (multiSelect) {
            Object[] checked = checkViewer.getCheckedElements();
            List<ProjectModel> selected = new ArrayList<>(checked.length);
            for (Object item : checked) {
                selected.add((ProjectModel) item);
            }
            result = Collections.unmodifiableList(selected);
        } else {
            Object first = singleViewer.getStructuredSelection().getFirstElement();
            if (first != null) {
                result = Collections.singletonList((ProjectModel) first);
            }
        }
        super.okPressed();
    }

    /**
     * Returns the list of modules the user confirmed.
     * Empty if the dialog was cancelled or no items were selected.
     *
     * @return An unmodifiable list of selected ProjectModel instances.
     */
    public List<ProjectModel> getResult() {
        return result;
    }

    /**
     * Convenience method for single-select callers.
     * Returns the first confirmed module, or null if the dialog was cancelled or no item
     * was selected.
     *
     * @return The selected ProjectModel, or null.
     */
    public ProjectModel getFirstResult() {
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Returns the items currently visible in the checkbox viewer, that is, those passing
     * all active filters. Used only in multi-select mode. Table items whose data is null
     * are excluded.
     *
     * @return An array of the currently visible model elements.
     */
    private Object[] getVisibleItems() {
        return Arrays.stream(checkViewer.getTable().getItems()).map(item -> item.getData()).filter(data -> data != null).toArray();
    }

    /**
     * Enables or disables the OK button based on whether at least one item is selected.
     * In multi-select mode at least one checkbox must be checked. In single-select mode
     * the table row highlight counts as the selection.
     */
    private void updateOkButton() {
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton == null || okButton.isDisposed()) {
            return;
        }
        if (multiSelect) {
            okButton.setEnabled(checkViewer.getCheckedElements().length > 0);
        } else {
            okButton.setEnabled(!singleViewer.getStructuredSelection().isEmpty());
        }
    }

    /**
     * A ViewerFilter that hides rows whose display name does not contain the current filter
     * string. The filter string is matched case-insensitively as a substring.
     */
    private class NameFilter extends ViewerFilter {

        /** The current lower-case filter string. Empty string matches everything. */
        private String filterString = "";

        /**
         * Sets the filter string. A null value is treated as an empty string.
         *
         * @param s The new filter string.
         */
        public void setFilterString(String s) {
            this.filterString = s == null ? "" : s.toLowerCase();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean select(Viewer v, Object parentElement, Object element) {
            if (filterString.isEmpty()) {
                return true;
            }
            String name = labelProvider.getText(element);
            return name != null && name.toLowerCase().contains(filterString);
        }
    }
}
