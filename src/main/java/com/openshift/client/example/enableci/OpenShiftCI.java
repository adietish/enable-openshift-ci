/******************************************************************************* 
 * Copyright (c) 2012 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/

package com.openshift.client.example.enableci;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.openshift.client.IApplication;
import com.openshift.client.ICartridge;
import com.openshift.client.IDomain;
import com.openshift.client.IEmbeddableCartridge;
import com.openshift.client.IEmbeddedCartridge;
import com.openshift.client.IOpenShiftConnection;
import com.openshift.client.IUser;
import com.openshift.client.OpenShiftConnectionFactory;
import com.openshift.client.OpenShiftException;
import com.openshift.client.configuration.OpenShiftConfiguration;

/**
 * @author Andre Dietisheim
 */
public class OpenShiftCI {

	private static final String CLIENT_ID = "enable-openshift-ci";
	private static final String DEFAULT_DOMAIN_NAME = "openshiftci";
	private static final String DEFAULT_JENKINS_NAME = "jenkins";
	private static final long WAIT_TIMEOUT = 3 * 60 * 1000;

	private File project;
	private String user;
	private String password;
	private ExecutorService executor = Executors.newSingleThreadExecutor();

	public OpenShiftCI(File project, String user, String password) {
		this.project = project;
		this.user = user;
		this.password = password;
	}

	public void create() throws OpenShiftException, FileNotFoundException, IOException, InterruptedException,
			ExecutionException {
		try {
			IUser user = createUser();
			IDomain domain = getOrCreateDomain(user);
			IApplication application = getOrCreateApplication(project.getName(), domain);
			IApplication jenkinsApplication = getOrCreateJenkins(domain);
			waitForApplication(jenkinsApplication);
			waitForApplication(application);
			embedJenkinsClient(application);
			ensureQuotaNotReached(domain);
			deployToOpenShift(application);
			System.out.println("Done.");
		} finally {
			executor.shutdownNow();
		}
	}

	private IUser createUser() throws OpenShiftException, FileNotFoundException, IOException {
		System.out.print("Connecting to OpenShift...");
		IOpenShiftConnection connection =
				new OpenShiftConnectionFactory()
						.getConnection(CLIENT_ID, user, password, new OpenShiftConfiguration().getLibraServer());
		System.out.println("Connected.");
		return connection.getUser();
	}

	private IDomain getOrCreateDomain(IUser user) {
		IDomain domain = user.getDefaultDomain();
		if (domain == null) {
			System.out.println("Creating domain " + DEFAULT_DOMAIN_NAME + "...");
			domain = user.createDomain(DEFAULT_DOMAIN_NAME);
		}

		return domain;
	}

	private IApplication getOrCreateApplication(String name, IDomain domain) {
		System.out.print("Creating application " + name + "...");
		IApplication application = domain.getApplicationByName(name);
		if (application == null) {
			application = domain.createApplication(name, ICartridge.JBOSSAS_7);
			System.out.println("done.");
		} else {
			System.out.println("using existing.");
		}
		return application;
	}

	private IApplication getOrCreateJenkins(IDomain domain) {
		System.out.print("Creating jenkins application...");
		List<IApplication> jenkinsApplications = domain.getApplicationsByCartridge(ICartridge.JENKINS_14);
		IApplication jenkins = null;
		if (jenkinsApplications.isEmpty()) {
			jenkins = domain.createApplication(DEFAULT_JENKINS_NAME, ICartridge.JENKINS_14);
			System.out.println("done:" + jenkins.getCreationLog());
		} else {
			jenkins = jenkinsApplications.get(0);
			System.out.println("using existing.");
		}
		return jenkins;
	}

	private void waitForApplication(IApplication application) throws InterruptedException, ExecutionException {
		System.out.print("Waiting for application " + application.getName() + " to become reachable...");
		Future<Boolean> applicationAccessible =
				executor.submit(new ApplicationAvailability(application));
		if (!applicationAccessible.get()) {
			throw new RuntimeException("OpenShift application did not get reachable while timeout...");
		}
		System.out.println("application ready.");
	}

	private void embedJenkinsClient(IApplication application) {
		System.out.print("Embedding jenkins client...");
		IEmbeddedCartridge jenkinsClient = application.getEmbeddedCartridge(IEmbeddableCartridge.JENKINS_14);
		if (jenkinsClient == null) {
			jenkinsClient = application.addEmbeddableCartridge(IEmbeddableCartridge.JENKINS_14);
			System.out.println(jenkinsClient.getCreationLog());
		} else {
			System.out.println("using existing at " + jenkinsClient.getUrl() + ".");
		}
	}

	private void ensureQuotaNotReached(IDomain domain) {
		System.out.print("Checking for free application slot...");
		if (domain.getApplications().size() >= 3) {
			throw new RuntimeException("You already have 3 applications. Jenkins will need another free application slot for a builder application.");
		} else {
			System.out.println("Ok.");
		}
	}
	
	private class ApplicationAvailability implements Callable<Boolean> {

		private IApplication application;

		public ApplicationAvailability(IApplication application) {
			this.application = application;
		}

		public Boolean call() throws Exception {
			return application.waitForAccessible(WAIT_TIMEOUT);
		}
	}

	private void deployToOpenShift(IApplication application) throws IOException, InterruptedException {
		System.out.println(
				"Pushing project " + project.getName() + " to OpenShift application " + application.getName() + ":");
		exec("git add .");
		exec("git commit -a -m 'deploying'");
		exec("git remote add openshift -f " + application.getGitUrl());
		exec("git merge openshift/master -s recursive -X ours");
		exec("git push openshift HEAD -f --progress");
	}

	private void exec(String command) throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec(command, null, project);

		InputStream processOut = process.getInputStream();
		InputStream processErr = process.getErrorStream();

		byte[] buf = new byte[10];
		int read = -1;
		while ((read = processOut.read(buf)) != -1)
		{
			for (int i = 0; i < read; i++)
			{
				System.out.write(buf[i]);
			}
		}

		while ((read = processErr.read(buf)) != -1)
		{
			for (int i = 0; i < read; i++)
			{
				System.out.write(buf[i]);
			}
		}

		process.waitFor();
	}
}
