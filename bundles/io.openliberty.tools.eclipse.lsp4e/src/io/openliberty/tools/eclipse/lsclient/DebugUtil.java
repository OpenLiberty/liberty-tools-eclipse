/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.lsclient;

public class DebugUtil {

    /**
     * @param className
     * 
     * @return JVM arg if debug is enabled, otherwise returns emptry string
     */
    public static String getDebugJVMArg(String className) {
        String debugPortString = System.getProperty(className + ".debugPort");
        if (debugPortString != null) {
            return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + getDebugSuspend(className) + ",address=" + debugPortString;
        } else {
            return "";
        }
    }

    private static String getDebugSuspend(String className) {
        String debugSuspend = System.getProperty(className + ".debugSuspend", "false");

        if (Boolean.parseBoolean(debugSuspend)) {
            return "y";
        } else if (debugSuspend.equalsIgnoreCase("y")) {
            return "y";
        } else {
            return "n";
        }
    }

}
