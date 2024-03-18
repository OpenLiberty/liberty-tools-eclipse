/*******************************************************************************
* Copyright (c) 2024 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.liberty.languageserver;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.swt.widgets.Display;

import io.openliberty.tools.eclipse.ls.plugin.LibertyToolsLSPlugin;

/**
 * Liberty Config language client.
 * 
 * @author
 */
public class LibertyLSClientImpl extends LanguageClientImpl {

    public LibertyLSClientImpl() {
        super();
        IWorkspace iWorkspace = ResourcesPlugin.getWorkspace();
        LCLSListener resourceChangeListener = new LCLSListener();
        iWorkspace.addResourceChangeListener(resourceChangeListener, IResourceChangeEvent.POST_BUILD);

    }

    public void fireUpdate(List<String> uris) {
        List<FileEvent> fileEvents = new ArrayList<FileEvent>();
        for (String uri : uris) {
            fileEvents.add(new FileEvent(uri, FileChangeType.Changed));
        }
        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams();
        params.setChanges(fileEvents);

        getLanguageServer().getWorkspaceService().didChangeWatchedFiles(params);
    }

    public class LCLSListener implements IResourceChangeListener {

        /**
         * {@inheritDoc}
         */
        @Override
        public void resourceChanged(IResourceChangeEvent event) {
            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run() {

                    IResourceDelta delta = event.getDelta();
                    if (delta == null) {
                        return;
                    }

                    final ArrayList<String> changed = new ArrayList<String>();

                    IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
                        public boolean visit(IResourceDelta delta) {
                            IResource resource = delta.getResource();
                            if (resource.getType() == IResource.FILE) {

                                // Look for changes to liberty-plugin-config.xml, *.properties, and *.env
                                if ("liberty-plugin-config.xml".equalsIgnoreCase(resource.getName())
                                        || "properties".equalsIgnoreCase(resource.getFileExtension())
                                        || "env".equalsIgnoreCase(resource.getFileExtension())) {
                                    changed.add(resource.getLocationURI().toString());
                                }
                            }
                            return true;
                        }
                    };

                    try {
                        delta.accept(visitor);
                    } catch (CoreException e) {
                        LibertyToolsLSPlugin.logException(e.getLocalizedMessage(), e);
                    }

                    if (!changed.isEmpty()) {
                        fireUpdate(changed);
                    }

                }
            });
        }
    }

}