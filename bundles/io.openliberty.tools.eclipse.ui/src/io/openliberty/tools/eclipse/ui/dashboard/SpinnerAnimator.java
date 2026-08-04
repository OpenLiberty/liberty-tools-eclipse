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
package io.openliberty.tools.eclipse.ui.dashboard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.model.ProjectModel;

/**
 * Drives the STARTING in-progress spinner animation for the Liberty Dashboard.
 *
 * Loads FRAME_COUNT spinner frame images, applies a caller-supplied frame
 * transform (e.g. centre-in-canvas), and advances them at ~10 fps via a
 * Display.timerExec loop, triggering a label refresh for each project currently
 * in the STARTING state.
 */
class SpinnerAnimator {

    /** Number of frames in the spinner animation. */
    static final int FRAME_COUNT = 12;

    /** Delay between frames in milliseconds (~10 fps). */
    private static final int FRAME_DELAY_MS = 100;

    /** Spinner frames transformed for child-row display (centred in canvas). */
    private final Image[] frames;

    /** Spinner frames transformed for parent-row overlay (raw 8x8). */
    private final Image[] framesOverlay;

    /** Index of the current animation frame. */
    private volatile int currentFrame = 0;

    /** Projects currently in the STARTING state; drives when the timer loop runs. */
    private final Set<ProjectModel> startingProjects = ConcurrentHashMap.newKeySet();

    /** Whether the timer loop is currently scheduled. */
    private volatile boolean running = false;

    /** The dashboard view used to trigger label refreshes. */
    private final DashboardView dashboardView;

    /**
     * Creates and initializes a new SpinnerAnimator.
     *
     * @param dashboardView    The dashboard view to refresh on each animation tick.
     * @param canvasTransform  Transform applied to produce the child-row canvas frame.
     * @param overlayTransform Transform applied to produce the parent-row overlay frame.
     *                         Pass img -> img to keep the raw image as-is.
     */
    SpinnerAnimator(DashboardView dashboardView,
                    UnaryOperator<Image> canvasTransform,
                    UnaryOperator<Image> overlayTransform) {
        this.dashboardView = dashboardView;
        this.frames        = new Image[FRAME_COUNT];
        this.framesOverlay = new Image[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            Image raw = LibertyDevPlugin.loadIcon("spinners/state/state_in_progress_" + (i + 1) + "_8");
            if (raw != null) {
                frames[i]        = (canvasTransform  != null) ? canvasTransform.apply(raw)  : raw;
                framesOverlay[i] = (overlayTransform != null) ? overlayTransform.apply(raw) : raw;
                // Dispose raw only if neither transform returned it as-is.
                if (raw != frames[i] && raw != framesOverlay[i] && !raw.isDisposed()) {
                    raw.dispose();
                }
            }
        }
    }

    /**
     * Returns the current spinner frame centred in the child-row canvas.
     *
     * @return The current canvas frame, or null if not loaded.
     */
    Image getCurrentFrame() {
        return frames[currentFrame];
    }

    /**
     * Returns the current spinner frame for use as a parent-row badge overlay.
     *
     * @return The current overlay frame, or null if not loaded.
     */
    Image getCurrentFrameOverlay() {
        return framesOverlay[currentFrame];
    }

    /**
     * Registers a project as STARTING and starts the animation loop if not already running.
     *
     * @param project The project to animate.
     */
    void addProject(ProjectModel project) {
        startingProjects.add(project);
        if (!running) {
            running = true;
            Display.getDefault().timerExec(FRAME_DELAY_MS, this::tick);
        }
    }

    /**
     * Removes a project from the animating set. The loop stops when the set is empty.
     *
     * @param project The project to stop animating.
     */
    void removeProject(ProjectModel project) {
        startingProjects.remove(project);
    }

    /**
     * Advances the frame index and refreshes each animating project's label.
     * Re-schedules itself while there are still STARTING projects.
     */
    private void tick() {
        if (!running || startingProjects.isEmpty()) {
            running = false;
            return;
        }
        currentFrame = (currentFrame + 1) % FRAME_COUNT;
        for (ProjectModel pm : startingProjects) {
            dashboardView.updateLabel(pm);
        }
        Display.getDefault().timerExec(FRAME_DELAY_MS, this::tick);
    }

    /**
     * Stops the animation and disposes all frame images.
     * Must be called on the SWT UI thread at label-provider dispose time.
     */
    void dispose() {
        running = false;
        startingProjects.clear();
        for (int i = 0; i < FRAME_COUNT; i++) {
            if (frames[i] != null && !frames[i].isDisposed()) {
                frames[i].dispose();
                frames[i] = null;
            }
            // framesOverlay[i] may be the same object as frames[i] (identity transform).
            if (framesOverlay[i] != null && !framesOverlay[i].isDisposed()) {
                framesOverlay[i].dispose();
                framesOverlay[i] = null;
            }
        }
    }
}
