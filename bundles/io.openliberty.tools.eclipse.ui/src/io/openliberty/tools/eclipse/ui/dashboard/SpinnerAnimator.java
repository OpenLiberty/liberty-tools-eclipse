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

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.model.ProjectModel;

/**
 * Drives the STARTING in-progress spinner animation for the Liberty Dashboard.
 */
class SpinnerAnimator {

    /** Number of frames in the spinner animation. */
    static final int FRAME_COUNT = 12;

    /** Delay between frames in milliseconds (approximately 10 fps). */
    private static final int FRAME_DELAY_MS = 100;

    /** Spinner frame images after the caller-supplied transform has been applied. */
    private final Image[] frames;

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
     * @param dashboardView The dashboard view to refresh on each animation tick.
     * @param frameTransform Transform applied to each raw frame before storage.
     *                       Pass null to store the raw frame as-is.
     */
    SpinnerAnimator(DashboardView dashboardView, UnaryOperator<Image> frameTransform) {
        this.dashboardView = dashboardView;
        this.frames = new Image[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            ImageDescriptor desc = LibertyDevPlugin.loadIconDescriptor("spinners/state/state_in_progress_" + (i + 1) + "_8");
            if (desc == null) {
                continue;
            }
            Image raw = desc.createImage();
            if (raw == null) {
                continue;
            }
            if (frameTransform != null) {
                frames[i] = frameTransform.apply(raw);
                // Dispose the raw frame only when the transform produced a new image.
                if (frames[i] != raw && !raw.isDisposed()) {
                    raw.dispose();
                }
            } else {
                frames[i] = raw;
            }
        }
    }

    /**
     * Returns the current spinner frame image.
     *
     * @return The current frame, or null if not loaded.
     */
    Image getCurrentFrame() {
        return frames[currentFrame];
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
        }
    }
}
