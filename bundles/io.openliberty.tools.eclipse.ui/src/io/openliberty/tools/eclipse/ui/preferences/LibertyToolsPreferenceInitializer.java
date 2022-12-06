package io.openliberty.tools.eclipse.ui.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import io.openliberty.tools.eclipse.LibertyDevPlugin;

public class LibertyToolsPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        // TODO Auto-generated method stub
        IPreferenceStore libertyPrefs = LibertyDevPlugin.getDefault().getPreferenceStore();
        
        libertyPrefs.setDefault("DEBUG_TIMEOUT", 90);
    }

}
