/**
 * Copyright (c) 2011, 2023 IBM Corporation and others.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.AbstractSWTBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCheckBox;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotRadio;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTableItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarPushButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.ViewReference;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.part.ViewPart;
import org.hamcrest.Matcher;

@SuppressWarnings("restriction")
public class MagicWidgetFinder {

    public static final String VERSION = "1.4.0";

    /**
     * Optional: You may use this reference to access the statics, or (recommended) use static import and add MagicWidgetFinder.* in
     * Preferences > Java > Editor > Content Assist > Favourites. If you insist on using this reference (not the best solution, but
     * not the end of the world), I'd suggest adding the @SuppressWarnings("static-access") warning to the method where you use it.
     * Then do this: MagicWidgetFinder f = MagicWidgetFinder.MWF; f.method(); [...]
     */
    public static final MagicWidgetFinder MWF = new MagicWidgetFinder();

    public static void out(String str) {
        System.out.println("* " + str);
        System.out.println();
    }

    public static void expandTreeItem(Object ti, boolean problematic) {

        SWTBotTreeItem treeItem = new SWTBotTreeItem((TreeItem) ti);

        if (treeItem.isExpanded()) {
            return;
        }

        treeItem.expand();
        pause(250);

        long expireTimeInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(3, TimeUnit.MINUTES);

        boolean cont = true;
        do {
            SWTBotTreeItem[] items = treeItem.getItems();

            cont = items.length == 1 && (items[0] == null || items[0].getText().trim().length() == 0);

            if (cont) {

                if (System.getProperty("os.name").toLowerCase().startsWith("linux")) {
                    treeItem.select();
                    treeItem.pressShortcut(Keystrokes.SHIFT, Keystrokes.LEFT);
                    pause(200);
                    treeItem.select();
                    treeItem.pressShortcut(Keystrokes.SHIFT, Keystrokes.RIGHT);
                    pause(200);

                } else {
                    treeItem.select();
                    treeItem.pressShortcut(Keystrokes.LEFT);
                    pause(200);
                    treeItem.select();
                    treeItem.pressShortcut(Keystrokes.RIGHT);
                    pause(200);

                }

            }
        } while (cont && System.nanoTime() < expireTimeInNanos);

        if (!cont) {
            System.err.println("WARNING: MagicWidgetFinder was never able to expand tree item: " + ti.getClass().getName());
        }

    }

    public static void expandTreeItem(Object ti) {
        expandTreeItem(ti, false);

    }

    public static void goMenuItem(Object menuItem, String... args) {

        if (menuItem instanceof TreeItem) {
            throw new RuntimeException("Use context(...) method");
        }

        // System.out.println("--------------------------");
        SWTBotMenu menu = new SWTBotMenu((MenuItem) menuItem);
        for (String arg : args) {
            try {
                menu = menu.menu(arg);
            } catch (WidgetNotFoundException e) {
                if (e.getMessage().equals("The widget was null.")) {
                    throw new RuntimeException("menu item not found: " + arg);
                } else {
                    throw e;
                }
            }
            // System.out.println("arg: "+menu);
        }

        menu.click();

    }

    private static boolean isDisposed(final Object o) {

        final AtomicBoolean valueSet = new AtomicBoolean(false);
        final AtomicBoolean result = new AtomicBoolean(false);

        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                if (o instanceof TreeItem) {
                    TreeItem ti = (TreeItem) o;
                    valueSet.set(true);
                    result.set(ti.isDisposed());
                }

            }

        });

        if (!valueSet.get()) {
            throw new RuntimeException("Unrecognized widget class " + o.getClass().getName());
        }

        return result.get();
    }

    public static void context(Object widget, Object... args) {
        if (widget instanceof TreeItem) {
            if (isDisposed(widget)) {
                throw new RuntimeException("Widget is disposed.");
            }
        }

        long expireTimeInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(30, TimeUnit.SECONDS);

        Throwable thrown = null;
        SWTBotMenu currMenu = null;
        while (System.nanoTime() < expireTimeInNanos && currMenu == null) {

            AbstractSWTBot<?> obj = null;
            if (widget instanceof TreeItem) {

                obj = new SWTBotTreeItem((TreeItem) widget);
            } else if (widget instanceof TableItem) {
                obj = new SWTBotTableItem((TableItem) widget);
            } else {
                throw new RuntimeException("Unable to identify context object type: " + widget.getClass().getName());
            }

            try {
                for (int x = 0; x < args.length; x++) {
                    if (x == 0) {
                        if (args[x] instanceof String)
                            currMenu = obj.contextMenu().menu((String) args[x]);
                        else
                            currMenu = obj.contextMenu().menu((Matcher<MenuItem>) args[x], true, 0);
                    } else {
                        if (args[x] instanceof String)
                            currMenu = currMenu.menu((String) args[x]);
                        else
                            currMenu = currMenu.menu((Matcher<MenuItem>) args[x], false, 0);
                    }

                }
            } catch (Throwable t) {
                thrown = t;
                currMenu = null;
                System.out.println("waiting for context menu.");
                pause(2000);
            }
        } // end-while

        if (currMenu != null) {
            currMenu.click();
        } else if (thrown != null) {
            throw (new RuntimeException(thrown));
        } else {
            throw (new RuntimeException("could not find context."));
        }
    }

    public static Object get(Object w) {
        return get(w, Option.getGlobalOptions());

    }

    public static Object get(final Object w, final Option options) {

        if (w instanceof Button) {

            final AtomicBoolean result = new AtomicBoolean(false);
            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {
                    Button b = (Button) w;
                    if ((b.getStyle() & SWT.CHECK) > 0) {
                        SWTBotCheckBox cb = new SWTBotCheckBox(b);
                        result.set(cb.isChecked());
                    } else {
                        logErr("Unrecognized button style", options);
                    }

                }
            });

            return result.get();

        } else if (w instanceof Text) {
            Text t = (Text) w;
            SWTBotText st = new SWTBotText(t);
            return st.getText();

        } else {
            logErr("Unrecognized widget: " + w.getClass().getName(), options);
        }
        return null;
    }

    public static Object get(final String name, Object neighbour) {
        if (neighbour instanceof Option) {
            throw new RuntimeException("Second parameter should not be Option");
        }

        return get(name, neighbour, Option.getGlobalOptions());

    }

    public static Object get(final String name, Object neighbour, Option options) {
        Object o = find(name, neighbour, options);
        if (o == null) {
            logErr("Unable to find '" + name + "'", options);
            return null;
        } else {
            return get(o, options);
        }
    }

    public static void setGlobal(String name, Boolean value) {
        setGlobal(name, value, Option.getGlobalOptions());
    }

    public static void setGlobal(String name, Boolean value, Option options) {
        Object o = findGlobal(name, options);
        if (o == null) {
            logErr("Unable to find '" + name + "'", options);
            return;
        } else {
            set(o, value, options);
        }
    }

    public static void set(Object o, int index) {
        set(o, index, Option.getGlobalOptions());
    }

    public static void set(Object o, int index, Option options) {
        if (o instanceof String) {
            throw new RuntimeException("Object param should not be a string.");
        }

        out("Setting " + o.getClass().getName() + "(" + Magic.getText(o) + ") to index" + index);

        boolean found = false;
        if (o instanceof Combo) {
            Combo combo = (Combo) o;
            SWTBotCombo sc = new SWTBotCombo(combo);
            sc.setSelection(index);

        } else {
            logErr("Unable to set on unknown class:" + o.getClass(), options);
        }

        if (found) {
            pause(options.getDelayAfterActionInMsecs());
        }

    }

    public static void set(Object o, Boolean value) {
        set(o, value, Option.getGlobalOptions());
    }

    public static void set(Object o, final Boolean value, final Option options) {
        if (o == null) {
            throw new IllegalArgumentException("object cannot be null");
        }
        if (o instanceof String) {
            throw new RuntimeException("Object param should not be a string.");
        }

        out("Setting " + o.getClass().getName() + " (" + Magic.getText(o) + ") to " + value);

        boolean found = false;
        if (o instanceof Button) {
            final Button b = (Button) o;
            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {
                    if ((b.getStyle() & SWT.PUSH) > 0) {
                        SWTBotButton sb = new SWTBotButton(b);
                        sb.click();
                    } else if ((b.getStyle() & SWT.CHECK) > 0) {
                        SWTBotCheckBox cb = new SWTBotCheckBox(b);
                        if (cb.isChecked() != value) {
                            cb.click();
                        }
                    } else if ((b.getStyle() & SWT.RADIO) > 0) {
                        SWTBotRadio rd = new SWTBotRadio(b);
                        rd.click();
                    } else {
                        logErr("Unrecognized style", options);
                    }
                }
            });

            found = true;
        } else {
            logErr("Unable to set on unknown class:" + o.getClass(), options);
        }

        if (found) {
            pause(options.getDelayAfterActionInMsecs());
        }

    }

    public static void set(Object o, final String value) {
        set(o, value, Option.getGlobalOptions());
    }

    public static void set(Object o, final String value, Option options) {
        boolean found = false;

        out("Setting " + o.getClass().getName() + " (" + Magic.getText(o) + ") to " + value);

        if (o instanceof Text) {
            Text t = (Text) o;

            SWTBotText st = new SWTBotText(t);
            st.setText(value);
            found = true;

        } else if (o instanceof Combo) {
            // The ComboTester is failing to properly select a value in some cases (Abbot bug), so try once, and then do it ourselves.

            final Combo combo = (Combo) o;

            final AtomicBoolean foundB = new AtomicBoolean(false);

            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {
                    String[] items = combo.getItems();
                    for (int x = 0; x < items.length; x++) {
                        if (items[x].equalsIgnoreCase(value)) {

                            SWTBotCombo sc = new SWTBotCombo(combo);
                            sc.setSelection(x);
                            sc.setFocus();
                            foundB.set(true);
                            break;
                        }
                    }
                }

            });

            found = foundB.get();

            if (!found) {
                logErr("Unable to find combo selection: " + value, options);
            }
        } else {
            logErr("Unable to set on unknown class:" + o.getClass(), options);
        }

        if (found) {
            pause(options.getDelayAfterActionInMsecs());
        }
    }

    public static void pause(long timeInMsecs) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeInMsecs);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void context(Object o) {
        // final Widget w = (Widget)o;

        out("Opening context on " + o.getClass().getName() + "(" + Magic.getText(o) + ")");

        SWTBotTreeItem ti = new SWTBotTreeItem((TreeItem) o);
        ti.select().pressShortcut(Keystrokes.SHIFT, Keystrokes.F10);

    }

    public static void waitForWidgetDispose(Object o) {
        Widget w = (Widget) o;

        while (!isWidgetDisposed(w)) {
            pause(1000);
        }

    }

    private static boolean isWidgetDisposed(final Widget w) {
        final AtomicBoolean result = new AtomicBoolean(false);
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                result.set(w.isDisposed());
            }

        });

        return result.get();
    }

    // waits timeoutInMs ms for the widget to dispose, throws an exception when time expires
    public static void waitForWidgetDispose(Object o, long timeoutInMs) {
        final Widget w = (Widget) o;
        // array of size 1 since it needs to be final to be set from inside the runnable

        while (!isWidgetDisposed(w)) {
            if (timeoutInMs <= 0) {
                final String[] widgetName = new String[1];

                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {
                        widgetName[0] = Magic.getText(w).getText();
                    }
                });

                throw new RuntimeException("Waiting for widget " + widgetName[0] + " to dispose timed out.");
            }
            final int interval = 1000;

            pause(interval);
            timeoutInMs -= interval;
        }
    }

    public static void go(String name, Widget neighbour) {
        go(name, neighbour, Option.getGlobalOptions());

    }

    public static void go(String name, Widget neighbour, Option options) {
        Object result = find(name, neighbour, options);
        if (result != null) {
            go(result, options);
        } else {
            logErr("Unable to find widget " + name, options);
        }

    }

    public static void go(Object o) {
        if (o instanceof String) {
            throw new RuntimeException("Param should be a UI element, not a String.");
        }
        go(o, Option.getGlobalOptions());
    }

    public static void go(Object o, Option options) {
        if (o == null) {
            throw new IllegalArgumentException(
                    "Object param may not be null; this likely means a widget could not be found in a previous step.");
        }

        boolean recognized = true;

        try {

            out("Clicking " + o.getClass().getName() + " (" + Magic.getText(o) + ") " + Magic.getText(o).getText());

            if (o instanceof Text) {
                SWTBotText st = new SWTBotText((Text) o);
                st.setFocus();

            } else if (o instanceof Button) {
                final Button b = (Button) o;
                final AtomicInteger style = new AtomicInteger();

                Display.getDefault().syncExec(new Runnable() {

                    @Override
                    public void run() {
                        style.set(b.getStyle());
                    }

                });
                if ((style.get() & SWT.RADIO) == SWT.RADIO) {
                    SWTBotRadio sr = new SWTBotRadio(b);
                    sr.click();

                } else if ((style.get() & SWT.CHECK) == SWT.CHECK) {
                    SWTBotCheckBox scb = new SWTBotCheckBox(b);
                    scb.click();

                } else {
                    SWTBotButton sb = new SWTBotButton(b);

                    long expireTimeInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(30, TimeUnit.SECONDS);
                    while (!sb.isEnabled() && System.nanoTime() < expireTimeInNanos) {
                        System.out.println("Waiting for button enabled.");
                        pause(1000);
                    }
                    sb.click();
                }

                // SWTBotRadio sr = new SWTBot

            } else if (o instanceof MenuItem) {
                throw new RuntimeException("Unsupported SWTBot operation, use goMenuItem.");
                // SWTBotMenu sm = new SWTBotMenu((MenuItem)o);
                // sm.click();
            } else if (o instanceof TableItem) {
                TableItem ti = (TableItem) o;
                SWTBotTableItem sti = new SWTBotTableItem(ti);
                sti.setFocus();
                sti.select();
                sti.click();
                // sti.click();
            } else if (o instanceof ViewPart) {
                ViewPart vp = (ViewPart) o;
                EclipseViewOpener.showViewNamed(vp.getPartName());

            } else if (o instanceof ViewReference) {
                ViewReference vw = (ViewReference) o;
                EclipseViewOpener.showViewNamed(vw.getPartName());
            } else if (o instanceof TreeItem) {
                TreeItem ti = (TreeItem) o;
                SWTBotTreeItem sti = new SWTBotTreeItem(ti);
                System.out.println("Clicking thing tree item: " + sti.getText());
                sti.setFocus();
                sti.select();
                try {
                    // This happens on Oxygen with Linux, but is optional, as select should cover it.
                    sti.click();
                } catch (Throwable t) {
                    System.err.println("Non-severe exception: " + t.getClass().getSimpleName() + " " + t.getMessage());
                }
            } else if (o instanceof CTabItem) {
                SWTBotCTabItem cti = new SWTBotCTabItem((CTabItem) o);
                cti.show();
            } else if (o instanceof ToolItem) {
            	ToolItem ti = (ToolItem)o;
            	if (SWTUtils.hasStyle((ToolItem)o, SWT.PUSH)) {
            		SWTBotToolbarPushButton sb = new SWTBotToolbarPushButton(ti);
                    long expireTimeInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(30, TimeUnit.SECONDS);
                    while (!sb.isEnabled() && System.nanoTime() < expireTimeInNanos) {
                        System.out.println("Waiting for button enabled.");
                        pause(1000);
                    }
                    sb.click();
            	}                    
            } else {
                logErr("Unrecognized tester item (3): " + o.getClass().getName(), options);
                recognized = false;
            }

            if (recognized) {
                pause(options.getDelayAfterActionInMsecs());
            }
        } catch (Error e) {
            e.printStackTrace();
        }

    }

    public static void goGlobal(final String name) {
        goGlobal(name, Option.getGlobalOptions());
    }

    public static void goGlobal(final String name, Option options) {
        Object o = findGlobal(name, options);

        if (o != null) {
            go(o, options);
        } else {
            logErr("Unable to find item in go(String) '" + name + "'", options);
        }

    }

    public static void go(final String name, Object neighbour) {
        if (neighbour instanceof Option) {
            throw new RuntimeException("Second parameter should not be Option");
        }

        go(name, neighbour, Option.getGlobalOptions());
    }

    public static void go(final String name, Object neighbour, Option option) {
        Object o = find(name, neighbour, option);
        if (o == null) {
            logErr("Unable to find item in goClose(String, Object) '" + name + "'", option);
            return;
        }

        go((Widget) o, option);
    }

    private static String utilgetRuntimePos() {
        StackTraceElement[] stearr = Thread.currentThread().getStackTrace();
        StackTraceElement ste = stearr[4];
        String className = ste.getClassName();
        if (className.contains(".")) {
            // Strip all but the class name
            className = className.substring(className.lastIndexOf(".") + 1);
        }
        return className + "." + ste.getMethodName() + ":" + ste.getLineNumber();
    }

    private static String padInt(int x) {
        String str = "" + x;
        while (str.length() < 4) {
            str = " " + str;
        }

        return str;

    }

    
    
    
	public static Shell activeShell() {

		final Shell shell[] = new Shell[1];
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				shell[0] = Display.getDefault().getActiveShell();
				
				if(shell[0] == null) {
					Shell[] shells = Display.getDefault().getShells();
					if(shells.length == 1) {
						shell[0] = shells[1];
					}
				}
				
			}
		});
		return shell[0];
	}

	

    public static void debugDumpNodes(Node n, int depth, OutputStream s) throws IOException {
        for (int x = 0; x < depth; x++) {
            s.write("  ".getBytes());
        }

        if (n.getItem() == null) {
            return;
        }

        UIItemInfo uui = Magic.getText(n.getItem());

        String uuiSubstring = "";
        if (uui != null) {
            if (uui.getText() != null) {
                uuiSubstring += uui.getText().trim();

            } else if (uui.getMultipleText() != null && uui.getMultipleText().size() > 0) {
                for (String str : uui.getMultipleText()) {
                    uuiSubstring += str.trim() + "|";
                }
            }

            if (uuiSubstring.trim().length() > 0) {
                uuiSubstring = " % " + uuiSubstring;
            }
        }

        String r = " - [" + padInt(depth) + " ] " + n.getItem().getClass().getName() + " " + uuiSubstring + "\n";
        s.write(r.getBytes());

        for (Node child : n.getChildren()) {
            debugDumpNodes(child, depth + 1, s);
        }

    }

    public static Shell shell(final Object widget) {
        final Shell[] result = new Shell[1];

        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                if (widget instanceof Control) {
                    Control w = (Control) widget;
                    result[0] = w.getShell();
                } else if (widget instanceof TreeItem) {
                    TreeItem ti = (TreeItem) widget;
                    result[0] = ti.getParent().getShell();
                } else {
                    logErr("shell(...) called on object w/o a shell." + widget + " " + widget.getClass().getName(),
                            Option.getGlobalOptions());
                }
            }

        });

        return result[0];
    }

    public static Object findGlobal(final String name) {
        return findGlobal(name, Option.getGlobalOptions());
    }

    public static Object findGlobal(final String name, final Option options) {

        final List<Node> matches = new ArrayList<Node>();

        for (int attempts = 0; attempts < (1 + options.getRetryAttempts()) && matches.size() == 0; attempts++) {
            matches.clear();

            @SuppressWarnings("unused")
            final int currAttempt = attempts;
            final Node parent = new Node("ROOT");

            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {

                    long startTimeInNanos = System.nanoTime();

                    Shell[] shells = Display.getDefault().getShells();

                    HashMap<Object, Boolean> allSeen = new HashMap<Object, Boolean>();
                    for (Shell s : shells) {

                        if (!s.isVisible()) {
                            continue;
                        }

                        Node shell = new Node(s);
                        parent.addChild(shell);

                        try {
                            // TODO: Would it be faster if I specify s, instead of null, here?
                            Magic.visitQueue(s, name, parent, null, matches, allSeen, options);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    long msecsForNodeBuilder = TimeUnit.MILLISECONDS.convert((System.nanoTime() - startTimeInNanos), TimeUnit.NANOSECONDS);
                    System.out.println("Node builder time for all shells [" + name + "]: " + (msecsForNodeBuilder / 1000d) + " seconds");
                    //
                    // if(name.equals("")) {
                    //
                    // FileOutputStream fos;
                    // try {
                    // fos = new FileOutputStream(new File("d:\\delme\\not-found-"+name+"-"+currAttempt+".log"));
                    // debugDumpNodes(parent, 0, fos);
                    // fos.close();
                    // } catch (FileNotFoundException e) {
                    // e.printStackTrace();
                    // } catch (IOException e) {
                    // }
                    // }
                    //
                    // breadthFirstSearch(parent, name, matches, null, options);

                }
            });

            if (matches.size() == 0) {
                logErr("[findGlobal] * " + name + " not found.", options);

                //
                // Display.getDefault().syncExec(new Runnable() {
                // @Override
                // public void run() {
                // try {
                // FileOutputStream fos = new FileOutputStream(new File("d:\\delme\\not-found-"+name+"-"+currAttempt+".log"));
                // dumpNodes(parent, 0, fos);
                // fos.close();
                // } catch(Exception e) {
                // e.printStackTrace();
                // }
                // }
                //
                // });

                pause(2000);

            } else if (matches.size() > 1) {
                final List<Object> result = new ArrayList<Object>();

                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {
                        logOut("\n[findGlobal] Multiple matches [" + name + "]:", options);

                        // Look for a visible item
                        for (Node n : matches) {
                            String subText = "";
                            if (n.getItem() instanceof Control) {
                                Control c = (Control) n.getItem();
                                subText = "" + c.isVisible() + " " + c.hashCode();

                                if (c.isVisible()) {
                                    result.add(c);
                                }
                            } else {
                                result.add(n.getItem());
                            }

                            logOut(" - " + n.getItem() + " | " + name.getClass().getName() + " " + subText, options);
                        }

                        // No visible items found, just return the first
                        if (result.size() == 0) {
                            result.add(matches.get(0).getItem());
                        }
                    }
                });

                return result.get(0);

            } else {
                logOut("[findGlobal] * " + name + " found.", options);

                return matches.get(0).getItem();
            }

        } // end for

        if (options.isThrowExceptionOnNotFound()) {
            throw new RuntimeException("Unable to locate widget: " + name);
        }

        return null;
        // return matches.size() > 0 ? matches.get(0).getItem() : null;

    }

    public static Object find(final String name, final Object neighbour) {
        if (neighbour instanceof Option) {
            throw new RuntimeException("Second parameter should not be Option");
        }

        return find(name, neighbour, Option.getGlobalOptions());
    }


    private static int fCnt=0;

    public static Object find(final String name, final Object neighbour, final Option options) {
        final List<Node> matches = new ArrayList<Node>();

        for (int attempts = 0; attempts < (1 + options.getRetryAttempts()) && matches.size() == 0; attempts++) {
            Node curr = new Node("ROOT");
            final Node parent = curr;

            @SuppressWarnings("unused")
            final int finalAttempt = attempts;

            matches.clear();

            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {

                    Shell shellConstraint = null;
                    if (neighbour instanceof Control) {
                        shellConstraint = ((Control) neighbour).getShell();
                    }

                    try {
                        long startTimeInNanos = System.nanoTime();
                        Magic.visitQueue(neighbour, name, parent, shellConstraint, matches, new HashMap<Object, Boolean>(), options);

                        logOut("* Magic.visitQueue time: [" + name + "]: "
                                + ((double) TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS))
                                        / 1000d,
                                options);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }

                    if(name.equals("Start parameters:")) {
                        try {
                            FileOutputStream fos = new FileOutputStream(new File("MWFout." + fCnt++ + ".log"));
                            debugDumpNodes(parent, 0, fos);
                            fos.close();
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            if (matches.size() == 0) {
                logErr("Dump to MWFout - [find] * " + name + " not found.", options);

                try {
                    FileOutputStream fos = new FileOutputStream(new File("MWFerr." + fCnt++ + ".log"));
                    debugDumpNodes(parent, 0, fos);
                    fos.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }

                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                }
            } else {
                if (matches.size() > 1) {

                    final List<Object> result = new ArrayList<Object>();
                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            logOut("\n*findLocal - Multiple matches [" + name + "]:", options);

                            for (Node n : matches) {
                                String subtext = "";
                                if (n.getItem() instanceof Control) {
                                    Control control = (Control) n.getItem();
                                    if (control.isVisible()) {
                                        result.add(control);
                                    }

                                    subtext = "" + control.isVisible();
                                } else {
                                    result.add(n.getItem());
                                }

                                logOut(" - " + n.getItem() + " | " + name.getClass().getName() + " " + subtext, options);
                            }

                            if (result.size() == 0) {
                                result.add(matches.get(0).getItem());
                            }

                        }
                    });

                    return result.get(0);
                }
            }
        }

        if (matches.size() == 0) {
            if (options.isThrowExceptionOnNotFound()) {
                throw new RuntimeException("Unable to locate widget: " + name);
            }
        }

        return matches.size() > 0 ? matches.get(0).getItem() : null;

    }

    static boolean utilIsNodeAMatch(Node n, String matchingText, List<Node> matches, Option options) {

        if (n.getItem() instanceof Control) {
            Control control = (Control) n.getItem();
            if (control.isDisposed()) {
                return false;
            }

            if (!control.isVisible() && !options.isUnrestrictedSearch()) {
                return false;
            }

        }

        if (n.getItem() instanceof Widget) {
            Widget w = (Widget) n.getItem();
            if (w.isDisposed()) {
                return false;
            }

            // For Items, check if the parent is visible
            if (w instanceof Item && !options.isUnrestrictedSearch()) {

                if (w instanceof MenuItem) {
                    MenuItem mi = (MenuItem) w;
                    if (mi.getParent() != null && !mi.getParent().isDisposed() && !mi.getParent().isVisible()) {
                        return false;
                    }
                } else if (w instanceof TreeItem) {
                    TreeItem ti = (TreeItem) w;
                    if (ti.getParent() != null && !ti.getParent().isDisposed() && !ti.getParent().isVisible()) {
                        return false;
                    }
                }
            }

        }

        UIItemInfo itemInfo = Magic.getText(n.getItem());

        if (itemInfo.getText() != null) {
            String text = itemInfo.getText().replace("&", "");

            if (options.isUseContains()) {

                if (text.trim().toLowerCase().contains(matchingText.trim().toLowerCase()) && !matches.contains(n)) {
                    return true;
                }

            } else {

                if (text.trim().equalsIgnoreCase(matchingText.trim()) && !matches.contains(n)) {
                    return true;
                }
            }
        }

        if (itemInfo.getMultipleText().size() > 0) {
            for (String str : itemInfo.getMultipleText()) {
                String text = str.replace("&", "");

                if (options.isUseContains()) {
                    if (text.trim().toLowerCase().contains(matchingText.trim().toLowerCase()) && !matches.contains(n)) {
                        return true;
                    }

                } else {

                    if (text.trim().equalsIgnoreCase(matchingText.trim()) && !matches.contains(n)) {
                        return true;
                    }
                }

            }
        }
        
        // Added for Liberty Tools Eclipse
        if (itemInfo.getTooltipText() != null) {
            String text = itemInfo.getTooltipText().replace("&", "");

            if (options.isUseContains()) {

                if (text.trim().toLowerCase().contains(matchingText.trim().toLowerCase()) && !matches.contains(n)) {
                    return true;
                }

            } else {

                if (text.trim().equalsIgnoreCase(matchingText.trim()) && !matches.contains(n)) {
                    return true;
                }
            }
        }

        return false;

    }

    private MagicWidgetFinder() {
    }

    private static void logOut(String str, Option o) {
        if (o.isLogOut()) {
            System.out.println("[" + utilgetRuntimePos() + "] " + str);
        }
    }

    private static void logErr(String str, Option o) {
        if (o.isLogErrors()) {
            System.err.println("[" + utilgetRuntimePos() + "] " + str);
        }
    }

    public static interface WidgetMatcher {

        boolean isMatch(Node n);

    }

    public static class TypeMatcher implements WidgetMatcher {

        Class<?> type;

        public TypeMatcher(Class<?> type) {
            this.type = type;
        }

        @Override
        public boolean isMatch(Node n) {
            if (type.isInstance(n.getItem())) {
                return true;
            } else {
                return false;
            }
        }

    }

    public static class Option {
        private int retryAttempts = 5;

        private int delayAfterActionInMsecs = 500;

        private boolean unrestrictedSearch = false;

        private WidgetMatcher matcher = null;

        private boolean useContains = false;

        private boolean logErrors = true;

        private boolean throwExceptionOnNotFound = true;

        @SuppressWarnings("rawtypes")
        private Class widgetClass = null;

        private boolean logOut = true; // whether to log non-errors

        private static final Option DEFAULT = (new Builder()).setDelayAfterActionInMsecs(500).setRetryAttempts(5)
                .setUnrestrictedSearch(false).setMatcher(null).setUseContains(false).setWidgetClass(null).setThrowExceptionOnNotFound(true)
                .build();

        private static Option globalOptions = DEFAULT;

        private Option() {
        }

        public static Builder factory() {
            return new Builder(globalOptions);
        }

        private Option(Option copy) {
            retryAttempts = copy.getRetryAttempts();
            delayAfterActionInMsecs = copy.getDelayAfterActionInMsecs();
            unrestrictedSearch = copy.isUnrestrictedSearch();
            matcher = copy.getMatcher();
            useContains = copy.isUseContains();
            logErrors = copy.isLogErrors();
            widgetClass = copy.getWidgetClass();
            logOut = copy.isLogOut();
            throwExceptionOnNotFound = copy.isThrowExceptionOnNotFound();
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public int getDelayAfterActionInMsecs() {
            return delayAfterActionInMsecs;
        }

        public boolean isUnrestrictedSearch() {
            return unrestrictedSearch;
        }

        public boolean isUseContains() {
            return useContains;
        }

        public WidgetMatcher getMatcher() {
            return matcher;
        }

        public boolean isLogErrors() {
            return logErrors;
        }

        public boolean isLogOut() {
            return logOut;
        }

        @SuppressWarnings("rawtypes")
        public Class getWidgetClass() {
            return widgetClass;
        }

        public boolean isThrowExceptionOnNotFound() {
            return throwExceptionOnNotFound;
        }

        // ------------------------------------------

        public static void setGlobalOptions(Option o) {
            globalOptions = o;
        }

        public static Option getGlobalOptions() {
            return globalOptions;
        }

        public static void resetGlobalOptions() {
            globalOptions = DEFAULT;
        }

        // ------------------------------------------

        public static class Builder {

            Option inner;

            private Builder() {
                inner = new Option();
            }

            private Builder(Option copy) {
                inner = new Option(copy);

            }

            public Builder setRetryAttempts(int x) {
                inner.retryAttempts = x;
                return this;
            }

            public Builder setDelayAfterActionInMsecs(int x) {
                inner.delayAfterActionInMsecs = x;
                return this;
            }

            public Builder setUnrestrictedSearch(boolean b) {
                inner.unrestrictedSearch = b;
                return this;
            }

            public Option build() {
                return inner;
            }

            @Deprecated
            public Builder setMatcher(WidgetMatcher matcher) {
                return matcher(matcher);
            }

            public Builder matcher(WidgetMatcher matcher) {
                inner.matcher = matcher;
                return this;
            }

            @Deprecated
            public Builder setUseContains(boolean b) {
                return useContains(b);
            }

            public Builder useContains(boolean b) {
                inner.useContains = b;
                return this;
            }

            public Builder setLogErrors(boolean b) {
                inner.logErrors = b;
                return this;
            }

            public Builder setLogOut(boolean b) {
                inner.logOut = b;
                return this;
            }

            @SuppressWarnings("rawtypes")
            @Deprecated
            public Builder setWidgetClass(Class c) {
                return widgetClass(c);
            }

            @SuppressWarnings("rawtypes")
            public Builder widgetClass(Class c) {
                inner.widgetClass = c;
                return this;
            }

            public Builder setThrowExceptionOnNotFound(boolean b) {
                inner.throwExceptionOnNotFound = b;
                return this;
            }
        }

    }

    /**
     * Given an existing widget, find the closest (widget) in pixels in the given direction. The can be used to find unlabeled widgets
     * (like combo boxes) that are empty, or whose contents is unknown, but for which the UI location is known relative to an existing
     * thread. For example: Label label = ... Web module version Control c = findControlInRange(label, Combo.class, Direction.SOUTH)
     * This would locate the closest combo box to label, and that is below label (south)
     */
    public static class ControlFinder {

        public static enum Direction {
            NORTH, EAST, SOUTH, WEST
        };

        public static Control findControlInRange(final Object o, final Class<?> controlType, final Direction d) {

            int numTriesLeft = 10;

            Control result = null;

            do {
                result = findControlInRange(o, controlType, d, Option.getGlobalOptions());

                if (result == null) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                numTriesLeft--;
            } while (result == null && numTriesLeft > 0);

            return result;
        }

        @SuppressWarnings("rawtypes")
        public static Control findControlInRange(final Object o, final Class controlType, final Direction d, final Option options) {
            final Control control = (Control) o;

            final List<Object[]> result = new ArrayList<Object[]>();

            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {

                    final Point controlPos = control.toDisplay(0, 0);

                    Node shellNode = new Node(control);

                    try {
                        Magic.visitQueue(control, null, shellNode, null, null, new HashMap<Object, Boolean>(), options);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Queue<Node> nodes = new LinkedList<Node>();
                    nodes.offer(shellNode);
                    while (nodes.size() > 0) {

                        Node current = nodes.poll();

                        if (current.getItem() instanceof Control) {
                            Control item = (Control) current.getItem();

                            if (item.isDisposed()) {
                                continue;
                            }
                            if (item.getShell() != control.getShell()) {
                                continue;
                            }

                            if (!item.isVisible()) {
                                continue;
                            }

                            if (controlType.isInstance(item) && item != control) {
                                Point itemPos = item.toDisplay(0, 0);

                                int diffX = itemPos.x - controlPos.x;
                                int diffY = itemPos.y - controlPos.y;

                                // System.out.println("FCIR: "+diffX+" "+diffY);

                                if (d == Direction.NORTH && diffY <= 5) {
                                    result.add(new Object[] { current.getItem(), Math.sqrt(Math.pow(diffX * 2, 2) + Math.pow(diffY, 2)),
                                            new Point(diffX, diffY) });
                                }
                                if (d == Direction.SOUTH && diffY >= 5) {
                                    result.add(new Object[] { current.getItem(), Math.sqrt(Math.pow(diffX * 2, 2) + Math.pow(diffY, 2)),
                                            new Point(diffX, diffY) });
                                }
                                if (d == Direction.EAST && diffX > 5) {
                                    result.add(new Object[] { current.getItem(), Math.sqrt(Math.pow(diffX, 2) + Math.pow(diffY * 2, 2)),
                                            new Point(diffX, diffY) });
                                }
                                if (d == Direction.WEST && diffX < 5) {
                                    result.add(new Object[] { current.getItem(), Math.sqrt(Math.pow(diffX, 2) + Math.pow(diffY * 2, 2)),
                                            new Point(diffX, diffY) });
                                }

                            }
                        }

                        for (Node child : current.getChildren()) {
                            nodes.offer(child);
                        }
                    }
                }

            });

            if (result.size() > 0) {
                // Sort by distance and return the closest
                Collections.sort(result, new Comparator<Object[]>() {
                    @Override
                    public int compare(Object[] object1, Object[] object2) {
                        Double d1 = (Double) object1[1];
                        Double d2 = (Double) object2[1];

                        return (int) (d1 * 1000d - d2 * 1000d);
                    }
                });

                System.out.println("Control finder objects:");
                for (Object[] e : result) {
                    System.out.println(" - " + e[0] + " " + e[1] + " " + e[2]);
                }

                Object[] finalResult = result.get(0);

                return (Control) finalResult[0];
            } else {
                return null;
            }

        }

    }

    public static class UIItemInfo {
        private String text = null;
        private String tooltipText = null;

        private List<String> multipleText = new ArrayList<String>();

        boolean recognized = false;

        public void setText(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setTooltipText(String tooltipText) {
            this.tooltipText = tooltipText;
        }

        public String getTooltipText() {
            return tooltipText;
        }

        public void setRecognized(boolean recognized) {
            this.recognized = recognized;
        }

        public List<String> getMultipleText() {
            return multipleText;
        }

        @Override
        public String toString() {
            return text != null ? text : (tooltipText != null ? tooltipText : "n/a");
        }
    }

    public static class QueueObj {

        public QueueObj(Object obj, Node parent, int depth) {
            this.obj = obj;
            this.parent = parent;
            this.depth = depth;
        }

        Object obj;
        Node parent;
        int depth;

        @Override
        public String toString() {
            return depth + ") " + obj.getClass().getName();
        }
    }

    public static class Node {
        Object item;
        List<Node> children = new ArrayList<Node>();

        public Node(Object parent) {
            if (parent == null) {
                throw new RuntimeException("null value in constructor.");
            }
            this.item = parent;
        }

        public Object getItem() {
            return item;
        }

        public List<Node> getChildren() {
            return children;
        }

        public void addChild(Node o) {
            children.add(o);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node)) {
                return false;
            }

            return item.equals(((Node) o).getItem());
        }

        @Override
        public String toString() {
            UIItemInfo info = Magic.getText(item);
            String text = "";
            if (info != null && info.getText() != null) {
                text = " - % " + info.getText();
            }
            return item.getClass().getName() + text;
        }
    }

    public static class Magic {

        private static boolean shouldSkipObjectQ(Object obj, Shell shellConstraint, Option options) {

            if (obj instanceof Control) {
                final Control control = (Control) obj;
                if (control.isDisposed()) {
                    return true;
                }

                if (shellConstraint != null && control.getShell() != shellConstraint) {
                    // If shell is specified, we only want to look at controls w/ the same shell
                    return true;
                }

                if (!options.isUnrestrictedSearch()) {
                    final AtomicBoolean isVisible = new AtomicBoolean(true);
                    Display.getDefault().syncExec(new Runnable() {

                        @Override
                        public void run() {
                            isVisible.set(control.isVisible());
                        }

                    });

                    if (!isVisible.get()) {
                        return true;
                    }
                }
            }

            if (obj instanceof Widget) {
                final Widget w = (Widget) obj;
                if (w.isDisposed()) {
                    return true;
                }

                // For Items, check if the parent is visible
                if (!options.isUnrestrictedSearch() && (w instanceof MenuItem || w instanceof TreeItem)) {
                    if (w instanceof MenuItem) {
                        MenuItem mi = (MenuItem) w;
                        if (mi.getParent() != null && !mi.getParent().isDisposed() && !mi.getParent().isVisible()) {
                            return true;
                        }
                    } else if (w instanceof TreeItem) {
                        TreeItem ti = (TreeItem) w;
                        if (ti.getParent() != null && !ti.getParent().isDisposed() && !ti.getParent().isVisible()) {
                            return true;
                        }
                    }
                }

            }

            return false;
        }

        private static void addToQueue(QueueObj obj, Queue<QueueObj> queue) {
            if (obj == null || obj.obj == null) {
                System.err.println("* null value added to queue.");
                return;
            }
            if (obj.obj instanceof Label) {
                Label l = (Label)obj.obj;
                if (l.getText().contains("arameter")) {
                    System.out.println("SKSK: adding " + obj.obj + ", with text = " + l.getText() + " with display = " + l.getDisplay());
                    System.out.println("SKSK: from parent = " + obj.parent);
                    System.out.println("SKSK: -- beg dump stack --");
                    Thread.dumpStack();
                    System.out.println("SKSK: -- end dump stack --");                    
                }
            }
            queue.offer(obj);
        }

        private static void addObjectFieldsQ(Queue<QueueObj> queue, Object obj, Node currNode, QueueObj currQueueObj, Option options,
                List<Object> visitedObjects) throws IllegalArgumentException, IllegalAccessException {

            List<Field> farr = Magic.getAllFields(obj.getClass());

            for (Field f : farr) {

                boolean accessible = f.isAccessible();
                try {
                    f.setAccessible(true);
                } catch (InaccessibleObjectException ioe) {
                    continue;
                }

                Object fieldObject = f.get(obj);

                f.setAccessible(accessible);

                if (fieldObject == null) {
                    continue;
                }

                String fieldObjectClassName = fieldObject.getClass().getName();
                if (fieldObjectClassName.contains("org.eclipse.osgi.internal") || fieldObjectClassName.contains("java.lang.reflect")
                        || fieldObjectClassName.contains("org.eclipse.core.internal")
                        || fieldObjectClassName.contains("org.eclipse.e4.ui.workbench.renderers.swt.")
                        || fieldObjectClassName.equals("org.eclipse.swt.widgets.Event") || fieldObjectClassName.contains("ClassLoader")
                        || fieldObjectClassName.contains("org.eclipse.wst.common.frameworks.internal.ui.ValidationStatus")
                        || fieldObjectClassName.contains("org.eclipse.ui.internal.contexts.") || fieldObject instanceof Class) {
                    continue;
                }

                visitedObjects.add(fieldObject);

                if (Magic.isUIObject(fieldObject, options)) {
                    try {
                        List<Object> toVisit = new ArrayList<Object>();

                        if (fieldObject.getClass().isArray()) {

                            int length = Array.getLength(fieldObject);
                            for (int i = 0; i < length; i++) {
                                Object arrayElement = Array.get(fieldObject, i);
                                if (arrayElement != null && !arrayElement.getClass().isPrimitive()
                                        && Magic.isUIObject(arrayElement, options)) {
                                    toVisit.add(arrayElement);
                                    visitedObjects.add(arrayElement);
                                }
                            }

                        } else {
                            toVisit.add(fieldObject);
                        }

                        for (Object o : toVisit) {
                            addToQueue(new QueueObj(o, currNode, currQueueObj.depth + 1), queue);

                        }
                    } catch (ClassCastException e) {
                        e.printStackTrace();
                    } catch (Error err) {
                        err.printStackTrace();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        System.out.println("Error: " + obj.getClass().getName() + " [" + e.getClass().getName() + "]");
                    }

                }

            }

        }

        public static void addMiscFieldsQ(final Object uiObject, final Node thisNode, final QueueObj currQueueObj,
                final List<Object> visitedObjects, final Queue<QueueObj> queue) {

            if (uiObject instanceof CommonNavigator) {
                CommonNavigator cn = (CommonNavigator) uiObject;

                Tree t = (Tree) cn.getCommonViewer().getTree();
                if (t != null && !visitedObjects.contains(t)) {
                    addToQueue(new QueueObj(t, thisNode, currQueueObj.depth + 1), queue);
                }
            }

            if (uiObject instanceof Composite) {
                Composite c = (Composite) uiObject;
                for (Control control : c.getChildren()) {
                    if (control != null && !visitedObjects.contains(control)) {

                        addToQueue(new QueueObj(control, thisNode, currQueueObj.depth + 1), queue);
                    }
                }
            }

            if (uiObject instanceof Menu) {
                Menu m = (Menu) uiObject;
                if (!m.isDisposed()) {
                    for (MenuItem mi : m.getItems()) {
                        if (mi != null && !visitedObjects.contains(mi)) {

                            addToQueue(new QueueObj(mi, thisNode, currQueueObj.depth + 1), queue);
                        }
                    }
                }
            }

            if (uiObject instanceof Tree) {
                Tree t = (Tree) uiObject;
                for (TreeItem ti : t.getItems()) {
                    if (ti != null && !visitedObjects.contains(ti)) {
                        addToQueue(new QueueObj(ti, thisNode, currQueueObj.depth + 1), queue);
                    }
                }
            }

            if (uiObject instanceof Table) {
                Table t = (Table) uiObject;
                for (TableItem ti : t.getItems()) {
                    if (ti != null && !visitedObjects.contains(ti)) {
                        addToQueue(new QueueObj(ti, thisNode, currQueueObj.depth + 1), queue);
                    }

                }
            }

            if (uiObject instanceof DialogPage) {
                DialogPage dp = (DialogPage) uiObject;
                Control inner = dp.getControl();
                if (inner != null && !visitedObjects.contains(inner)) {
                    addToQueue(new QueueObj(inner, thisNode, currQueueObj.depth + 1), queue);
                }
            }

        }

        public static void visitQueue(final Object initialObject, String matchingText, final Node parent, final Shell shellConstraint,
                List<Node> matches, final HashMap<Object, Boolean> allSeen, final Option options)
                throws IllegalArgumentException, IllegalAccessException {
            if (initialObject == null) {
                return;
            }

            Queue<QueueObj> queue = new LinkedList<QueueObj>();

            queue.offer(new QueueObj(initialObject, parent, 0));

            while (queue.size() > 0) {

                final QueueObj objPoll = queue.poll();

                Object obj = objPoll.obj;

                if (objPoll.depth > 30) {
                    continue;
                }

                if (obj.getClass().isPrimitive()) {
                    continue;
                }

                if (shouldSkipObjectQ(obj, shellConstraint, options)) {
                    continue;
                }

                try {

                    if (allSeen.get(obj) != null) {
                        continue;
                    }

                } catch (Exception e) {
                    continue;
                } // Some objects are thrown an exception when their hashcode is called; so ignore it and continue.

                allSeen.put(obj, true);

                Node parentNode = objPoll.parent;

                Node thisNode = new Node(obj);
                if (parentNode != null) {
                    parentNode.addChild(thisNode);
                }

                if (matchingText != null && matches != null && utilIsNodeAMatch(thisNode, matchingText, matches, options)) {

                    boolean isMatch = true;

                    if (isMatch && options.getMatcher() != null) {
                        // If the user has specified a match, then check to see if 'thisNode' matches
                        if (!options.getMatcher().isMatch(thisNode)) {
                            isMatch = false;
                        }

                    }

                    if (isMatch && options.getWidgetClass() != null) {
                        isMatch = options.getWidgetClass().isInstance(obj);
                    }

                    if (isMatch) {
                        matches.add(thisNode);
                    }

                    // We only keep searching if the user has told us too, otherwise we return now that we have found a result
                    if (isMatch && !options.isUnrestrictedSearch()) {
                        break;
                    }
                }

                /// -------
                List<Object> visitedObjects = new ArrayList<Object>();

                addObjectFieldsQ(queue, obj, thisNode, objPoll, options, visitedObjects);

                addMiscFieldsQ(obj, thisNode, objPoll, visitedObjects, queue);

            }

            // System.out.println("final size:"+size);

        }

        public static boolean isUIObject(Object o, Option options) {

            if (o.getClass().getName().contains("Finalizer")) {
                return false;
            }

            if (o instanceof Widget) {
                return true;
            }

            if (o instanceof Collection || o instanceof Map || o instanceof Map.Entry) {
                return true;
            }

            if (o instanceof CommonNavigator) {
                return true;
            }

            if (o.getClass().isArray()) {
                return true;
            }

            String className = o.getClass().getName();
            if (className.equals(String.class.getName()) || className.equals(Boolean.class.getName())
                    || className.equals(Long.class.getName()) || className.equals(Byte.class.getName())
                    || className.equals(Integer.class.getName()) || className.equals(Double.class.getName())
                    || className.equals(Short.class.getName()) || className.equals(Float.class.getName())
                    || className.equals(Integer.class.getName()) || className.equals(Character.class.getName())) {
                return false;
            }

            // This code previously didn't work here:
            //
            // if(options.isUnrestrictedSearch()) {
            // return true;
            // }
            //
            // There's just way too much data to handle.

            Class<?> curr = o.getClass();

            boolean foundEclipse = false;

            do {
                if (curr.getName().contains("org.eclipse.e4") || curr.getName().contains("org.eclipse.swt")
                        || curr.getName().contains("org.eclipse.ui.") || curr.getName().contains("org.eclipse.jface")
                        || (curr.getName().contains("com.ibm.etools.") /* && curr.getName().contains(".ui.") */ ) // slick ui : |
                        || o instanceof Reference) {
                    foundEclipse = true;
                }

                curr = curr.getSuperclass();
            } while (curr != null && !foundEclipse);

            return foundEclipse;
        }

        @SuppressWarnings("rawtypes")
        static boolean simpleContains(Collection c, Object o) {
            try {
                return c.contains(o);
            } catch (Exception e) {

                for (Object object : c) {

                    if (object == o) {
                        return true;
                    }

                }

                return false;

            }
        }

        @SuppressWarnings("rawtypes")
        static List<Field> getAllFields(Class param) {

            List<Field> result = new ArrayList<Field>();
            Class c = param;

            do {
                for (Field f : c.getDeclaredFields()) {
                    if (!result.contains(f)) {
                        result.add(f);
                    }
                }

                c = c.getSuperclass();
            } while (c != null);

            return result;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static String invokeGetter(Object o, String method) {
            Class c = o.getClass();

            try {
                Method getText = c.getMethod(method);
                return (String) getText.invoke(o);
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                // e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (Throwable t) {
                // Ignore
            }

            return null;

        }

        /** Not currently used */
        @SuppressWarnings("unused")
        private static UIItemInfo getTextNew(Object o) {
            UIItemInfo info = new UIItemInfo();

            if (o instanceof MenuItem) {
                MenuItem mi = (MenuItem) o;

                // Strip the hotkey, for example, F2 or Ctrl+Shift+W
                String text = mi.getText();
                int tab = text.indexOf("\t");
                if (tab != -1) {
                    text = text.substring(0, tab).trim();
                }

                info.setText(text);
                return info;
            }

            if (o instanceof Combo) {
                Combo c = (Combo) o;
                String[] items = c.getItems();

                for (String str : items) {
                    info.getMultipleText().add(str);
                }
                return info;
            }

            String getText = invokeGetter(o, "getText");

            info.setRecognized(false);

            if (getText != null) {
                info.setText(getText);
                info.setRecognized(true);
            }

            String getTooltipText = invokeGetter(o, "getToolTipText");
            if (getTooltipText != null) {
                info.setTooltipText(getTooltipText);
                info.setRecognized(true);

            }

            return info;

        }

        public static UIItemInfo getText(final Object o) {

            UIItemInfo info = new UIItemInfo();

            if (Display.getCurrent() == null) {

                final List<UIItemInfo> result = new ArrayList<UIItemInfo>();

                Display.getDefault().syncExec(new Runnable() {

                    @Override
                    public void run() {

                        result.add(getText(o));
                        // getText();

                    }

                });

                if (result.size() > 0) {
                    return result.get(0);
                }

                return info;
            }

            if (o == null) {
                throw new IllegalArgumentException(
                        "param must not be null. This likely means a widget from a previous step could not be located.");
            }

            info.setRecognized(true);

            if (o instanceof ToolBar) {
                ToolBar tb = (ToolBar) o;
                info.setTooltipText(tb.getToolTipText());
            }
            if (o instanceof TreeItem) {
                TreeItem ti = (TreeItem) o;
                info.setText(ti.getText());
            } else if (o instanceof ToolItem) {
                ToolItem ti = (ToolItem) o;
                info.setText(ti.getText());
                info.setTooltipText(ti.getToolTipText());

            } else if (o instanceof CTabFolder) {
                CTabFolder ctab = (CTabFolder) o;
                info.setTooltipText(ctab.getToolTipText());

            } else if (o instanceof Label) {
                Label label = (Label) o;
                info.setText(label.getText());
                info.setTooltipText(label.getToolTipText());

            } else if (o instanceof Button) {
                Button b = (Button) o;
                info.setText(b.getText());
                info.setTooltipText(b.getToolTipText());

            } else if (o instanceof Text) {
                Text t = (Text) o;
                info.setText(t.getText());
                info.setTooltipText(t.getToolTipText());

            } else if (o instanceof ToolItem) {
                ToolItem ti = (ToolItem) o;
                info.setText(ti.getText());
                info.setTooltipText(ti.getToolTipText());

            } else if (o instanceof TreeColumn) {
                TreeColumn tc = (TreeColumn) o;
                info.setText(tc.getText());
                info.setTooltipText(tc.getToolTipText());

            } else if (o instanceof MenuItem) {
                MenuItem mi = (MenuItem) o;

                if (!mi.isDisposed()) {
                    // Strip the hotkey, for example, F2 or Ctrl+Shift+W
                    String text = mi.getText();
                    int tab = text.indexOf("\t");
                    if (tab != -1) {
                        text = text.substring(0, tab).trim();
                    }

                    info.setText(text);
                }
            } else if (o instanceof CTabItem) {
                CTabItem ct = (CTabItem) o;
                info.setText(ct.getText());
                info.setTooltipText(ct.getToolTipText());

            } else if (o instanceof CLabel) {
                CLabel cl = (CLabel) o;

                info.setText(cl.getText());
                info.setTooltipText(cl.getToolTipText());

            } else if (o instanceof TableItem) {
                TableItem ti = (TableItem) o;
                info.setText(ti.getText());

            } else if (o instanceof Link) {
                Link l = (Link) o;
                info.setText(l.getText());
                info.setTooltipText(l.getToolTipText());

            } else if (o instanceof Combo) {
                Combo c = (Combo) o;
                String[] items = c.getItems();

                for (String str : items) {
                    info.getMultipleText().add(str);
                }

            } else if (o instanceof Group) {
                Group g = (Group) o;
                info.setText(g.getText());
                info.setTooltipText(g.getToolTipText());

            } else if (o instanceof org.eclipse.ui.part.ViewPart) {
                org.eclipse.ui.part.ViewPart vp = (org.eclipse.ui.part.ViewPart) o;

                info.setText(vp.getTitle());
                info.setTooltipText(vp.getTitleToolTip());
            } else if (o instanceof org.eclipse.ui.internal.ViewReference) {
                ViewReference vr = (ViewReference) o;

                info.setText(vr.getTitle());
                info.setTooltipText(vr.getTitleToolTip());

            } else if (o instanceof Shell) {
                Shell s = (Shell) o;
                info.setText(s.getText());
                info.setTooltipText(s.getToolTipText());

            } else if (o instanceof Tree) {
                // Ignore
            } else if (o instanceof org.eclipse.swt.widgets.List) {
                // Ignore
            } else if (o instanceof Sash) {
                // Ignore
            } else if (o instanceof ProgressBar) {
                // Ignore
            } else if (o.getClass().getName().equals("org.eclipse.e4.ui.widgets.ImageBasedFrame")) {
                /// Ignore
            } else if (o instanceof Composite) {
                // Ignore

            } else if (o instanceof Scale) {
                // ignore

            } else if (o instanceof Control) {
                info.setRecognized(false);
                System.err.println("Unrecognized: " + o.getClass().getName());
            }

            return info;
        }
    }

    private static class EclipseViewOpener {

        public static IViewPart showViewNamed(final String partName) {
            IViewReference[] references = getActivePage(true).getViewReferences();
            for (IViewReference reference : references) {
                if (partName.equalsIgnoreCase(reference.getPartName())) {
                    // if (ExtendedComparator.stringsMatch(partName,
                    // reference.getPartName()))
                    return showView(reference.getId());
                }
            }
            return null;
        }

        /**
         * Shows a view with a specified ID on the currently acitve workbench page.
         * 
         * @see IWorkbenchPage#showView(String)
         */
        private static IViewPart showView(final String id) {
            final IWorkbenchPage page = getActivePage(true);

            final IViewPart[] result = new IViewPart[1];

            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {
                    try {
                        result[0] = page.showView(id);
                    } catch (PartInitException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            });

            return result[0];

        }

        private static IWorkbenchPage getActivePage(boolean activate) {
            IWorkbenchWindow window = getActiveWindow(activate);
            return window.getActivePage();
        }

        private static IWorkbench getWorkbench() {
            return PlatformUI.getWorkbench();
        }

        /**
         * Gets the active {@link IWorkbenchWindow}.
         * 
         * @param activate If <code>activate</code> is <code>true</code> and no workbench window is active then a visible workbench window
         *        will be activated. If <code>activate</code> is <code>false</code> and no workbench window is active then no workbench
         *        window will be activated and <code>null</code> will be returned.
         * 
         * @return the active workbench window or <code>null</code>
         */
        private static IWorkbenchWindow getActiveWindow(boolean activate) {

            // Get the active workbench window.
            final IWorkbench workbench = getWorkbench();

            final IWorkbenchWindow[] window = new IWorkbenchWindow[1];
            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {
                    window[0] = workbench.getActiveWorkbenchWindow();
                }
            });

            // If there isn't one and activate was specified then activate the first
            // visible workbench
            // window (if any).
            if (window[0] == null && activate) {
                final IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();

                Display.getDefault().syncExec(new Runnable() {

                    @Override
                    public void run() {
                        for (IWorkbenchWindow workbenchWindow : windows) {
                            Shell shell = workbenchWindow.getShell();
                            if (shell.isVisible()) {
                                shell.setActive();
                                window[0] = workbenchWindow;
                                return;
                            }
                        }
                        window[0] = null;

                    }
                });
            }

            return window[0];
        }

    }

}
