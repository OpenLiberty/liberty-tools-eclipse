package io.openliberty.tools.eclipse.utils;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.IPreferencePageContainer;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

public class LibertyToolsMessageDialog extends MessageDialog {

    public LibertyToolsMessageDialog(Shell parentShell, String dialogTitle, Image dialogTitleImage, String dialogMessage,
            int dialogImageType, String[] dialogButtonLabels, int defaultIndex) {
        super(parentShell, dialogTitle, dialogTitleImage, dialogMessage, dialogImageType, defaultIndex, dialogButtonLabels);
    }
    
    @Override
    protected Control createCustomArea( Composite parent ) {
      Link link = new Link( parent, SWT.WRAP );
      link.setText( "Please visit <a>Liberty Preferences</a>" );
      link.addSelectionListener(new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
              PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                      null, "io.openliberty.tools.eclipse.ui.preferences.page",  
                      new String[] {"io.openliberty.tools.eclipse.ui.preferences.page"}, null);
                  dialog.open();
          }
      });
      return link;
    }
  }

