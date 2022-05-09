/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package liberty.tools.messages;

import org.eclipse.osgi.util.NLS;

/**
 * Translated messages.
 */
public class Messages extends NLS {

    public static String liberty_maven_plugin_incorrect;
    public static String liberty_gradle_plugin_incorrect;

    static {
        NLS.initializeMessages("liberty.tools.messages.Messages", Messages.class);
    }
}