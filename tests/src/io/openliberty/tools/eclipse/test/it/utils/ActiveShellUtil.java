package io.openliberty.tools.eclipse.test.it.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;

/**
 * - Linux on some platforms (GTK3) has a problem where: A) Workbench opens dialog A (like preference dialog) B) Dialog A opens a
 * second dialog B C) Dialog B closes D) Dialog A is still open, but it does not have desktop focus (eg it is greyed out and not
 * active) ActiveShellUtil is a Linux-only utility that fixes the problem, by running a thread in the background and detecting
 * this issue. Start the utility by calling ActiveShellUtil.startActiveShellThread(); May be safely called multiple times; will
 * only start one thread.
 */
public class ActiveShellUtil {

    private static ActiveShellThread thread;

    /**
     * Automatically activate shells that are on top but inactive for at least 10 seconds. This is only required on Linux.
     */
    public synchronized static void startActiveShellThread() {

        System.out.println("INFO: Activating 'ActiveShellUtil'");

        if (thread == null) {
            thread = new ActiveShellThread();
            thread.start();
        }
    }

    private static class ShellEntry implements Comparable<ShellEntry> {
        Shell shell;
        long firstSeenInNanos;
        boolean shellSeen = false;
        long timeToMakeActiveInNanos;

        @Override
        public int compareTo(ShellEntry o) {
            // Descending order
            long l = o.firstSeenInNanos - firstSeenInNanos;

            if (l > 0) {
                return 1;
            } else if (l == 0) {
                return 0;
            } else {
                return -1;
            }

        }
    }

    private static class ActiveShellThread extends Thread {

        public ActiveShellThread() {
            super(ActiveShellThread.class.getName());
            setDaemon(true);
        }

        @Override
        public void run() {

            final Map<String, ShellEntry> shellMap = new HashMap<String, ShellEntry>();

            while (true) {

                // Reset shells seen value
                for (Map.Entry<String, ShellEntry> e : shellMap.entrySet()) {
                    e.getValue().shellSeen = false;
                }

                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {

                        System.out.println("INFO: ActiveShellUtil - running cleanup");

                        Shell[] s = Display.getDefault().getShells();
                        for (Shell sh : s) {

                            String text = sh.getText();
                            System.out.println("INFO: ActiveShellUtil - found shell " + text);

                            // Skip known boring shells
                            if (text.trim().isEmpty() || text.contains("Quick Access") || text.contains("PartRenderingEngine")) {
                                continue;
                            }

                            if (sh.isDisposed()) {
                                continue;
                            }

                            // Update entry contents
                            ShellEntry se = shellMap.get(text);
                            if (se == null) {
                                se = new ShellEntry();
                                shellMap.put(text, se);
                                se.firstSeenInNanos = System.nanoTime();
                            }
                            se.shell = sh;
                            se.shellSeen = true;

                        }

                    } // end run();
                });

                // Remove shells that are no longer alive
                for (Iterator<Map.Entry<String, ShellEntry>> it = shellMap.entrySet().iterator(); it.hasNext();) {
                    Entry<String, ShellEntry> e = it.next();

                    if (!e.getValue().shellSeen) {
                        System.out.println("INFO: ActiveShellUtil - removing shell " + e.getKey());
                        it.remove();
                    }
                }

                // Find the shell that should be on top
                List<ShellEntry> sel = new ArrayList<ShellEntry>();
                sel.addAll(shellMap.values());
                Collections.sort(sel);

                try {

                    for (int x = 0; x < sel.size(); x++) {
                        ShellEntry se = sel.get(x);

                        boolean onTopAndInactive = false;

                        if (x == 0) {

                            SWTBotShell sbs = new SWTBotShell(se.shell);

                            if (!sbs.isActive() && sbs.isOpen()) {
                                onTopAndInactive = true;

                                if (se.timeToMakeActiveInNanos == 0) {
                                    se.timeToMakeActiveInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(10, TimeUnit.SECONDS);
                                } else if (System.nanoTime() > se.timeToMakeActiveInNanos) {

                                    // If a dialog is on top but inactive, for
                                    // 10 seconds, then make it active

                                    sbs.activate();
                                    sbs.setFocus();

                                    onTopAndInactive = false;
                                }
                            }
                        }

                        if (!onTopAndInactive) {
                            se.timeToMakeActiveInNanos = 0;
                        }
                    }

                } catch (Exception e) {
                    /* ignore */
                }

                delay(1000);

            } // end while()

        }

        private void delay(long waitTimeInMsecs) {

            Display display = Display.getCurrent();

            if (display != null) {
                long expire = System.nanoTime() + TimeUnit.NANOSECONDS.convert(waitTimeInMsecs, TimeUnit.MILLISECONDS);

                while (System.nanoTime() < expire) {
                    try {
                        if (!display.readAndDispatch()) {
                            display.sleep();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                display.update();

            } else {
                try {
                    TimeUnit.MILLISECONDS.sleep(waitTimeInMsecs);
                } catch (InterruptedException e) {
                    /* Ignore */
                }
            }
        }

    }
}
