/*******************************************************************************
 * Copyright (c) 2022, 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.dashboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.AppState;

/**
 * Label provider for entries in the Liberty Dashboard tree viewer.
 */
public class DashboardEntryLabelProvider extends CellLabelProvider {

    /** Cache key for a fully-running composite (used as base-identity * 10 + CACHE_RUNNING). */
    private static final int CACHE_RUNNING = 1;

    /** Cache key for a fully-stopped composite (used as base-identity * 10 + CACHE_STOPPED). */
    private static final int CACHE_STOPPED = 2;

    /** Cache key for the mixed-state composite (used as base-identity * 10 + CACHE_MIXED). */
    private static final int CACHE_MIXED   = 9;

    /** Top padding (px) added to badge composites so the artwork is not clipped at the top of the tree row. */
    private static final int BADGE_TOP_PAD = 4;

    /** Maven build-type badge image at native size (22x18 px). */
    private Image mavenImg;

    /** Gradle build-type badge image at native size (22x18 px). */
    private Image gradleImg;

    /**
     * Running-state dot centred in a 22x18 canvas — used directly for child rows
     * so they match the badge column width and are never stretched.
     */
    private Image runningImg;

    /**
     * Stopped-state dot centred in a 22x18 canvas — used directly for child rows
     * so they match the badge column width and are never stretched.
     */
    private Image stoppedImg;

    /**
     * Mixed-state dot centred in a 22x18 canvas — used directly for child rows
     * so they match the badge column width and are never stretched.
     */
    private Image mixedImg;

    /**
     * Running-state overlay dot for the badge corner on parent/standalone rows.
     * Kept as an independent Image instance from runningImg so each can be disposed separately.
     */
    private Image runningOverlay;

    /**
     * Stopped-state overlay dot for the badge corner on parent/standalone rows.
     * Kept as an independent Image instance from stoppedImg so each can be disposed separately.
     */
    private Image stoppedOverlay;

    /**
     * Mixed-state overlay dot for the badge corner on parent/standalone rows.
     * Kept as an independent Image instance from mixedImg so each can be disposed separately.
     */
    private Image mixedOverlay;

    /** Composite image cache keyed by (base-identity * 10 + stateOrdinal). */
    private final Map<Integer, Image> compositeCache = new HashMap<>();

    /** Previous spinner composite — disposed on the next tick to avoid leaks. */
    private Image lastSpinnerComposite;

    /** Spinner animator that drives the STARTING-state animation. */
    private final SpinnerAnimator spinnerAnimator;

    /** Dashboard view — used to obtain the Tree widget for hit-testing in tooltips. */
    private final DashboardView dashboardView;

