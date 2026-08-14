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

import java.util.List;

import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.Point;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.AppState;

/**
 * Label providers for the two columns of the Liberty Dashboard tree viewer.
 *
 * Column 0: Has the badge project type column
 * Column 1: Has the state icon and the project name.
 */
public class DashboardEntryLabelProvider {

    /** Width of the badge images in logical pixels. */
    static final int BADGE_W = 22;

    /** Height of the badge images in logical pixels. */
    static final int BADGE_H = 18;

    /** Size of the state SVG icons in logical pixels (all state icons are 16×16). */
    private static final int STATE_ICON_SIZE = 16;

    /**
     * Label provider for column 0 — the badge column.
     *
     * Returns the build-type badge for root and standalone rows.
     * Returns null for child module rows so the cell is empty.
     */
    public static final class BadgeColumnLabelProvider extends ColumnLabelProvider {

        /** Maven badge image. */
        private Image mavenImg;

        /** Gradle badge image. */
        private Image gradleImg;

        /**
         * Constructor. Loads the badge images.
         */
        public BadgeColumnLabelProvider() {
            mavenImg = loadImage("mavenTag");
            gradleImg = loadImage("gradleTag");
        }

        /**
         * {@inheritDoc}
         *
         * Returns the build-type badge for parent rows, null for child rows.
         */
        @Override
        public Image getImage(Object element) {
            if (!(element instanceof ProjectModel)) {
                return null;
            }
            ProjectModel project = (ProjectModel) element;
            if (project.getParentProjectModel() != null) {
                return null;
            }
            return (project.getBuildType() == ProjectModel.BuildType.Gradle) ? gradleImg : mavenImg;
        }

        /**
         * {@inheritDoc}
         *
         * Returns an empty string so the column shows only the icon.
         */
        @Override
        public String getText(Object element) {
            return "";
        }

        /**
         * {@inheritDoc}
         *
         * Returns null to suppress the tooltip for the badge column.
         * Without this override, ColumnLabelProvider falls back to getText() which
         * returns "" and causes ColumnViewerToolTipSupport to render an empty tooltip shell.
         */
        @Override
        public String getToolTipText(Object element) {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void dispose() {
            disposeImage(mavenImg);
            mavenImg = null;
            disposeImage(gradleImg);
            gradleImg = null;
            super.dispose();
        }
    }

    /**
     * Label provider for column 1 containing the state icon and the project name.
     */
    public static final class StateNameColumnLabelProvider extends ColumnLabelProvider {

        /** Starting-state icon, padded to badge height. */
        private Image startingImg;

        /** Running-state icon, padded to badge height. */
        private Image runningImg;

        /** Stopping-state icon, padded to badge height. */
        private Image stoppingImg;

        /** Incomplete-state icon, padded to badge height (some children running, some not). */
        private Image incompleteImg;

        /** Stopped-state icon, padded to badge height. */
        private Image stoppedImg;

        /** Image animator for low definition displays and IDEs that do not support SVG images. */
        private final SpinnerAnimator spinnerAnimator;

        /**
         * Constructor. Loads the state icons and initialises the (inactive) spinner animator.
         *
         * @param dashboardView The dashboard view instance.
         */
        public StateNameColumnLabelProvider(DashboardView dashboardView) {
            String themeFolder = LibertyDevPlugin.isDarkTheme() ? "state/dark/" : "state/light/";
            runningImg = paddedStateIcon(themeFolder + "running");
            stoppedImg = paddedStateIcon(themeFolder + "stopped");
            startingImg = paddedStateIcon(themeFolder + "starting");
            stoppingImg = paddedStateIcon(themeFolder + "stopping");
            incompleteImg = paddedStateIcon(themeFolder + "incomplete");
            spinnerAnimator = new SpinnerAnimator(dashboardView, raw -> paddedDotFromImage(raw));
        }

        /**
         * {@inheritDoc}
         *
         * Returns the state icon for the project's current app state.
         */
        @Override
        public Image getImage(Object element) {
            if (!(element instanceof ProjectModel)) {
                return null;
            }
            ProjectModel project = (ProjectModel) element;
            AppState effective = resolveEffectiveState(project);
            if (effective == null) {
                return (incompleteImg != null) ? incompleteImg : stoppedImg;
            }
            switch (effective) {
                case STARTING:
                    return (startingImg != null) ? startingImg : stoppedImg;
                case RUNNING:
                    return runningImg;
                case STOPPING:
                    return (stoppingImg != null) ? stoppingImg : stoppedImg;
                case STOPPED:
                default:
                    return stoppedImg;
            }
        }

        /**
         * {@inheritDoc}
         *
         * Returns the project name.
         */
        @Override
        public String getText(Object element) {
            if (!(element instanceof ProjectModel)) {
                return "";
            }
            return ((ProjectModel) element).getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void dispose() {
            spinnerAnimator.dispose();
            disposeImage(runningImg);
            runningImg = null;
            disposeImage(stoppedImg);
            stoppedImg = null;
            disposeImage(startingImg);
            startingImg = null;
            disposeImage(stoppingImg);
            stoppingImg = null;
            disposeImage(incompleteImg);
            incompleteImg = null;
            super.dispose();
        }

    }

