/*
 * Copyright 2008-2013 Andre Pfeiler
 *
 * This file is part of FindBugs-IDEA.
 *
 * FindBugs-IDEA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FindBugs-IDEA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FindBugs-IDEA.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.twodividedbyzero.idea.findbugs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.common.event.EventListener;
import org.twodividedbyzero.idea.findbugs.common.event.EventManagerImpl;
import org.twodividedbyzero.idea.findbugs.common.event.filters.BugReporterEventFilter;
import org.twodividedbyzero.idea.findbugs.common.event.types.BugReporterEvent;
import org.twodividedbyzero.idea.findbugs.common.exception.FindBugsPluginException;
import org.twodividedbyzero.idea.findbugs.common.util.IdeaUtilImpl;
import org.twodividedbyzero.idea.findbugs.core.FindBugsPluginImpl;
import org.twodividedbyzero.idea.findbugs.core.FindBugsWorker;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.0.1
 */
public class AnalyzePackageFiles extends BaseAction implements EventListener<BugReporterEvent> {

	private static final Logger LOGGER = Logger.getInstance(AnalyzePackageFiles.class.getName());

	private DataContext _dataContext;
	private AnActionEvent _actionEvent;
	private boolean _enabled;
	private boolean _running;


	@Override
	public void actionPerformed(final AnActionEvent e) {
		_actionEvent = e;
		_dataContext = e.getDataContext();

		final com.intellij.openapi.project.Project project = DataKeys.PROJECT.getData(_dataContext);
		assert project != null;
		final Presentation presentation = e.getPresentation();

		// check a project is loaded
		if (isProjectNotLoaded(project, presentation)) {
			Messages.showWarningDialog("Project not loaded.", "FindBugs");
			return;
		}

		final FindBugsPreferences preferences = getPluginInterface(project).getPreferences();
		if (preferences.getBugCategories().containsValue("true") && preferences.getDetectors().containsValue("true")) {
			initWorker();
		} else {
			FindBugsPluginImpl.showToolWindowNotifier(project, "No bug categories or bug pattern detectors selected. analysis aborted.", MessageType.WARNING);
			ShowSettingsUtil.getInstance().editConfigurable(project, IdeaUtilImpl.getPluginComponent(project));
		}
	}


	@Override
	public void update(final AnActionEvent event) {
		try {
			_actionEvent = event;
			_dataContext = event.getDataContext();
			final Project project = DataKeys.PROJECT.getData(_dataContext);
			final Presentation presentation = event.getPresentation();

			// check a project is loaded, true when project is null
			if (isProjectNotLoaded(project, presentation)) {
				return;
			}

			isPluginAccessible(project);

			// check if tool window is registered
			final ToolWindow toolWindow = isToolWindowRegistered(project);
			if (toolWindow == null) {
				presentation.setEnabled(false);
				presentation.setVisible(false);

				return;
			}

			registerEventListener(project);

			final VirtualFile[] selectedSourceFiles = IdeaUtilImpl.getVirtualFiles(_dataContext);

			// enable ?
			if (!_running) {
				_enabled = selectedSourceFiles != null && selectedSourceFiles.length == 1 &&
						(IdeaUtilImpl.isValidFileType(selectedSourceFiles[0].getFileType()) || selectedSourceFiles[0].isDirectory()) &&
						null != getPackagePath(selectedSourceFiles, project);
			}
			presentation.setEnabled(toolWindow.isAvailable() && isEnabled());
			presentation.setVisible(true);

		} catch (final Throwable e) {
			final FindBugsPluginException processed = FindBugsPluginImpl.processError("Action update failed", e);
			LOGGER.error("Action update failed", processed);
		}
	}


	private void initWorker() {
		final com.intellij.openapi.project.Project project = IdeaUtilImpl.getProject(_dataContext);
		final Module module = IdeaUtilImpl.getModule(_dataContext);

		final FindBugsPreferences preferences = getPluginInterface(project).getPreferences();
		if (Boolean.valueOf(preferences.getProperty(FindBugsPreferences.TOOLWINDOW_TO_FRONT))) {
			IdeaUtilImpl.activateToolWindow(getPluginInterface(project).getInternalToolWindowId(), _dataContext);
		}

		final FindBugsWorker worker = new FindBugsWorker(project, module);

		final VirtualFile[] selectedSourceFiles = IdeaUtilImpl.getVirtualFiles(_dataContext);
		final VirtualFile packagePath = getPackagePath(selectedSourceFiles, project);

		// set aux classpath
		final VirtualFile[] files = IdeaUtilImpl.getProjectClasspath(_dataContext);
		worker.configureAuxClasspathEntries(files);

		// set source dirs
		final VirtualFile[] sourceRoots = IdeaUtilImpl.getModulesSourceRoots(_dataContext);
		worker.configureSourceDirectories(sourceRoots);

		// set class files
		final VirtualFile outPath = IdeaUtilImpl.getCompilerOutputPath(packagePath, project);
		final String packageUrl = IdeaUtilImpl.getPackageAsPath(project, packagePath);

		if (outPath != null) {
			final String output = outPath.getPresentableUrl() + packageUrl;
			worker.configureOutputFiles(output);
			worker.work("Running FindBugs analysis for directory '" + output + "'...");
		}
	}


	@Nullable
	private VirtualFile getPackagePath(@Nullable final VirtualFile[] selectedSourceFiles, @NotNull final Project project) {
		VirtualFile packagePath = null;
		if (selectedSourceFiles != null && selectedSourceFiles.length > 0) {
			for (final VirtualFile virtualFile : selectedSourceFiles) {
				final Module moduleOfFile = IdeaUtilImpl.findModuleForFile(virtualFile, project);
				if (moduleOfFile == null) {
					return null;
				}


				if (virtualFile.isDirectory()) {
					if (!virtualFile.getPath().endsWith(moduleOfFile.getName())) {
						packagePath = virtualFile;
					}
				} else {
					final VirtualFile parent = virtualFile.getParent();
					if (parent != null && !parent.getPath().endsWith(moduleOfFile.getName())) {
						packagePath = parent;
					}
				}
			}
		}
		return packagePath;
	}


	private void registerEventListener(final Project project) {
		final String projectName = project.getName();
		if (!isRegistered(projectName)) {
			EventManagerImpl.getInstance().addEventListener(new BugReporterEventFilter(projectName), this);
			addRegisteredProject(projectName);
		}
	}


	protected boolean isRunning() {
		return _running;
	}


	protected boolean setRunning(final boolean running) {
		final boolean was = _running;
		if (_running != running) {
			_running = running;
		}
		return was;
	}


	@Override
	protected boolean isEnabled() {
		return _enabled;
	}


	@Override
	protected boolean setEnabled(final boolean enabled) {
		final boolean was = _enabled;
		if (_enabled != enabled) {
			_enabled = enabled;
		}
		return was;
	}


	public void onEvent(@NotNull final BugReporterEvent event) {
		switch (event.getOperation()) {
			case ANALYSIS_STARTED:
				setEnabled(false);
				setRunning(true);
				break;
			case ANALYSIS_ABORTED:
			case ANALYSIS_FINISHED:
				setEnabled(true);
				setRunning(false);
				break;
		}
	}
}
