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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamMonitor;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Intercepts console output produced by a dev mode process.
 *
 * A single instance of this class is registered as an IStreamListener on both the stdout
 * and stderr monitors of the IProcess returned by DebugPlugin.newProcess(). Eclipse still
 * delivers the same text to the Console view; this class only observes the text in parallel.
 */
public class ConsoleOutputInterceptor implements IStreamListener {

    /** Handlers to be notified for each complete line. */
    private final List<IConsoleLineHandler> handlers = new CopyOnWriteArrayList<>();

    /** Name of the project whose process output is being intercepted. */
    private final String projectName;

    /** Buffer for partial lines that did not end with a newline in the last chunk. */
    private final StringBuilder lineBuffer = new StringBuilder();

    /**
     * Constructs an interceptor for the specified project.
     *
     * @param projectName The name of the project whose process output is intercepted.
     */
    public ConsoleOutputInterceptor(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Adds a handler to be notified for each complete line of output.
     *
     * @param handler The handler to add.
     */
    public void addHandler(IConsoleLineHandler handler) {
        handlers.add(handler);
    }

    /**
     * Removes a previously registered handler.
     *
     * @param handler The handler to remove.
     */
    public void removeHandler(IConsoleLineHandler handler) {
        handlers.remove(handler);
    }

    /**
     * Receives a set of text from the Eclipse stream monitor. The chunk is appended to
     * the internal line buffer and then split on newlines. Each complete line is dispatched
     * to all registered handlers. Any trailing text without a newline remains in the buffer
     * until the next set arrives or until flush() is called.
     *
     * @param text The text chunk appended to the stream.
     * @param monitor The stream monitor that produced the text.
     */
    @Override
    public void streamAppended(String text, IStreamMonitor monitor) {
        if (text == null || text.isEmpty()) {
            return;
        }

        lineBuffer.append(text);
        String buffered = lineBuffer.toString();
        lineBuffer.setLength(0);

        int start = 0;
        int length = buffered.length();
        for (int i = 0; i < length; i++) {
            char c = buffered.charAt(i);
            if (c == '\n' || c == '\r') {
                dispatchLine(buffered.substring(start, i));
                // Treat \r\n as a single line ending.
                if (c == '\r' && i + 1 < length && buffered.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }

        // Retain any trailing text that did not end with a newline.
        if (start < length) {
            lineBuffer.append(buffered.substring(start));
        }
    }

    /**
     * Flushes any text remaining in the partial-line buffer as a final line. Should be
     * called once when the associated process has terminated to ensure that a final line
     * not terminated with a newline is still delivered to handlers.
     */
    public void flush() {
        if (lineBuffer.length() > 0) {
            String line = lineBuffer.toString();
            lineBuffer.setLength(0);
            dispatchLine(line);
        }
    }

    /**
     * Dispatches one complete line to all registered handlers. Exceptions thrown by
     * individual handlers are caught and logged so that other handlers still receive
     * the line.
     *
     * @param line The line to dispatch.
     */
    private void dispatchLine(String line) {
        for (IConsoleLineHandler handler : handlers) {
            try {
                handler.handleLine(projectName, line);
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI,
                        "ConsoleOutputInterceptor: handler " + handler.getClass().getName()
                        + " threw an exception processing line for project " + projectName, e);
                }
            }
        }
    }
}
