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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.openshift.client.IApplication;
import com.openshift.client.ICartridge;
import com.openshift.client.IDomain;
import com.openshift.client.IEmbeddableCartridge;
import com.openshift.client.IEmbeddedCartridge;
import com.openshift.client.IOpenShiftConnection;
import com.openshift.client.IOpenShiftSSHKey;
import com.openshift.client.ISSHPublicKey;
import com.openshift.client.IUser;
import com.openshift.client.OpenShiftConnectionFactory;
import com.openshift.client.OpenShiftException;
import com.openshift.client.SSHPublicKey;
import com.openshift.client.configuration.OpenShiftConfiguration;

/**
 * @author Andre Dietisheim
 */
public class OpenShiftCI {

	private static final String CLIENT_ID = "enable-openshift-ci";
	private static final String DEFAULT_DOMAIN_NAME = "openshiftci";
	private static final String DEFAULT_APPLICATION_NAME = "openshiftci";
	private static final String DEFAULT_JENKINS_NAME = "jenkins";
	private static final long WAIT_TIMEOUT = 3 * 60 * 1000;

	private static final File SSH_PUBLIC_KEY =
			new File(System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "id_rsa.pub");

	private String user;
	private String password;

	public OpenShiftCI(String user, String password) {
		this.user = user;
		this.password = password;
	}

	public void create() throws OpenShiftException, FileNotFoundException, IOException, InterruptedException,
			ExecutionException {
		IUser user = createUser();
		IDomain domain = getOrCreateDomain(user);
		IApplication application = getOrCreateApplication(domain);
		IApplication jenkinsApplication = getOrCreateJenkins(domain);
		waitForApplication(jenkinsApplication);
		waitForApplication(application);
		embedJenkinsClient(application);
		System.out
				.println("Jenkins is in place. It'll build with your next push to the " + DEFAULT_APPLICATION_NAME 
						+ ". You'll now want to add the openshift-profile to your pom, merge your application at "
						+ application.getGitUrl() + " into your local project and push it upstream.");
	}

	private IUser createUser() throws OpenShiftException, FileNotFoundException, IOException {
		System.out.print("Connecting to OpenShift...");
		IOpenShiftConnection connection =
				new OpenShiftConnectionFactory()
						.getConnection(CLIENT_ID, user, password, new OpenShiftConfiguration().getLibraServer());
		System.out.println("Connected.");
		return connection.getUser();
	}

	private IDomain getOrCreateDomain(IUser user) throws FileNotFoundException, OpenShiftException, IOException {
		System.out.print("Creating domain " + DEFAULT_DOMAIN_NAME + "...");
		IDomain domain = user.getDefaultDomain();
		if (domain == null) {
			domain = user.createDomain(DEFAULT_DOMAIN_NAME);
			System.out.println("done");
			addSSHKey(user);
		} else {
			System.out.println("using existing.");
		}

		return domain;
	}

	private void addSSHKey(IUser user) throws FileNotFoundException, IOException {
		if (!SSH_PUBLIC_KEY.exists()) {
			throw new IllegalStateException("no public key " + SSH_PUBLIC_KEY.getAbsolutePath()
					+ "found on your local machine");
		}
		ISSHPublicKey key = new SSHPublicKey(SSH_PUBLIC_KEY);
		IOpenShiftSSHKey addedKey = user.getSSHKeyByPublicKey(key.getPublicKey());
		if (addedKey == null) {
			user.putSSHKey(String.valueOf(System.currentTimeMillis()), key);
		}
	}

	private IApplication getOrCreateApplication(IDomain domain) {
		System.out.print("Creating application " + DEFAULT_APPLICATION_NAME + "...");
		IApplication application = domain.getApplicationByName(DEFAULT_APPLICATION_NAME);
		if (application == null) {
			application = domain.createApplication(DEFAULT_APPLICATION_NAME, ICartridge.JBOSSAS_7);
			System.out.println("done.");
		} else if (!application.getCartridge().equals(ICartridge.JBOSSAS_7)) {
			throw new RuntimeException(
					"You already have an application called " + DEFAULT_APPLICATION_NAME + " but it's not a "
							+ ICartridge.JBOSSAS_7.getName() + " application."
							+ ".");
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
				application.waitForAccessibleAsync(WAIT_TIMEOUT);
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
}
