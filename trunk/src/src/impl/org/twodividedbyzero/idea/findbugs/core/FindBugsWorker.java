/**
 * Copyright 2008 Andre Pfeiler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.twodividedbyzero.idea.findbugs.core;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.FindBugs2;
import edu.umd.cs.findbugs.IFindBugsEngine;
import edu.umd.cs.findbugs.Plugin;
import edu.umd.cs.findbugs.SortedBugCollection;
import edu.umd.cs.findbugs.config.ProjectFilterSettings;
import edu.umd.cs.findbugs.config.UserPreferences;
import org.dom4j.DocumentException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.common.EventDispatchThreadHelper;
import org.twodividedbyzero.idea.findbugs.common.EventDispatchThreadHelper.OperationAdapter;
import org.twodividedbyzero.idea.findbugs.common.event.EventListener;
import org.twodividedbyzero.idea.findbugs.common.event.EventManagerImpl;
import org.twodividedbyzero.idea.findbugs.common.event.filters.BugReporterEventFilter;
import org.twodividedbyzero.idea.findbugs.common.event.types.BugReporterEvent;
import org.twodividedbyzero.idea.findbugs.common.util.IdeaUtilImpl;
import org.twodividedbyzero.idea.findbugs.gui.PluginGuiCallback;
import org.twodividedbyzero.idea.findbugs.preferences.AnalysisEffort;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;
import org.twodividedbyzero.idea.findbugs.report.BugReporter;
import org.twodividedbyzero.idea.findbugs.tasks.FindBugsTask;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;


/**
 * Execute FindBugs on a collection of java classes in an idea project/module.
 * <p/>
 * <pre>
 * FindBugsWorker worker = new FindBugsWorker(com.intellij.openapi.project.Project)
 * worker.configureAuxClasspathEntries(files);
 * worker.configureSourceDirectories(selectedSourceFiles);
 * worker.configureOutputFiles(compilerOutputPath, selectedSourceFiles);
 * worker.work();
 * </pre>
 * <p/>
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.0.1
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class FindBugsWorker implements EventListener<BugReporterEvent>, CompileStatusNotification {

	protected static final Logger LOGGER = Logger.getInstance(FindBugsWorker.class.getName());

	protected FindBugsProject _findBugsProject;
	protected final com.intellij.openapi.project.Project _project;
	protected UserPreferences _userPrefs;
	protected BugReporter _bugReporter;
	private CompilerManager _compilerManager;
	private final boolean _startInBackground;
	private Module _module;
	protected SortedBugCollection _bugCollection;
	//private RecurseCollectorTask _collectorTask;


	public FindBugsWorker(final com.intellij.openapi.project.Project project) {
		this(project, IdeaUtilImpl.getPluginComponent(project).getPreferences().getBooleanProperty(FindBugsPreferences.RUN_ANALYSIS_IN_BACKGROUND, false));
	}


	public FindBugsWorker(final com.intellij.openapi.project.Project project, final Module module) {
		final FindBugsPreferences preferences = IdeaUtilImpl.getPluginComponent(project).getPreferences();
		_startInBackground = preferences.getBooleanProperty(FindBugsPreferences.RUN_ANALYSIS_IN_BACKGROUND, false);
		_project = project;
		_module = module;

		configure();
	}


	public FindBugsWorker(final com.intellij.openapi.project.Project project, final boolean startInBackground) {
		_startInBackground = startInBackground;
		_project = project;

		configure();
	}


	/**
	 * Configure findbugs project settings.
	 * Note: detectors are configured in FindBugsPreferences
	 *
	 * @see org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences#applyDetectors()
	 */
	private void configure() {

		FindBugsPreferences preferences = IdeaUtilImpl.getPluginComponent(_project).getPreferences();
		if (_module != null && preferences.isModuleConfigEnabled(_module)) {
			preferences = IdeaUtilImpl.getModuleComponent(_module).getPreferences();
		}

		//_startInBackground = preferences.getBooleanProperty(FindBugsPreferences.RUN_ANALYSIS_IN_BACKGROUND, false);

		_userPrefs = preferences.getUserPreferences();//UserPreferences.createDefaultUserPreferences();
		_userPrefs.setEffort(preferences.getProperty(FindBugsPreferences.ANALYSIS_EFFORT_LEVEL, AnalysisEffort.DEFAULT.getEffortLevel()));

		final ProjectFilterSettings projectFilterSettings = _userPrefs.getFilterSettings();
		projectFilterSettings.setMinPriority(preferences.getProperty(FindBugsPreferences.MIN_PRIORITY_TO_REPORT));

		configureSelectedCategories(preferences, projectFilterSettings);
		//_userPrefs.setFilterSettings(projectFilterSettings);

		_userPrefs.setIncludeFilterFiles(preferences.getIncludeFilters());
		_userPrefs.setExcludeBugsFiles(preferences.getExcludeBaselineBugs());
		_userPrefs.setExcludeFilterFiles(preferences.getExcludeFilters());
		//_userPrefs.setUserDetectorThreshold(preferences.getProperty(FindBugsPreferences.MIN_PRIORITY_TO_REPORT)); // todo: needed?

		_findBugsProject = new FindBugsProject();
		_findBugsProject.setProjectName(_project.getName());
		for (Plugin plugin : Plugin.getAllPlugins()) {
			_findBugsProject.setPluginStatus(plugin, !preferences.isPluginDisabled(plugin.getPluginId()));
		}

		_bugCollection = new SortedBugCollection();
		FindBugsPlugin pluginComponent = IdeaUtilImpl.getPluginComponent(_project);
		_bugCollection.getProject().setGuiCallback(new PluginGuiCallback(pluginComponent));
		_bugCollection.setDoNotUseCloud(true);

		//CompilerManager.getInstance(_project).addCompilationStatusListener(this);

		//initCollectorTask(_findBugsProject);
	}


	private static void configureSelectedCategories(final FindBugsPreferences preferences, final ProjectFilterSettings projectFilterSettings) {
		for (final Entry<String, String> category : preferences.getBugCategories().entrySet()) {
			if ("true".equals(category.getValue())) {
				projectFilterSettings.addCategory(category.getKey());
			} else {
				projectFilterSettings.removeCategory(category.getKey());
			}
		}
	}

	/*public void initCollectorTask(final FindBugsProject findBugsProject) {
		_collectorTask = new RecurseCollectorTask(_project, "Collector...", true, findBugsProject);
		_findBugsProject.setCollectorTask(_collectorTask);
		
	}*/


	/*public void setUserDetectorThreshold(int threshold) {
	Detector.EXP_PRIORITY
		String minPriority = ProjectFilterSettings.getIntPriorityAsString(threshold);
		filterSettings.setMinPriority(minPriority);
	}*/


	public boolean work() {
		try {
			registerEventListner();
			final IFindBugsEngine engine = createFindBugsEngine();

			// Create FindBugsTask
			final FindBugsTask findBugsTask = new FindBugsTask(_project, _bugCollection, "Running FindBugs analysis...", true, engine, _startInBackground);
			_bugReporter.setFindBugsTask(findBugsTask);
			queue(findBugsTask);

			//_compilerManager = CompilerManager.getInstance(_project);
			//_compilerManager.addAfterTask(findBugsTask);
			//new File(virtualFile.getPath()) # method compileOutputFile(virtualfiles)
			//_compilerManager.compile(_findBugsProject.getOutputFiles(), null, true);
			return true;
		} catch (Exception e) {
			LOGGER.debug("FindBugs analysis failed.", e);
			unregisterEventListener();
			return false;
		}/* finally {
			//unregisterEventListner();
		}*/

	}


	public void compile(@NotNull final VirtualFile virtualFile, @NotNull final Project project, final CompileTask afterTask) {
		compile(new VirtualFile[] {virtualFile}, project, afterTask);
	}


	public void compile(final VirtualFile[] virtualFiles, final Project project, @Nullable final CompileTask afterTask) {
		//TODO: use make() with CompilerScope
		EventDispatchThreadHelper.invokeAndWait(new OperationAdapter() {
			public void run() {
				final CompilerManager compilerManager = CompilerManager.getInstance(project);
				if (afterTask != null) {
					addCompileAfterTask(project, afterTask);
				}
				compilerManager.compile(virtualFiles, afterTask != null ? null : FindBugsWorker.this, false);
			}
		});
	}


	private static void addCompileAfterTask(@NotNull final Project project, @NotNull final CompileTask afterTask) {
		final CompilerManager compilerManager = CompilerManager.getInstance(project);
		//compilerManager.make();
		compilerManager.addAfterTask(afterTask);
	}


	public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
		if (!aborted && errors == 0) {
			DaemonCodeAnalyzer.getInstance(_project).restart();
			//work();
		}
	}


	protected IFindBugsEngine createFindBugsEngine() {
		// Create BugReporter
		_bugReporter = new BugReporter(_project, _bugCollection, _findBugsProject);

		//final ProjectFilterSettings projectFilterSettings = _userPrefs.getFilterSettings();
		_bugReporter.setPriorityThreshold(_userPrefs.getUserDetectorThreshold());
		//_bugReporter.setPriorityThreshold(projectFilterSettings.getMinPriorityAsInt());

		// Create IFindBugsEngine
		final IFindBugsEngine engine = new FindBugs2();
		engine.setNoClassOk(true);
		engine.setMergeSimilarWarnings(false);
		engine.setBugReporter(_bugReporter);
		engine.setProject(_findBugsProject);
		engine.setProgressCallback(_bugReporter);
		//engine.setScanNestedArchives(true); // todo: prefrences Bean
		//engine.setRelaxedReportingMode(true); // todo: prefrences Bean
		//engine.setRankThreshold(99);
		configureFilter(engine);


		// add plugins to detector collection
		final DetectorFactoryCollection factoryCollection = FindBugsPreferences.getDetectorFactorCollection();
		engine.setDetectorFactoryCollection(factoryCollection);

		//engine.excludeBaselineBugs(_userPrefs.getExcludeBugsFiles());
		// configure detectors.
		engine.setUserPreferences(_userPrefs);
		return engine;
	}


	private void configureFilter(final IFindBugsEngine engine) {
		final Collection<String> excludeFilterFiles = _userPrefs.getExcludeFilterFiles();
		for (final String excludeFileName : excludeFilterFiles) {
			try {
				engine.addFilter(IdeaUtilImpl.replace$PROJECT_DIR$(excludeFileName), false);
			} catch (IOException e) {
				LOGGER.error("ExcludeFilter configuration failed.", e);
			}
		}
		final Collection<String> includeFilterFiles = _userPrefs.getIncludeFilterFiles();
		for (final String includeFileName : includeFilterFiles) {
			try {
				engine.addFilter(IdeaUtilImpl.replace$PROJECT_DIR$(includeFileName), true);
			} catch (IOException e) {
				LOGGER.error("IncludeFilter configuration failed.", e);
			}
		}
		final Collection<String> excludeBugFiles = _userPrefs.getExcludeBugsFiles();
		for (final String excludeBugFile : excludeBugFiles) {
			try {
				engine.excludeBaselineBugs(IdeaUtilImpl.replace$PROJECT_DIR$(excludeBugFile));
			} catch (IOException e) {
				LOGGER.error("ExcludeBaseLineBug files configuration failed.", e);
			} catch (DocumentException e) {
				LOGGER.error("ExcludeBaseLineBug files configuration failed.", e);
			}
		}
	}


	private static void queue(final FindBugsTask findBugsTask) {
		EventDispatchThreadHelper.invokeLater(new Runnable() {
			public void run() {
				findBugsTask.queue();
			}
		});
	}


	public void configureAuxClasspathEntries(final VirtualFile[] files) {
		_findBugsProject.configureAuxClasspathEntries(files);
		/*_collectorTask.setAuxClasspathEntries(files);
		_collectorTask.setCancelText("Cancel");
		_collectorTask.asBackgroundable();
		_collectorTask.queue();*/
	}


	public void configureSourceDirectories(final VirtualFile[] files) {
		_findBugsProject.configureSourceDirectories(files);
		//_collectorTask.setSourceDirectories(files);
	}


	public void configureSourceDirectories(final VirtualFile file) {
		_findBugsProject.configureSourceDirectories(file);
		//_collectorTask.setSourceDirectories(files);
	}


	public void configureOutputFiles(final VirtualFile[] files) {
		// set class files
		_findBugsProject.configureOutputFiles(_project, files);
		//_collectorTask.setOutputFiles(compilerOutputPath, _project, files);
	}


	public void configureOutputFiles(final VirtualFile packagePath) {
		// set class files
		_findBugsProject.configureOutputFiles(packagePath, _project);
		//_collectorTask.setOutputFiles(compilerOutputPath, _project, files);
	}


	public void configureOutputFile(final PsiClass psiClass) {
		_findBugsProject.configureOutputFile(_project, psiClass);
	}


	public void configureOutputFiles(final String path) {
		// set class files
		_findBugsProject.configureOutputFiles(path);
	}


	public void configureOutputFiles(final String[] paths) {
		// set class files
		for (final String path : paths) {
			_findBugsProject.configureOutputFiles(path);
		}
	}


	/**
	 * Return the collection of BugInstances.
	 *
	 * @return a collection of all bug instances
	 */
	public Collection<BugInstance> getBugInstances() {
		return _bugReporter.getBugCollection().getCollection();
	}


	void registerEventListner() {
		EventManagerImpl.getInstance().addEventListener(new BugReporterEventFilter(_project.getName()), this);
	}


	void unregisterEventListener() {
		EventManagerImpl.getInstance().removeEventListener(this);
	}


	public void onEvent(@NotNull final BugReporterEvent event) {
		switch (event.getOperation()) {
			case ANALYSIS_STARTED:
				break;
			case ANALYSIS_ABORTED:
				_bugReporter.getFindBugsTask().getProgressIndicator().cancel();
				unregisterEventListener();
				break;
			case ANALYSIS_FINISHED:
				unregisterEventListener();
				break;
			case NEW_BUG_INSTANCE:
				break;
		}
	}

}
