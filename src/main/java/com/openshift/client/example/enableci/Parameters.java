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
import java.text.MessageFormat;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 * @author Andre Dietisheim
 */
public class Parameters {

	private JCommander jCommander = new JCommander();
	
	@Parameter(names = "-p", description = "the project (folder) that we'll enable CI for", required = true, converter = ProjectConverter.class, validateValueWith = ProjectValidator.class)
	private File project;

	public static class ProjectConverter implements IStringConverter<File> {

		public File convert(String projectPath) {
			return new File(projectPath);
		}
	}

	public static class ProjectValidator implements IValueValidator<File> {

		private static final String POM_FILE = "pom.xml";
		private static final String GIT_DIR = ".git";

		public void validate(String name, File project) throws ParameterException {
			if (!exists(project)) {
				throw new ParameterException(
						MessageFormat.format("Could not find directory {0}", project));
			}
			if (!isMaven(project)) {
				throw new ParameterException(
						MessageFormat.format("Project {0} is not a maven project", project));
			}
			if (!isGit(project)) {
				throw new ParameterException(
						MessageFormat.format("Project {0} is not committed to a git repository", project));
			}
		}

		public boolean exists(File project) {
			return project.isDirectory() && project.exists();
		}

		public boolean isMaven(File project) {
			return new File(project, POM_FILE).exists();
		}

		public boolean isGit(File project) {
			return new File(project, GIT_DIR).exists();
		}
	}

	public File getProject() {
		return project;
	}

	@Parameter(names = "-u", description = "the OpenShift user", required = true)
	private String user;

	public String getUser() {
		return user;
	}

	@Parameter(names = "-pw", description = "the OpenShift password", required = true)
	private String password;

	public String getPassword() {
		return password;
	}

	public void parse(String[] argv) {
		jCommander.addObject(this);
		jCommander.parse(argv);
	}

	public void usage() {
		jCommander.usage();
	}
	
}