    /**
     * Constructor.
     *
     * @param dashboardView The dashboard view instance.
     */
    public DashboardEntryLabelProvider(DashboardView dashboardView) {
        this.dashboardView  = dashboardView;

        // Load build-type badge images at their native size (22x18).
        mavenImg  = LibertyDevPlugin.loadIcon("mavenTag");
        gradleImg = LibertyDevPlugin.loadIcon("gradleTag");

        // The child-row canvas matches the padded composite height so every row is
        // the same height and the tree never stretches either image type.
        int canvasW = (mavenImg != null) ? mavenImg.getBounds().width  : 22;
        int canvasH = (mavenImg != null) ? mavenImg.getBounds().height : 18;
        int paddedH = canvasH + BADGE_TOP_PAD;

        // Load the child module state icons.
        Image rawRunning = LibertyDevPlugin.loadIcon("state/state_running_8");
        Image rawStopped = LibertyDevPlugin.loadIcon("state/state_stopped_8");
        Image rawMixed   = LibertyDevPlugin.loadIcon("state/state_mixed_8");

        // centered the child module state icons in a fixed (canvasW x paddedH) transparent
        // canvas. The canvas size matches the padded composite so row height is uniform.
        runningImg = centerInCanvas(rawRunning, canvasW, paddedH);
        stoppedImg = centerInCanvas(rawStopped, canvasW, paddedH);
        mixedImg   = centerInCanvas(rawMixed,   canvasW, paddedH);

        disposeImage(rawRunning);
        disposeImage(rawStopped);
        disposeImage(rawMixed);

        // Load a different referece of the state icons that overlay the build type badge. 
        runningOverlay = LibertyDevPlugin.loadIcon("state/state_running_8");
        stoppedOverlay = LibertyDevPlugin.loadIcon("state/state_stopped_8");
        mixedOverlay   = LibertyDevPlugin.loadIcon("state/state_mixed_8");

        // Spinner: child rows get the frame centred in the fixed canvas;
        // parent overlay uses the raw 8x8 frame so buildComposite positions it correctly.
        spinnerAnimator = new SpinnerAnimator(dashboardView,
                img -> centerInCanvas(img, canvasW, paddedH),
                img -> img);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(ViewerCell cell) {
        Object element = cell.getElement();
        if (!(element instanceof ProjectModel)) {
            return;
        }
        ProjectModel project = (ProjectModel) element;

        // Set image for a single tree row.
        if (project.getParentProjectModel() != null) {
            cell.setImage(stateImageForChild(project));
        } else {
            Image badgeImg = (project.getBuildType() == ProjectModel.BuildType.Gradle)
                    ? gradleImg : mavenImg;
            cell.setImage(badgeImg == null
                    ? stateImageForChild(project)
                    : imageForParent(badgeImg, project));
        }

        // Set text.
        cell.setText(project.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getToolTipText(Object element) {
        if (!(element instanceof ProjectModel)) {
            return null;
        }

        // Only show tooltip when hovering over the image, not the project name.
        // Ask the tree for the current mouse position and compare against the
        // image area of the hovered item.
        if (!isMouseOverImage()) {
            return null;
        }

        ProjectModel project = (ProjectModel) element;
        if (project.getParentProjectModel() != null) {
            return tooltipForState(project.getAppState());
        }
        return tooltipForParent(project);
    }

    /**
     * Returns true when the mouse cursor is within the image (icon) cell of the
     * currently hovered tree item, i.e. to the left of where the item text begins.
     */
    private boolean isMouseOverImage() {
        Display display = Display.getCurrent();
        if (display == null) {
            return false;
        }
        Point cursorInDisplay = display.getCursorLocation();
        Tree tree = dashboardView.getTree();
        if (tree == null || tree.isDisposed()) {
            return false;
        }
        // Convert display coordinates to tree-widget coordinates.
        Point cursorInTree = tree.toControl(cursorInDisplay);
        TreeItem item = tree.getItem(cursorInTree);
        if (item == null) {
            return false;
        }
        // getImageBounds(0) returns just the image slot bounds for column 0.
        Rectangle imageBounds = item.getImageBounds(0);
        return imageBounds.contains(cursorInTree);
    }

    /** 
     * Returns the full-size state dot for a child module row. 
     */
    private Image stateImageForChild(ProjectModel project) {
        AppState state = project.getAppState();
        if (state == AppState.STARTING) {
            spinnerAnimator.addProject(project);
            Image frame = spinnerAnimator.getCurrentFrame();
            return (frame != null) ? frame : stoppedImg;
        }
        spinnerAnimator.removeProject(project);
        switch (state) {
            case RUNNING: 
                return runningImg;
            case STOPPED:
            default:      
                return stoppedImg;
        }
    }

    /**
     * Returns the effective state for a parent or standalone project,
     * derived dynamically from its children. Returns null for the MIXED state
     * (some children running, some stopped).
     */
    private AppState resolveEffectiveState(ProjectModel project) {
        List<ProjectModel> children = project.getChildLibertyServerProjects();
        if (children == null || children.isEmpty()) {
            return project.getAppState();
        }
        int running  = 0;
        int starting = 0;
        int total    = children.size();
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.RUNNING)  { running++;  }
            if (s == AppState.STARTING) { starting++; }
        }
        if (running + starting == 0) { return AppState.STOPPED;  }
        if (starting > 0)            { return AppState.STARTING; }
        if (running == total)        { return AppState.RUNNING;  }
        return null; // Null signals the MIXED state (some running, some stopped).
    }

    /** 
     * Returns the composite badge and overlay image for a parent or standalone row. 
     */
    private Image imageForParent(Image badgeImg, ProjectModel project) {
        AppState effective = resolveEffectiveState(project);

        if (effective == AppState.STARTING) {
            spinnerAnimator.addProject(project);
            Image frame = spinnerAnimator.getCurrentFrameOverlay();
            Image overlayFrame = (frame != null) ? frame : stoppedOverlay;
            // Build a fresh composite each tick. Use a pixel-copy of the badge
            // as the base so disposing this composite never touches badgeImg.
            Image composite = buildComposite(badgeImg, overlayFrame);
            if (lastSpinnerComposite != null && !lastSpinnerComposite.isDisposed()) {
                lastSpinnerComposite.dispose();
            }
            lastSpinnerComposite = composite;
            return composite;
        }

        spinnerAnimator.removeProject(project);

        if (effective == null) {
            // MIXED state.
            if (mixedOverlay == null) return badgeImg;
            return compositeImage(badgeImg, mixedOverlay,
                    System.identityHashCode(badgeImg) * 10 + CACHE_MIXED);
        }
        switch (effective) {
            case RUNNING:
                if (runningOverlay == null) return badgeImg;
                return compositeImage(badgeImg, runningOverlay,
                        System.identityHashCode(badgeImg) * 10 + CACHE_RUNNING);
            case STOPPED:
            default:
                if (stoppedOverlay == null) return badgeImg;
                return compositeImage(badgeImg, stoppedOverlay,
                        System.identityHashCode(badgeImg) * 10 + CACHE_STOPPED);
        }
    }

    /**
     * Returns a cached badge and overlay composite, creating it on first access.
     * The composite is built from a pixel-copy of base so that disposing the
     * cached composite never affects the original badge image.
     */
    private Image compositeImage(Image base, Image overlay, int cacheKey) {
        return compositeCache.computeIfAbsent(cacheKey, k -> buildComposite(base, overlay));
    }

    /**
     * Builds a composite image by drawing base then stamping overlay onto its
     * bottom-right corner, using a GC so the result is rendered at the display's
     * full physical pixel density (sharp on HiDPI/Retina displays).
     * Must be called on the SWT UI thread.
     */
    private static Image buildComposite(Image base, Image overlay) {
        if (base == null) {
            return null;
        }
        Rectangle b = base.getBounds();
        // Add top padding so the badge artwork is not clipped at the top of the tree row.
        Image result = newTransparentImage(base.getDevice(), b.width, b.height + BADGE_TOP_PAD);
        GC gc = new GC(result);
        try {
            gc.setAntialias(SWT.ON);
            gc.drawImage(base, 0, BADGE_TOP_PAD);
            if (overlay != null) {
                Rectangle ob = overlay.getBounds();
                gc.drawImage(overlay, b.width - ob.width, b.height + BADGE_TOP_PAD - ob.height);
            }
        } finally {
            gc.dispose();
        }
        return result;
    }

    /**
     * Returns a new canvasW x canvasH transparent image with src drawn centred,
     * using a GC so the result is at full physical pixel density.
     * Must be called on the SWT UI thread.
     */
    private static Image centerInCanvas(Image src, int canvasW, int canvasH) {
        if (src == null || src.isDisposed()) {
            return null;
        }
        Image result = newTransparentImage(src.getDevice(), canvasW, canvasH);
        GC gc = new GC(result);
        try {
            gc.setAntialias(SWT.ON);
            Rectangle sb = src.getBounds();
            gc.drawImage(src, (canvasW - sb.width) / 2, (canvasH - sb.height) / 2);
        } finally {
            gc.dispose();
        }
        return result;
    }

    /**
     * Creates a fully-transparent 32-bit RGBA image of the given size.
     * Used as the canvas for all GC-based compositing so the result has a
     * transparent (not white) background.
     */
    private static Image newTransparentImage(Device device, int w, int h) {
        ImageData data = new ImageData(w, h, 32, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        data.alphaData = new byte[w * h]; // All zeros means fully transparent.
        return new Image(device, data);
    }

    /** 
     * Returns the tooltip string for a leaf module's state. 
     * 
     * @return The tooltip string for a leaf module's state. 
     */
    private static String tooltipForState(AppState state) {
        if (state == null) return null;
        switch (state) {
            case RUNNING:  return "Running";
            case STARTING: return "Starting...";
            case STOPPED:
            default:       return "Stopped";
        }
    }

    /** 
     * Returns the tooltip string for a parent or standalone row's aggregate state.
     * 
     * @param project The parent project.
     * 
     * @return the tooltip string for a parent or standalone row's aggregate state.
     */
    private String tooltipForParent(ProjectModel project) {
        // A standalone project receives the same tooltip as a leaf module.
        List<ProjectModel> children = project.getChildLibertyServerProjects();
        if (children == null || children.isEmpty()) {
            return tooltipForState(project.getAppState());
        }

        AppState effective = resolveEffectiveState(project);
        if (effective != null) {
            return tooltipForState(effective);
        }

        // MIXED state — count how many children are running.
        int running = 0;
        for (ProjectModel child : children) {
            if (child.getAppState() == AppState.RUNNING) {
                running++;
            }
        }
        return running + "/" + children.size() + " running";
    }

    /**
     * Disposes all images owned by this label provider and stops the spinner animator.
     */
    @Override
    public void dispose() {
        spinnerAnimator.dispose();

        disposeImage(mavenImg);  mavenImg  = null;
        disposeImage(gradleImg); gradleImg = null;

        // Dispose the overlay copies first — they are separate objects from the full-size originals below.
        disposeImage(runningOverlay); runningOverlay = null;
        disposeImage(stoppedOverlay); stoppedOverlay = null;
        disposeImage(mixedOverlay);   mixedOverlay   = null;

        // Dispose the full-size state dot originals.
        disposeImage(runningImg); runningImg = null;
        disposeImage(stoppedImg); stoppedImg = null;
        disposeImage(mixedImg);   mixedImg   = null;

        compositeCache.values().forEach(DashboardEntryLabelProvider::disposeImage);
        compositeCache.clear();

        disposeImage(lastSpinnerComposite);
        lastSpinnerComposite = null;

        super.dispose();
    }

    /**
     * Disposes the input image.
     * 
     * @param img The image to dispose.
     */
    private static void disposeImage(Image img) {
        if (img != null && !img.isDisposed()) {
            img.dispose();
        }
    }
}
