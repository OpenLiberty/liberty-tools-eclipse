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

import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.AppState;

/**
 * Label provider for entries in the Liberty Dashboard tree viewer.
 *
 * Display rules:
 * - Child modules show only the state icon centered in the badge-column width.
 * - Standalone projects and multi-module parents show the build-type badge with
 * the state dot overlaid on its bottom-right corner via DecorationOverlayIcon.
 * - Every row carries a tooltip describing its current state.
 */
public class DashboardEntryLabelProvider extends CellLabelProvider {

    /** Field hash key for a fully-running composite. */
    private static final int FIELD_HASH_RUNNING = 1;

    /** Field hash key for a fully-stopped composite. */
    private static final int FIELD_HASH_STOPPED = 2;

    /** Field hash key for the mixed-state composite. */
    private static final int FIELD_HASH_MIXED = 9;

    /**
     * Top padding added to every composite canvas so the badge artwork is not
     * clipped at the top of the tree row.
     */
    private static final int BADGE_TOP_PAD = 4;

    /**
     * Design size in logical pixels of the state dot icons (running, stopped,
     * mixed, spinner frames). Passed to DecorationOverlayIcon so the overlay
     * is rendered at this size regardless of the source image's intrinsic size.
     */
    private static final int STATE_DOT_SIZE = 8;

    /** Image representing a Maven project badge. */
    private Image mavenImg;

    /** Image representing a Gradle project badge. */
    private Image gradleImg;

    /** ImageDescriptor for the Maven badge, used for overlay. */
    private ImageDescriptor mavenDesc;

    /** ImageDescriptor for the Gradle badge, used for overlay. */
    private ImageDescriptor gradleDesc;

    /**
     * Running-state dot centered in a badge-sized canvas for child-module rows.
     * Never used as a badge overlay; the raw overlay descriptor is used for that.
     */
    private Image runningImg;

    /**
     * Stopped-state dot centered in a badge-sized canvas for child-module rows.
     * Never used as a badge overlay; the raw overlay descriptor is used for that.
     */
    private Image stoppedImg;

    /**
     * Mixed-state dot centered in a badge-sized canvas for child-module rows.
     * Never used as a badge overlay; the raw overlay descriptor is used for that.
     */
    private Image mixedImg;

    /** Running-state dot descriptor used as the bottom-right overlay on parent rows. */
    private ImageDescriptor runningOverlayDesc;

    /** Stopped-state dot descriptor used as the bottom-right overlay on parent rows. */
    private ImageDescriptor stoppedOverlayDesc;

    /** Mixed-state dot descriptor used as the bottom-right overlay on parent rows. */
    private ImageDescriptor mixedOverlayDesc;

    /**
     * Composite image cache keyed by (base-identity * 10 + stateOrdinal).
     * Spinner-frame composites are never stored here because the frame changes
     * every tick.
     */
    private final Map<Integer, Image> compositeCache = new HashMap<>();

    /** Previous spinner composite, disposed on the next tick to avoid leaks. */
    private Image lastSpinnerComposite;

    /** Spinner animator that drives the STARTING-state animation. */
    private final SpinnerAnimator spinnerAnimator;

    /** Dashboard view, used to obtain the Tree widget for tooltip hit-testing. */
    private final DashboardView dashboardView;

