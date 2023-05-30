/******************************************************************************* 
 * Copyright (c) 2019 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
/**
 * This class is a copy/paste of jbosstools-quarkus language server plugin
 * https://github.com/jbosstools/jbosstools-quarkus/blob/main/plugins/org.jboss.tools.quarkus.lsp4e/src/org/jboss/tools/quarkus/lsp4e/QuarkusLanguageClient.java
 * with modifications made for the Liberty Tools Microprofile LS plugin
 *
 */
package io.openliberty.tools.eclipse.jakarta.languageserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4jakarta.api.JakartaLanguageClientAPI;
import org.eclipse.lsp4jakarta.commons.JakartaClasspathParams;
import org.eclipse.lsp4jakarta.commons.JakartaDiagnosticsParams;
import org.eclipse.lsp4jakarta.commons.JakartaJavaCodeActionParams;
import org.eclipse.lsp4jakarta.commons.JakartaJavaCompletionParams;
import org.eclipse.lsp4jakarta.commons.JavaCursorContextKind;
import org.eclipse.lsp4jakarta.commons.JavaCursorContextResult;
import org.eclipse.lsp4jakarta.jdt.core.JDTServicesManager;
import org.eclipse.lsp4jakarta.jdt.core.JDTUtils;

/**
 * Liberty Devex MicroProfile language client.
 * 
 * @author
 *
 */
public class JakartaLSClientImpl extends LanguageClientImpl implements JakartaLanguageClientAPI {

    public JakartaLSClientImpl() {
        // do nothing
    }

    private IProgressMonitor getProgressMonitor(CancelChecker cancelChecker) {
        IProgressMonitor monitor = (IProgressMonitor) new NullProgressMonitor() {
            public boolean isCanceled() {
                cancelChecker.checkCanceled();
                return false;
            };
        };
        return monitor;
    }

    @Override
    public CompletableFuture<JavaCursorContextResult> getJavaCursorContext(JakartaJavaCompletionParams params) {
        JDTUtils utils = new JDTUtils();
        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            try {
                return JDTServicesManager.getInstance().javaCursorContext(params, utils, monitor);
            } catch (JavaModelException e) {
                return new JavaCursorContextResult(JavaCursorContextKind.IN_EMPTY_FILE, "");
            }
        });
    }
    
    @Override
    public CompletableFuture<List<PublishDiagnosticsParams>> getJavaDiagnostics(JakartaDiagnosticsParams javaParams) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);

            List<PublishDiagnosticsParams> publishDiagnostics = new ArrayList<PublishDiagnosticsParams>();
            publishDiagnostics = JDTServicesManager.getInstance().getJavaDiagnostics(javaParams);
            return publishDiagnostics;
        });
    }

    /**
     * @author ankushsharma
     * @brief creates a filter to let the language server know which contexts exist
     *        in the Java Project
     * @param uri            - String representing file from which to derive project
     *                       classpath
     * @param snippetContext - get all the context fields from the snippets and
     *                       check if they exist in this method
     * @return List<String>
     */
    @Override
    public CompletableFuture<List<String>> getContextBasedFilter(JakartaClasspathParams classpathParams) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            return JDTServicesManager.getInstance().getExistingContextsFromClassPath(classpathParams.getUri(), classpathParams.getSnippetCtx()); 
        });
    }

    @Override
    public CompletableFuture<List<CodeAction>> getCodeAction(JakartaJavaCodeActionParams params) {
        JDTUtils utils = new JDTUtils();

        return CompletableFutures.computeAsync((cancelChecker) -> {
            IProgressMonitor monitor = getProgressMonitor(cancelChecker);
            try {
                JakartaJavaCodeActionParams JakartaParams = new JakartaJavaCodeActionParams(params.getTextDocument(),
                        params.getRange(), params.getContext());
                return (List<CodeAction>) JDTServicesManager.getInstance().getCodeAction(JakartaParams, utils, monitor);
            } catch (JavaModelException e) {
                return null;
            }
        });
    }



}
