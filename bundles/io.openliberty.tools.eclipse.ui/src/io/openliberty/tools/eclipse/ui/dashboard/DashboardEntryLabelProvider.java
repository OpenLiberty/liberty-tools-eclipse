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

    /** Size of the static state icons in logical pixels (active, stopped, stopping, incomplete). */
    private static final int STATE_ICON_SIZE = 16;

    /** Size of the progress frame icons in logical pixels. */
    private static final int PROGRESS_ICON_SIZE = 12;

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
         */
        @Override
        public String getText(Object element) {
            return "";
        }

        /**
         * {@inheritDoc}
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

        /** Running-state icon, padded to badge height. */
        private Image runningImg;

        /** Stopping-state icon, padded to badge height. */
        private Image stoppingImg;

        /** Incomplete-state icon, padded to badge height (some children running, some not). */
        private Image incompleteImg;

        /** Stopped-state icon, padded to badge height. */
        private Image stoppedImg;

        /** Progress indicator animator for the STARTING state. */
        private final ProgressIndicatorAnimator progressAnimator;

        /**
         * Constructor. Loads the state icons and initialises the (inactive) progress animator.
         *
         * @param dashboardView The dashboard view instance.
         */
        public StateNameColumnLabelProvider(DashboardView dashboardView) {
            String themeFolder = LibertyDevPlugin.isDarkTheme() ? "state/dark/" : "state/light/";
            runningImg = paddedStateIcon(themeFolder + "active");
            stoppedImg = paddedStateIcon(themeFolder + "stopped");
            stoppingImg = paddedStateIcon(themeFolder + "stopping");
            incompleteImg = paddedStateIcon(themeFolder + "incomplete");
            String progressFolder = themeFolder + "inProgress/";
            progressAnimator = new ProgressIndicatorAnimator(dashboardView, progressFolder, raw -> paddedStateIconFromImage(raw, PROGRESS_ICON_SIZE));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Image getImage(Object element) {
            if (!(element instanceof ProjectModel)) {
                return null;
            }
            ProjectModel project = (ProjectModel) element;
            AppState effective = resolveEffectiveState(project);
            if (effective == null) {
                progressAnimator.removeProject(project);
                return (incompleteImg != null) ? incompleteImg : stoppedImg;
            }
            switch (effective) {
                case STARTING:
                    progressAnimator.addProject(project);
                    Image frame = progressAnimator.getCurrentFrame();
                    return (frame != null) ? frame : stoppedImg;
                case APP_RUNNING:
                    progressAnimator.removeProject(project);
                    return runningImg;
                case SERVER_RUNNING:
                    progressAnimator.removeProject(project);
                    return (incompleteImg != null) ? incompleteImg : stoppedImg;
                case STOPPING:
                    progressAnimator.removeProject(project);
                    return (stoppingImg != null) ? stoppingImg : stoppedImg;
                case STOPPED:
                default:
                    progressAnimator.removeProject(project);
                    return stoppedImg;
            }
        }

        /**
         * {@inheritDoc}
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
            progressAnimator.dispose();
            disposeImage(runningImg);
            runningImg = null;
            disposeImage(stoppedImg);
            stoppedImg = null;
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
                case APP_RUNNING:
                    return Messages.getMessage("dashboard_tooltip_app_running");
                case SERVER_RUNNING:
                    return Messages.getMessage("dashboard_tooltip_server_running_not_app");
                case STARTING:
                    return Messages.getMessage("dashboard_tooltip_starting");
                case STOPPING:
                    return Messages.getMessage("dashboard_tooltip_stopping");
                default:
                    return Messages.getMessage("dashboard_tooltip_stopped");
            }
        }
        int appRunning = 0;
        int serverRunning = 0;
        int starting = 0;
        int stopping = 0;
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.APP_RUNNING) {
                appRunning++;
            }
            if (s == AppState.SERVER_RUNNING) {
                serverRunning++;
            }
            if (s == AppState.STARTING) {
                starting++;
            }
            if (s == AppState.STOPPING) {
                stopping++;
            }
        }
        if (appRunning == children.size()) {
            return Messages.getMessage("dashboard_tooltip_modules_running", appRunning, children.size());
        }
        if (starting > 0) {
            return Messages.getMessage("dashboard_tooltip_starting");
        }
        if (stopping > 0) {
            return Messages.getMessage("dashboard_tooltip_stopping");
        }
        if (appRunning == 0 && serverRunning == 0) {
            return Messages.getMessage("dashboard_tooltip_stopped");
        }

        return Messages.getMessage("dashboard_tooltip_modules_running", appRunning, children.size());
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
        int appRunning = 0;
        int serverRunning = 0;
        int starting = 0;
        int stopping = 0;
        int total = children.size();
        for (ProjectModel child : children) {
            AppState s = child.getAppState();
            if (s == AppState.APP_RUNNING) {
                appRunning++;
            } else if (s == AppState.SERVER_RUNNING) {
                serverRunning++;
            } else if (s == AppState.STARTING) {
                starting++;
            } else if (s == AppState.STOPPING) {
                stopping++;
            }
        }
        if (appRunning + serverRunning + starting + stopping == 0) {
            return AppState.STOPPED;
        }
        if (starting > 0) {
            return AppState.STARTING;
        }
        if (stopping > 0) {
            return AppState.STOPPING;
        }
        if (appRunning == total) {
            return AppState.APP_RUNNING;
        }
        // At least one child is active but not all applications are running — incomplete state.
        return null;
    }

    /**
     * Creates a padded image for a 16×16 state icon loaded by name, centering it in a
     * BADGE_W × BADGE_H transparent canvas so SWT never stretches it.
     *
     * @param baseName The icon base name without extension, relative to icons/.
     *
     * @return The padded image, or null if the icon could not be loaded.
     */
    private static Image paddedStateIcon(String baseName) {
        ImageDescriptor desc = LibertyDevPlugin.loadIconDescriptor(baseName);
        return (desc != null) ? paddedIconDescriptor(desc, STATE_ICON_SIZE).createImage() : null;
    }

    /**
     * Creates a padded image from an already-loaded raw Image, centering it in a
     * BADGE_W × BADGE_H transparent canvas so SWT never stretches it.
     *
     * @param raw      The raw icon image.
     * @param iconSize The true logical pixel size of the (square) icon.
     *
     * @return The padded image, or null if raw is null.
     */
    private static Image paddedStateIconFromImage(Image raw, int iconSize) {
        if (raw == null) {
            return null;
        }
        return paddedIconDescriptor(ImageDescriptor.createFromImage(raw), iconSize).createImage();
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
