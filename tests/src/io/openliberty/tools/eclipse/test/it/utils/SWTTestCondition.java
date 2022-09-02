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
package io.openliberty.tools.eclipse.test.it.utils;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;

/**
 * Provides a predefined set conditions to wait for.
 */
public class SWTTestCondition {

    /**
     * Returns true if the view is active. False, otherwise.
     * 
     * @param view The view to check.
     * @param viewName The name of the view.
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
	 * Retursn true if the editor containing the input name is active. False, otherwise. 
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
			        SWTBotEditor editor =  SWTPluginOperations.searchForEditor(wbbot, fileName);
			        
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
	 * Retursn true if the editor containing the input name is active. False, otherwise. 
	 * 
	 * @param wbbot The Workbench bot instance.
	 * @param fileName The editor title or a subset of the title name.
	 * 
	 * @return True if the editor containing the input name is active. False, otherwise.
	 */
	public static ICondition isControlActive(Control cbot, String popupName) {
		return new CustomCondition() {

			@Override
			public boolean test() throws Exception {
				return (cbot != null);
			}

			@Override
			public String getFailureMessage() {
				return "Browser Control for " + popupName + " is not active.";
			}
		};
	}
	
	/**
	 * Retursn true if the editor containing the input name is active. False, otherwise. 
	 * 
	 * @param wbbot The Workbench bot instance.
	 * @param fileName The editor title or a subset of the title name.
	 * 
	 * @return True if the editor containing the input name is active. False, otherwise.
	 */
	public static ICondition isBrowserActive(Browser bbot, String popupName) {
		return new CustomCondition() {

			@Override
			public boolean test() throws Exception {
				return (bbot != null);
			}

			@Override
			public String getFailureMessage() {
				return "Browser for " + popupName + " is not active.";
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
