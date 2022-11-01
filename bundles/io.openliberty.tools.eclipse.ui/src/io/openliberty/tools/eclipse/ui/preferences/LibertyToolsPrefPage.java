package io.openliberty.tools.eclipse.ui.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import io.openliberty.tools.eclipse.LibertyDevPlugin;

public class LibertyToolsPrefPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public LibertyToolsPrefPage() {
        super(GRID);
    }

    public void createFieldEditors() {
        addField(new DirectoryFieldEditor("MVNPATH", "&Maven Install Location:", getFieldEditorParent()));
        addField(new DirectoryFieldEditor("GRADLEPATH", "&Gradle Install Location:", getFieldEditorParent()));
        /*
        addField(new BooleanFieldEditor("BOOLEAN_VALUE", "&A boolean preference", getFieldEditorParent()));

        addField(new RadioGroupFieldEditor("CHOICE", "A &multiple-choice preference", 1,
                new String[][] { { "&Choice 1", "choice1" }, { "C&hoice 2", "choice2" } }, getFieldEditorParent()));
        addField(new StringFieldEditor("MySTRING1", "A &text preference:", getFieldEditorParent()));
        addField(new StringFieldEditor("MySTRING2", "A t&ext preference:", getFieldEditorParent()));
        */
    }

    @Override
    public void init(IWorkbench workbench) {
        // second parameter is typically the plug-in id
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, "io.openliberty.tools.eclipse.ui"));
        //setPreferenceStore(LibertyDevPlugin.getDefault().getPreferenceStore());
        setDescription("Specify the Maven and Gradle installation locations to be used in the build of the application");
    }

}