    /**
     * Returns the state tooltip text for the given project, used when hovering over
     * the state icon in the dashboard. Returns null if no tooltip should be shown.
     *
     * @param project The project to describe.
     *
     * @return The state tooltip string, or null.
     */
    protected static String stateTooltipText(ProjectModel project) {
        List<ProjectModel> children = project.getChildLibertyServerProjects();
        if (children.isEmpty()) {
            AppState state = project.getAppState();
            if (state == null) {
                return null;
            }
            switch (state) {
                case RUNNING:
                    return Messages.getMessage("dashboard_tooltip_running");
                case STARTING:
                    return Messages.getMessage("dashboard_tooltip_starting");
                case STOPPING:
                    return Messages.getMessage("dashboard_tooltip_stopping");
                default:
                    return Messages.getMessage("dashboard_tooltip_stopped");
            }
        }
        int running = 0;
        int starting = 0;
        int stopping = 0;
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.RUNNING) {
                running++;
            }
            if (s == AppState.STARTING) {
                starting++;
            }
            if (s == AppState.STOPPING) {
                stopping++;
            }
        }
        if (running == children.size()) {
            return Messages.getMessage("dashboard_tooltip_modules_running", running, children.size());
        }
        if (starting > 0) {
            return Messages.getMessage("dashboard_tooltip_starting");
        }
        if (stopping > 0) {
            return Messages.getMessage("dashboard_tooltip_stopping");
        }
        if (running == 0) {
            return Messages.getMessage("dashboard_tooltip_stopped");
        }

        return Messages.getMessage("dashboard_tooltip_modules_running", running, children.size());
    }

    /**
     * Returns the effective state for any project.
     * Returns null to signal the mixed state (some children running, some not).
     *
     * @param project The project to evaluate.
     *
     * @return The effective AppState, or null for the mixed state.
     */
    private static AppState resolveEffectiveState(ProjectModel project) {
        List<ProjectModel> children = project.getChildLibertyServerProjects();
        if (children == null || children.isEmpty()) {
            return project.getAppState();
        }
        int running = 0;
        int starting = 0;
        int stopping = 0;
        int total = children.size();
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.RUNNING) {
                running++;
            } else if (s == AppState.STARTING) {
                starting++;
            } else if (s == AppState.STOPPING) {
                stopping++;
            }
        }
        if (running + starting + stopping == 0) {
            return AppState.STOPPED;
        }
        if (starting > 0) {
            return AppState.STARTING;
        }
        if (stopping > 0) {
            return AppState.STOPPING;
        }
        if (running == total) {
            return AppState.RUNNING;
        }
        // Some children are running, some stopped — mixed/incomplete state.
        return null;
    }

    /**
     * Creates a padded image for a 16×16 state SVG icon, centering it in a
     * BADGE_W × BADGE_H transparent canvas so SWT never stretches it.
     *
     * @param baseName The icon base name without extension, relative to {@code icons/}.
     *
     * @return The padded image, or null if the icon could not be loaded.
     */
    private static Image paddedStateIcon(String baseName) {
        ImageDescriptor desc = LibertyDevPlugin.loadIconDescriptor(baseName);
        return (desc != null) ? paddedIconDescriptor(desc, STATE_ICON_SIZE).createImage() : null;
    }

    /**
     * Creates a padded dot image from the given icon base name.
     * The dot is centered in a BADGE_W x BADGE_H transparent canvas so that
     * SWT receives an image at the same size as the badge and never stretches it.
     *
     * @param baseName The icon base name without extension.
     *
     * @return The padded image, or null if the icon could not be loaded.
     */
    protected static Image paddedDot(String baseName) {
        ImageDescriptor desc = LibertyDevPlugin.loadIconDescriptor(baseName);
        return (desc != null) ? paddedIconDescriptor(desc, 8).createImage() : null;
    }

    /**
     * Creates a padded dot image from an already-loaded raw Image.
     * The dot is centered in a BADGE_W x BADGE_H transparent canvas.
     *
     * @param raw The raw dot image.
     *
     * @return The padded image, or null if raw is null.
     */
    private static Image paddedDotFromImage(Image raw) {
        if (raw == null) {
            return null;
        }
        return paddedIconDescriptor(ImageDescriptor.createFromImage(raw), 8).createImage();
    }

    /**
     * Returns a CompositeImageDescriptor that draws the given icon descriptor
     * centered horizontally and vertically in a BADGE_W × BADGE_H canvas.
     *
     * @param iconDesc The icon image descriptor.
     * @param iconSize The logical pixel size of the (square) icon.
     *
     * @return The padded composite descriptor.
     */
    private static ImageDescriptor paddedIconDescriptor(ImageDescriptor iconDesc, int iconSize) {
        int xOff = (BADGE_W - iconSize) / 2;
        int yOff = (BADGE_H - iconSize) / 2;
        Point canvasSize = new Point(BADGE_W, BADGE_H);
        return new CompositeImageDescriptor() {
            @Override
            protected void drawCompositeImage(int width, int height) {
                ImageDataProvider provider = zoom -> iconDesc.getImageData(zoom);
                drawImage(provider, xOff, yOff);
            }

            @Override
            protected Point getSize() {
                return canvasSize;
            }
        };
    }

    /**
     * Loads an image from the plugin icon directory.
     *
     * @param baseName The icon base name without extension.
     *
     * @return The loaded Image, or null if the icon could not be found.
     */
    private static Image loadImage(String baseName) {
        ImageDescriptor desc = LibertyDevPlugin.loadIconDescriptor(baseName);
        return (desc != null) ? desc.createImage() : null;
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
}
