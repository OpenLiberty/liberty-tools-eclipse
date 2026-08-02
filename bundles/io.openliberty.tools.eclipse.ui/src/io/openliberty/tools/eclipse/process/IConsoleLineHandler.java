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
package io.openliberty.tools.eclipse.process;

/**
 * Receives individual lines of console output produced by a running dev mode process.
 *
 * Implementations are registered with a ConsoleOutputInterceptor. The interceptor calls
 * handleLine once for each complete line delivered by Eclipse. Handlers must not block
 * the calling thread because the call originates from an Eclipse stream monitor callback.
 */
public interface IConsoleLineHandler {

    /**
     * Handles one line of console output from the dev mode process.
     *
     * @param projectName The name of the project whose process produced the line.
     * @param line The line of text, without any trailing newline character.
     */
    void handleLine(String projectName, String line);
}
