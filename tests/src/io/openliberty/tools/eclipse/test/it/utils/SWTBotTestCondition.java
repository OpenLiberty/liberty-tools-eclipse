/*******************************************************************************
* Copyright (c) 2022, 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.test.it.utils;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

/**
 * Provides a predefined set conditions to wait for.
 */
public class SWTBotTestCondition {

    /**
     * Returns true if the view is active. False, otherwise.
     * 
     * @param view The view to check.
     * @param viewName The name of the view.
     * 
     * @return True if the view is active. False, otherwise.
     */
    public static ICondition isTextPresent(SWTBotText textbox, String text) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                return (textbox != null) && (textbox.isActive() && textbox.getText().contains(text));
            }

            @Override
            public String getFailureMessage() {
                return "Text box is not active or it did not contain " + text + ".";
            }
        };
    }

    /**
     * Returns true if the view is active. False, otherwise.
     * 
     * @param view The view to check.
     * @param viewName The name of the view.
     * 
     * @return True if the view is active. False, otherwise.
     */
    public static ICondition isViewActive(SWTBotView view, String viewName) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                return (view != null) && (view.isActive());
            }

            @Override
            public String getFailureMessage() {
                return "View " + viewName + " is not active.";
            }
        };
    }

    /**
     * Returns true if the input tree structure is enabled and contains a particular item. False, otherwise.
     * 
     * @param tree The tree widget
     * @param item The name of the desired item in the tree structure.
     * 
     * @return True If the input tree structure is enabled and contains a particular item. False, otherwise.
     */
    public static ICondition isTreeWidgetEnabled(SWTWorkbenchBot bot, String item) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                SWTBotTree tree = bot.tree();
                return (tree != null) && (tree.isEnabled() && tree.getTreeItem(item) != null);
            }

            @Override
            public String getFailureMessage() {
                return "Tree is not enabled or tree item " + item + " could not be found.";
            }
        };
    }

    /**
     * Returns true if the input tree item is enabled. False, otherwise.
     * 
     * @param item The tree item in the tree structure.
     * 
     * @return True if the input tree item is enabled. False, otherwise.
     */
    public static ICondition isTreeItemEnabled(SWTBotTreeItem item) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                return (item != null) && (item.isEnabled());
            }

            @Override
            public String getFailureMessage() {
                return "Tree item " + item + " not enabled.";
            }
        };
    }

    /**
     * Returns true if the input menu is enabled. False, otherwise.
     * 
     * @param menu The menu item object.
     * 
     * @return True if the input menu is enabled. False, otherwise.
     */
    public static ICondition isMenuEnabled(SWTBotMenu menu) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                return (menu != null) && (menu.isEnabled());
            }

            @Override
            public String getFailureMessage() {
                return "Menu " + menu + " is not enabled.";
            }
        };
    }

    /**
     * Returns true if the input button is enabled. False, otherwise.
     * 
     * @param button The button object.
     * 
     * @return True if the input button is enabled. False, otherwise.
     */
    public static ICondition isButtonEnabled(SWTBotButton button) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                return (button != null) && (button.isEnabled());
            }

            @Override
            public String getFailureMessage() {
                return "Button " + button.getText() + " is not enabled.";
            }
        };
    }

    /**
     * Returns true if the dialog is active. False, otherwise.
     * 
     * @param dialog The dialog to check.
     * 
     * @return True if the dialog is active. False, otherwise.
     */
    public static ICondition isDialogActive(SWTBotShell dialog) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                return dialog.isActive();
            }

            @Override
            public String getFailureMessage() {
                return "Dialog " + dialog.getText() + " is not active.";
            }
        };
    }

    /**
     * Returns true if the editor containing the input name is active. False, otherwise.
     * 
     * @param wbbot The Workbench bot instance.
     * @param fileName The editor title or a subset of the title name.
     * 
     * @return True if the editor containing the input name is active. False, otherwise.
     */
    public static ICondition isEditorActive(SWTWorkbenchBot wbbot, String fileName) {
        return new CustomCondition() {

            @Override
            public boolean test() throws Exception {
                SWTBotEditor editor = SWTBotPluginOperations.searchForEditor(wbbot, fileName);

                if (editor == null) {
                    return false;
                }

                return editor.isActive();
            }

            @Override
            public String getFailureMessage() {
                return "Editor " + fileName + " is not active";
            }
        };
    }

    /**
     * Custom condition
     */
    public static class CustomCondition implements ICondition {
        SWTBot bot;

        @Override
        public boolean test() throws Exception {
            return false;
        }

        @Override
        public void init(SWTBot bot) {
            this.bot = bot;
        }

        @Override
        public String getFailureMessage() {
            return null;
        }
    }
}
