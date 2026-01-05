/*******************************************************************************
 * Copyright (c) 2023, 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.tools.eclipse.messages;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Provides access to NLS Messages.
 */
public class Messages {

    /** Message Resource bundle */
    private static ResourceBundle NLS_BUNDLE;
    static {
        try {
            NLS_BUNDLE = ResourceBundle.getBundle("io.openliberty.tools.eclipse.messages.Messages", Locale.getDefault());
        } catch (Exception e) {
            NLS_BUNDLE = ResourceBundle.getBundle("io.openliberty.tools.eclipse.messages.Messages");
        }
    }

    /**
     * Returns a translated message with no arguments.
     * 
     * @param key The message Key.
     * 
     * @return The translated message without arguments.
     */
    public static String getMessage(String key) {
        return getMessage(key, (Object) null);
    }

    /**
     * Returns a translated message with arguments.
     * 
     * @param key  The message key.
     * @param args The arguments associated with the message.
     * 
     * @return A translated message with arguments.
     */
    public static String getMessage(String key, Object... args) {
        String msg = null;

        try {
            msg = NLS_BUNDLE.getString(key);
            if (msg != null && args != null) {
                msg = MessageFormat.format(msg, args);
            }
        } catch (Exception e) {
            msg = key;
        }

        return msg;
    }
}