/*
 * Copyright 2009 Andre Pfeiler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.twodividedbyzero.idea.findbugs.gui.editor;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.Detector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.common.ExtendedProblemDescriptor;
import org.twodividedbyzero.idea.findbugs.common.event.EventListener;
import org.twodividedbyzero.idea.findbugs.common.event.EventManagerImpl;
import org.twodividedbyzero.idea.findbugs.common.event.filters.BugReporterEventFilter;
import org.twodividedbyzero.idea.findbugs.common.event.types.BugReporterEvent;
import org.twodividedbyzero.idea.findbugs.common.util.BugInstanceUtil;
import org.twodividedbyzero.idea.findbugs.common.util.IdeaUtilImpl;
import org.twodividedbyzero.idea.findbugs.resources.GuiResources;

import javax.swing.Icon;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrepdo@dev.java.net>
 * @version $Revision$
 * @since 0.9.92
 */
public class BugsLineMarkerProvider implements LineMarkerProvider, EventListener<BugReporterEvent>/*, DumbAware*/ {

	private Map<PsiFile, List<ExtendedProblemDescriptor>> _problemCache;
	private boolean _analysisRunning;
	private boolean _isRegistered;


	public BugsLineMarkerProvider() {
		_analysisRunning = false;
		_isRegistered = false;
	}


	@Nullable
	public LineMarkerInfo<?> getLineMarkerInfo(final PsiElement psiElement) {
		if(!_isRegistered) {
			EventManagerImpl.getInstance().addEventListener(new BugReporterEventFilter(psiElement.getProject().getName()), this);
			_isRegistered = true;
		}
		if(_analysisRunning) {
			return null;
		}

		final PsiFile psiFile = IdeaUtilImpl.getPsiFile(psiElement);
		_problemCache = IdeaUtilImpl.getPluginComponent(psiElement.getProject()).getProblems();

		if (_problemCache.containsKey(psiFile)) {
			final List<ExtendedProblemDescriptor> descriptors = _problemCache.get(psiFile);
			for (final ExtendedProblemDescriptor problemDescriptor : descriptors) {

				final PsiElement problemPsiElement = problemDescriptor.getPsiElement();
				if (psiElement.equals(problemPsiElement)) {
					
					if(psiElement instanceof PsiAnonymousClass) {
						final Editor[] editors = com.intellij.openapi.editor.EditorFactory.getInstance().getEditors(IdeaUtilImpl.getDocument(psiFile.getProject(), problemDescriptor));
						//editors[0].getMarkupModel().addRangeHighlighter()
					}

					final GutterIconNavigationHandler<PsiElement> navHandler = new BugGutterIconNavigationHandler();
					return new LineMarkerInfo<PsiElement>(problemPsiElement, problemPsiElement.getTextRange().getStartOffset(), getIcon(problemDescriptor), 4, new TooltipProvider(problemDescriptor), navHandler, GutterIconRenderer.Alignment.LEFT);
				}
			}
		}

		return null;
	}


	public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
	}


	@Nullable
	private static Icon getIcon(final ExtendedProblemDescriptor problemDescriptor) {
		final BugInstance bugInstance = problemDescriptor.getBugInstance();
		final int priority = bugInstance.getPriority();
		final Icon icon;
		switch (priority) {
			case Detector.HIGH_PRIORITY :
				icon = GuiResources.PRIORITY_HIGH_ICON;
				break;
			case Detector.NORMAL_PRIORITY :
				icon = GuiResources.PRIORITY_NORMAL_ICON;
				break;
			case Detector.LOW_PRIORITY :
				icon = GuiResources.PRIORITY_LOW_ICON;
				break;
			case Detector.EXP_PRIORITY :
				icon = GuiResources.PRIORITY_EXP_ICON;
				break;
			case Detector.IGNORE_PRIORITY :
			default:
				icon = GuiResources.PRIORITY_HIGH_ICON;
			break;

		}
		return icon;
	}


	public void onEvent(@NotNull final BugReporterEvent event) {
		switch (event.getOperation()) {

			case ANALYSIS_STARTED:
				_analysisRunning = true;
				break;
			case ANALYSIS_ABORTED:
				_analysisRunning = false;
				break;
			case ANALYSIS_FINISHED:
				_analysisRunning = false;
				break;
		}
	}


	private static class BugGutterIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {

		public void navigate(final MouseEvent e, final PsiElement psiElement) {
			//psiFileSystemItem.navigate(true);
			
		}
	}

	private static class TooltipProvider implements Function<PsiElement, String> {

		private final ExtendedProblemDescriptor _problemDescriptor;


		private TooltipProvider(final ExtendedProblemDescriptor problemDescriptor) {
			_problemDescriptor = problemDescriptor;
		}


		public String fun(final PsiElement psiElement) {
			return getTooltipText(_problemDescriptor);
		}


		private static String getTooltipText(final ExtendedProblemDescriptor problemDescriptor) {
			final StringBuilder buffer = new StringBuilder();
			buffer.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n");
			buffer.append("<HTML><HEAD><TITLE>");
			buffer.append(BugInstanceUtil.getBugPatternShortDescription(problemDescriptor.getBugInstance()));
			buffer.append("</TITLE></HEAD><BODY><H3>");
			buffer.append(BugInstanceUtil.getBugPatternShortDescription(problemDescriptor.getBugInstance()));
			buffer.append("</H3>\n");
			buffer.append(BugInstanceUtil.getDetailText(problemDescriptor.getBugInstance()));
			buffer.append("</BODY></HTML>\n");
			return buffer.toString();
		}


		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("TooltipProvider");
			sb.append("{_problemDescriptor=").append(_problemDescriptor);
			sb.append('}');
			return sb.toString();
		}
	}
}