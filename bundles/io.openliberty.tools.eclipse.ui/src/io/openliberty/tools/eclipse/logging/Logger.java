package io.openliberty.tools.eclipse.logging;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Writes to the platform log using the Platform's logging facility. Users can view the logged entries using the error log: Window
 * > Show View > General > Error Log.
 */
public class Logger {

    /** The bundle associated with this class. */
    private static final Bundle BUNDLE = FrameworkUtil.getBundle(Logger.class);

    /** The Log for the input bundle. */
    private static final ILog LOGGER = Platform.getLog(BUNDLE);

    /**
     * Writes an error message to the log.
     * 
     * @param msg The message to write.
     */
    public static void logError(String msg) {
        LOGGER.log(new Status(Status.ERROR, BUNDLE.getSymbolicName(), msg));
    }

    /**
     * Writes an error message and throwable to the log.
     * 
     * @param msg The message to write.
     * @param t The throwable to write.
     */
    public static void logError(String msg, Throwable t) {
        LOGGER.log(new Status(Status.ERROR, BUNDLE.getSymbolicName(), msg, t));
    }

    /**
     * Writes an error message to the log.
     * 
     * @param msg The message to write.
     */
    public static void logWarning(String msg) {
        LOGGER.log(new Status(Status.WARNING, BUNDLE.getSymbolicName(), msg));
    }

    /**
     * Writes an error message and throwable to the log.
     * 
     * @param msg The message to write.
     * @param t The throwable to write.
     */
    public static void logWarning(String msg, Throwable t) {
        LOGGER.log(new Status(Status.WARNING, BUNDLE.getSymbolicName(), msg, t));
    }
}