    /**
     * Constructor.
     *
     * @param dashboardView The dashboard view instance.
     */
    public DashboardEntryLabelProvider(DashboardView dashboardView) {
        this.dashboardView = dashboardView;

        mavenDesc = LibertyDevPlugin.loadIconDescriptor("mavenTag");
        gradleDesc = LibertyDevPlugin.loadIconDescriptor("gradleTag");
        mavenImg = (mavenDesc != null) ? mavenDesc.createImage() : null;
        gradleImg = (gradleDesc != null) ? gradleDesc.createImage() : null;

        // Canvas size for child-row state icons: match the badge dimensions so
        // every row is the same height and the tree never stretches any icon.
        int canvasW = (mavenImg != null) ? mavenImg.getBounds().width : 22;
        int canvasH = (mavenImg != null) ? mavenImg.getBounds().height : 18;
        int paddedH = canvasH + BADGE_TOP_PAD;

        // Load state dot descriptors.
        ImageDescriptor rawRunningDesc = LibertyDevPlugin.loadIconDescriptor("state/state_running_8");
        ImageDescriptor rawStoppedDesc = LibertyDevPlugin.loadIconDescriptor("state/state_stopped_8");
        ImageDescriptor rawMixedDesc = LibertyDevPlugin.loadIconDescriptor("state/state_mixed_8");

        // Child-row versions: centered in the padded canvas via CompositeImageDescriptor.
        runningImg = centeredDotImage(rawRunningDesc, canvasW, paddedH);
        stoppedImg = centeredDotImage(rawStoppedDesc, canvasW, paddedH);
        mixedImg = centeredDotImage(rawMixedDesc, canvasW, paddedH);

        // Overlay versions: kept as descriptors for DecorationOverlayIcon.
        runningOverlayDesc = rawRunningDesc;
        stoppedOverlayDesc = rawStoppedDesc;
        mixedOverlayDesc = rawMixedDesc;

        // Spinner child-row frames: centered in the padded canvas via CompositeImageDescriptor.
        // Spinner parent-row overlay frames: kept as-is for use in buildComposite.
        spinnerAnimator = new SpinnerAnimator(dashboardView, raw -> centeredDotImage(ImageDescriptor.createFromImage(raw), canvasW, paddedH), raw -> raw);
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
            Image badgeImg = (project.getBuildType() == ProjectModel.BuildType.Gradle) ? gradleImg : mavenImg;
            ImageDescriptor badgeDesc = (project.getBuildType() == ProjectModel.BuildType.Gradle) ? gradleDesc : mavenDesc;
            cell.setImage(badgeImg == null ? stateImageForChild(project) : imageForParent(badgeImg, badgeDesc, project));
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
     * Returns true when the mouse cursor is within the image cell of the currently
     * hovered tree item, i.e. to the left of where the item text begins.
     *
     * @return True if the cursor is over the image area.
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
        Point cursorInTree = tree.toControl(cursorInDisplay);
        TreeItem item = tree.getItem(cursorInTree);
        if (item == null) {
            return false;
        }
        Rectangle imageBounds = item.getImageBounds(0);
        return imageBounds.contains(cursorInTree);
    }

