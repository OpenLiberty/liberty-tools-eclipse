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
package liberty.tools.mpls;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4mp.commons.JavaFileInfo;
import org.eclipse.lsp4mp.commons.MicroProfileDefinition;
import org.eclipse.lsp4mp.commons.MicroProfileJavaCodeActionParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaCodeLensParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaCompletionParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaDefinitionParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaDiagnosticsParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaFileInfoParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaHoverParams;
import org.eclipse.lsp4mp.commons.MicroProfileJavaProjectLabelsParams;
import org.eclipse.lsp4mp.commons.MicroProfileProjectInfo;
import org.eclipse.lsp4mp.commons.MicroProfileProjectInfoParams;
import org.eclipse.lsp4mp.commons.MicroProfilePropertyDefinitionParams;
import org.eclipse.lsp4mp.commons.ProjectLabelInfoEntry;
import org.eclipse.lsp4mp.jdt.core.IMicroProfilePropertiesChangedListener;
import org.eclipse.lsp4mp.jdt.core.MicroProfileCorePlugin;
import org.eclipse.lsp4mp.jdt.core.ProjectLabelManager;
import org.eclipse.lsp4mp.jdt.core.PropertiesManager;
import org.eclipse.lsp4mp.jdt.core.PropertiesManagerForJava;
import org.eclipse.lsp4mp.jdt.internal.core.ls.JDTUtilsLSImpl;
import org.eclipse.lsp4mp.ls.api.MicroProfileLanguageClientAPI;
import org.eclipse.lsp4mp.ls.api.MicroProfileLanguageServerAPI;

/**
 * Liberty Devex MicroProfile language client.
 * 
 * @author
 *
 */
public class LibertyMPLSClientImpl extends LanguageClientImpl implements MicroProfileLanguageClientAPI {

	private static IMicroProfilePropertiesChangedListener SINGLETON_LISTENER;

	private IMicroProfilePropertiesChangedListener listener = event -> {
		((MicroProfileLanguageServerAPI) getLanguageServer()).propertiesChanged(event);
	};

	public LibertyMPLSClientImpl() {
		if (SINGLETON_LISTENER != null) {
			MicroProfileCorePlugin.getDefault().removeMicroProfilePropertiesChangedListener(SINGLETON_LISTENER);
		}
		SINGLETON_LISTENER = listener;
		MicroProfileCorePlugin.getDefault().addMicroProfilePropertiesChangedListener(listener);
	}

	@Override
	public CompletableFuture<MicroProfileProjectInfo> getProjectInfo(MicroProfileProjectInfoParams params) {
		return CompletableFutures.computeAsync((cancelChecker) -> {
			final MicroProfileProjectInfo[] projectInfo = new MicroProfileProjectInfo[1];
			Job job = Job.create("MicroProfile properties collector", (ICoreRunnable) monitor -> {
				projectInfo[0] = PropertiesManager.getInstance().getMicroProfileProjectInfo(params,
						JDTUtilsLSImpl.getInstance(), monitor);
			});
			job.schedule();
			try {
				job.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return projectInfo[0];
		});

	}

	private IProgressMonitor getProgressMonitor(CancelChecker cancelChecker) {
		IProgressMonitor monitor = new NullProgressMonitor() {
			public boolean isCanceled() {
				cancelChecker.checkCanceled();
				return false;
			};
		};
		return monitor;
	}

	@Override
	public CompletableFuture<Location> getPropertyDefinition(MicroProfilePropertyDefinitionParams params) {

		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> getJavaCodelens(MicroProfileJavaCodeLensParams javaParams) {
		return CompletableFutures.computeAsync((cancelChecker) -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			try {
				return PropertiesManagerForJava.getInstance().codeLens(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
			} catch (JavaModelException e) {
				LibertyMPLSPlugin.logException(e.getLocalizedMessage(), e);
				return Collections.emptyList();
			}
		});
	}

	@Override
	public CompletableFuture<List<PublishDiagnosticsParams>> getJavaDiagnostics(
			MicroProfileJavaDiagnosticsParams javaParams) {
		return CompletableFutures.computeAsync((cancelChecker) -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			try {
				return PropertiesManagerForJava.getInstance().diagnostics(javaParams, JDTUtilsLSImpl.getInstance(),
						monitor);
			} catch (JavaModelException e) {
				LibertyMPLSPlugin.logException(e.getLocalizedMessage(), e);
				return Collections.emptyList();
			}
		});
	}

	@Override
	public CompletableFuture<List<CodeAction>> getJavaCodeAction(MicroProfileJavaCodeActionParams javaParams) {
		return CompletableFutures.computeAsync((cancelChecker) -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			try {
				return (List<CodeAction>) PropertiesManagerForJava.getInstance().codeAction(javaParams,
						JDTUtilsLSImpl.getInstance(), monitor);
			} catch (JavaModelException e) {
				LibertyMPLSPlugin.logException(e.getLocalizedMessage(), e);
				return Collections.emptyList();
			}
		});
	}

	@Override
	public CompletableFuture<ProjectLabelInfoEntry> getJavaProjectlabels(
			MicroProfileJavaProjectLabelsParams javaParams) {
		return CompletableFutures.computeAsync((cancelChecker) -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			return ProjectLabelManager.getInstance().getProjectLabelInfo(javaParams, JDTUtilsLSImpl.getInstance(),
					monitor);
		});
	}
	
	@Override
	public CompletableFuture<JavaFileInfo> getJavaFileInfo(MicroProfileJavaFileInfoParams javaParams) {
		return CompletableFutures.computeAsync(cancelChecker -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			return PropertiesManagerForJava.getInstance().fileInfo(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
		});
	}

	@Override
	public CompletableFuture<List<MicroProfileDefinition>> getJavaDefinition(
			MicroProfileJavaDefinitionParams javaParams) {
		return CompletableFutures.computeAsync(cancelChecker -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			try {
				return PropertiesManagerForJava.getInstance().definition(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
			} catch (JavaModelException e) {
				LibertyMPLSPlugin.logException(e.getLocalizedMessage(), e);
				return null;
			}
		});
	}

	@Override
	public CompletableFuture<CompletionList> getJavaCompletion(MicroProfileJavaCompletionParams javaParams) {
		return CompletableFutures.computeAsync(cancelChecker -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			try {
				return PropertiesManagerForJava.getInstance().completion(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
			} catch (JavaModelException e) {
				LibertyMPLSPlugin.logException(e.getLocalizedMessage(), e);
				return null;
			}
		});
	}
	
	@Override
	public CompletableFuture<Hover> getJavaHover(MicroProfileJavaHoverParams javaParams) {
		return CompletableFutures.computeAsync((cancelChecker) -> {
			IProgressMonitor monitor = getProgressMonitor(cancelChecker);
			try {
				return PropertiesManagerForJava.getInstance().hover(javaParams, JDTUtilsLSImpl.getInstance(), monitor);
			} catch (JavaModelException e) {
				LibertyMPLSPlugin.logException(e.getLocalizedMessage(), e);
				return null;
			}	
		});
	}
}
