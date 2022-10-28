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
package io.openliberty.tools.eclipse;

public class EarlyStartupRegistration implements org.eclipse.ui.IStartup {

    @Override
    public void earlyStartup() {
        // Causes AbstractUIPlugin to start() early

        // Don't need to do anything, just registering as an IStartup so
        // the bundle will eagerly start.
    }
}