    /**
     * Returns the state icon for a child module row.
     *
     * @param project The child project.
     *
     * @return The state icon image.
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
     * Returns the effective state for a parent or standalone project.
     * Returns null for the MIXED state (some running, some stopped).
     *
     * @param project The parent or standalone project.
     *
     * @return The effective AppState, or null for the mixed state.
     */
    private AppState resolveEffectiveState(ProjectModel project) {
        List<ProjectModel> children = project.getChildLibertyServerProjects();
        if (children == null || children.isEmpty()) {
            return project.getAppState();
        }
        int running = 0;
        int starting = 0;
        int total = children.size();
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.RUNNING) {
                running++;
            }
            if (s == AppState.STARTING) {
                starting++;
            }
        }
        if (running + starting == 0) {
            return AppState.STOPPED;
        }
        if (starting > 0) {
            return AppState.STARTING;
        }
        if (running == total) {
            return AppState.RUNNING;
        }
        return null; // Null signals the MIXED state.
    }

    /**
     * Returns the composite badge and overlay image for a parent or standalone row.
     *
     * @param badgeImg  The build-type badge image.
     * @param badgeDesc The build-type badge descriptor.
     * @param project   The project whose state drives the overlay.
     *
     * @return The composite image.
     */
    private Image imageForParent(Image badgeImg, ImageDescriptor badgeDesc, ProjectModel project) {
        AppState effective = resolveEffectiveState(project);

        if (effective == AppState.STARTING) {
            spinnerAnimator.addProject(project);
            Image frame = spinnerAnimator.getCurrentFrameOverlay();
            Image overlayFrame = (frame != null) ? frame : stoppedImg;
            // Build a fresh composite each tick — spinner frames change every tick.
            Image composite = buildComposite(badgeDesc, badgeImg,
                                             ImageDescriptor.createFromImage(overlayFrame));
            if (lastSpinnerComposite != null && !lastSpinnerComposite.isDisposed()) {
                lastSpinnerComposite.dispose();
            }
            lastSpinnerComposite = composite;
            return composite;
        }

        spinnerAnimator.removeProject(project);

        if (effective == null) {
            // MIXED state.
            if (mixedOverlayDesc == null) {
                return badgeImg;
            }
            return compositeImage(badgeImg, badgeDesc, mixedOverlayDesc,
                                  System.identityHashCode(badgeImg) * 31 + FIELD_HASH_MIXED);
        }
        switch (effective) {
            case RUNNING:
                if (runningOverlayDesc == null) {
                    return badgeImg;
                }
                return compositeImage(badgeImg, badgeDesc, runningOverlayDesc,
                                      System.identityHashCode(badgeImg) * 31 + FIELD_HASH_RUNNING);
            case STOPPED:
            default:
                if (stoppedOverlayDesc == null) {
                    return badgeImg;
                }
                return compositeImage(badgeImg, badgeDesc, stoppedOverlayDesc,
                                      System.identityHashCode(badgeImg) * 31 + FIELD_HASH_STOPPED);
        }
    }

    /**
     * Returns a cached composite, creating it on first access.
     *
     * @param badgeImg    The build-type badge image.
     * @param badgeDesc   The build-type badge descriptor.
     * @param overlayDesc The state dot descriptor for the bottom-right overlay.
     * @param cacheKey    The cache key.
     *
     * @return The cached composite image.
     */
    private Image compositeImage(Image badgeImg, ImageDescriptor badgeDesc,
                                 ImageDescriptor overlayDesc, int cacheKey) {
        return compositeCache.computeIfAbsent(cacheKey,
                                              k -> buildComposite(badgeDesc, badgeImg, overlayDesc));
    }

    /**
     * Builds a composite image using DecorationOverlayIcon.
     *
     * The badge is used as the base image descriptor. The state dot descriptor is
     * placed at the BOTTOM_RIGHT quadrant. DecorationOverlayIcon handles
     * transparency and DPI scaling correctly on all platforms without any manual
     * pixel manipulation.
     *
     * A top-padding row equal to BADGE_TOP_PAD is added to the base so the badge
     * artwork is not clipped at the top of the tree row.
     *
     * @param badgeDesc   The build-type badge descriptor.
     * @param badgeImg    The build-type badge image (used to read logical bounds).
     * @param overlayDesc The state dot descriptor for the bottom-right corner.
     *
     * @return The composite image, or null if badgeDesc is null.
     */
    private static Image buildComposite(ImageDescriptor badgeDesc, Image badgeImg,
                                        ImageDescriptor overlayDesc) {
        if (badgeDesc == null) {
            return null;
        }
        Rectangle b = badgeImg.getBounds();
        int paddedW = b.width;
        int paddedH = b.height + BADGE_TOP_PAD;
        Point canvasSize = new Point(paddedW, paddedH);

        // Wrap the badge in a padded CompositeImageDescriptor so the badge is
        // drawn at y=BADGE_TOP_PAD, giving the tree row enough vertical room.
        ImageDescriptor paddedBadge = new PaddedImageDescriptor(badgeDesc, canvasSize, BADGE_TOP_PAD);

        // DecorationOverlayIcon overlays the dot at the bottom-right quadrant.
        // It uses JFace's built-in transparent composite — no pixel manipulation.
        // The base image is created temporarily and disposed after the composite is built.
        ImageDescriptor[] overlays = new ImageDescriptor[5];
        overlays[IDecoration.BOTTOM_RIGHT] = new SizedImageDescriptor(overlayDesc, STATE_DOT_SIZE);
        Image baseImg = paddedBadge.createImage();
        try {
            DecorationOverlayIcon icon = new DecorationOverlayIcon(baseImg, overlays, canvasSize);
            return icon.createImage();
        } finally {
            baseImg.dispose();
        }
    }

    /**
     * Creates an Image with the given dot descriptor centered in a canvas of the
     * given logical size, using a CompositeImageDescriptor.
     *
     * This is used for child-module rows where only the state dot is shown,
     * centered in the same column width as the badge so all rows have uniform height.
     *
     * @param dotDesc The state dot descriptor.
     * @param canvasW The logical canvas width in pixels.
     * @param canvasH The logical canvas height in pixels.
     *
     * @return The new Image, or null if dotDesc is null.
     */
    private static Image centeredDotImage(ImageDescriptor dotDesc, int canvasW, int canvasH) {
        if (dotDesc == null) {
            return null;
        }
        Point canvasSize = new Point(canvasW, canvasH);
        int xOff = (canvasW - STATE_DOT_SIZE) / 2;
        int yOff = (canvasH - STATE_DOT_SIZE) / 2;
        ImageDescriptor centered = new OffsetImageDescriptor(dotDesc, canvasSize, xOff, yOff);
        return centered.createImage();
    }

    /**
     * Returns the tooltip string for a leaf state.
     *
     * @param state The AppState value.
     *
     * @return The tooltip string.
     */
    private static String tooltipForState(AppState state) {
        if (state == null) {
            return null;
        }
        switch (state) {
            case RUNNING:
                return Messages.getMessage("dashboard_tooltip_running");
            case STARTING:
                return Messages.getMessage("dashboard_tooltip_starting");
            default:
                return Messages.getMessage("dashboard_tooltip_stopped");
        }
    }

    /**
     * Returns the tooltip string for a parent or standalone row.
     *
     * @param project The parent or standalone project.
     *
     * @return The tooltip string.
     */
    private String tooltipForParent(ProjectModel project) {
        List<ProjectModel> children = project.getChildLibertyServerProjects();
        if (children.isEmpty()) {
            return tooltipForState(project.getAppState());
        }
        int running = 0;
        int starting = 0;
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.RUNNING)
                running++;
            if (s == AppState.STARTING)
                starting++;
        }
        if (running == children.size()) {
            return Messages.getMessage("dashboard_tooltip_running");
        }
        if (starting > 0) {
            return Messages.getMessage("dashboard_tooltip_starting");
        }
        if (running == 0) {
            return Messages.getMessage("dashboard_tooltip_stopped");
        }
        return Messages.getMessage("dashboard_tooltip_partial_running", running, children.size());
    }

    /**
     * Disposes all images owned by this label provider and stops the spinner animator.
     */
    @Override
    public void dispose() {
        spinnerAnimator.dispose();

        disposeImage(mavenImg);
        mavenImg = null;
        disposeImage(gradleImg);
        gradleImg = null;

        disposeImage(runningImg);
        runningImg = null;
        disposeImage(stoppedImg);
        stoppedImg = null;
        disposeImage(mixedImg);
        mixedImg = null;

        compositeCache.values().forEach(DashboardEntryLabelProvider::disposeImage);
        compositeCache.clear();

        disposeImage(lastSpinnerComposite);
        lastSpinnerComposite = null;

        super.dispose();
    }

    /**
     * Disposes the given image if it is not null and not already disposed.
     *
     * @param img The image to dispose.
     */
    private static void disposeImage(Image img) {
        if (img != null && !img.isDisposed()) {
            img.dispose();
        }
    }

    /**
     * A CompositeImageDescriptor that draws a base image at a fixed y-offset,
     * producing a taller canvas with padding above the base artwork.
     *
     * Used to add BADGE_TOP_PAD pixels of transparent space above the badge so
     * the badge is not clipped at the top of the tree row.
     */
    private static final class PaddedImageDescriptor extends CompositeImageDescriptor {

        /** The base image descriptor to draw with top padding. */
        private final ImageDescriptor base;

        /** The total canvas size (width x padded height). */
        private final Point size;

        /** The number of pixels of padding to add above the base image. */
        private final int topPad;

        /**
         * Constructor.
         *
         * @param base   The base image descriptor.
         * @param size   The total canvas size.
         * @param topPad The top padding in pixels.
         */
        PaddedImageDescriptor(ImageDescriptor base, Point size, int topPad) {
            this.base = base;
            this.size = size;
            this.topPad = topPad;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void drawCompositeImage(int width, int height) {
            ImageDataProvider provider = zoom -> {
                ImageData data = base.getImageData(zoom);
                return data;
            };
            drawImage(provider, 0, topPad);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Point getSize() {
            return size;
        }
    }

    /**
     * A CompositeImageDescriptor that draws a source image at a fixed (x, y)
     * offset within a larger transparent canvas.
     *
     * Used to center state dot icons in the badge-column canvas for child rows.
     */
    private static final class OffsetImageDescriptor extends CompositeImageDescriptor {

        /** The source image descriptor to center in the canvas. */
        private final ImageDescriptor source;

        /** The total canvas size. */
        private final Point size;

        /** The x offset at which to draw the source image. */
        private final int xOff;

        /** The y offset at which to draw the source image. */
        private final int yOff;

        /**
         * Constructor.
         *
         * @param source The source image descriptor.
         * @param size   The total canvas size.
         * @param xOff   The x offset in pixels.
         * @param yOff   The y offset in pixels.
         */
        OffsetImageDescriptor(ImageDescriptor source, Point size, int xOff, int yOff) {
            this.source = source;
            this.size = size;
            this.xOff = xOff;
            this.yOff = yOff;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void drawCompositeImage(int width, int height) {
            ImageDataProvider provider = zoom -> {
                ImageData data = source.getImageData(zoom);
                return data;
            };
            drawImage(provider, xOff, yOff);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Point getSize() {
            return size;
        }
    }

    /**
     * An ImageDescriptor wrapper that constrains the image to a fixed logical
     * size so that DecorationOverlayIcon renders the overlay dot at exactly
     * STATE_DOT_SIZE x STATE_DOT_SIZE regardless of the source's intrinsic size.
     */
    private static final class SizedImageDescriptor extends ImageDescriptor {

        /** The wrapped source descriptor. */
        private final ImageDescriptor source;

        /** The fixed logical size in pixels. */
        private final int size;

        /**
         * Constructor.
         *
         * @param source The source descriptor.
         * @param size   The fixed logical size in pixels (applied to both width and height).
         */
        SizedImageDescriptor(ImageDescriptor source, int size) {
            this.source = source;
            this.size = size;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ImageData getImageData(int zoom) {
            ImageData data = source.getImageData(zoom);
            if (data == null) {
                return null;
            }
            // Scale the source data to the target size at this zoom level.
            int scaledSize = (size * zoom + 50) / 100;
            return data.scaledTo(scaledSize, scaledSize);
        }
    }
}
