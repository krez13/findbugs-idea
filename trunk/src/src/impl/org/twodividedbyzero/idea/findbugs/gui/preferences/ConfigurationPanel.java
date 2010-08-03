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
package org.twodividedbyzero.idea.findbugs.gui.preferences;

import info.clearthought.layout.TableLayout;
import org.twodividedbyzero.idea.findbugs.core.FindBugsPlugin;
import org.twodividedbyzero.idea.findbugs.preferences.AnalysisEffort;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Dictionary;
import java.util.Hashtable;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrepdo@dev.java.net>
 * @version $Revision$
 * @since 0.9.84-dev
 */
public class ConfigurationPanel extends JPanel {


	private final FindBugsPlugin _plugin;
	private JCheckBox _compileBeforeAnalyseChkb;
	private JCheckBox _analyzeAfterCompileChkb;
	private JCheckBox _runInBackgroundChkb;
	private JCheckBox _toolwindowToFront;
	private JComboBox _effortLevelCombobox;


	private JPanel _mainPanel;
	private JCheckBox _detectorThresholdChkb;
	private JPanel _effortPanel;
	private JTabbedPane _tabbedPane;
	private transient DetectorConfiguration _detectorConfig;
	private transient ReportConfiguration _reporterConfig;
	private JButton _restoreDefaultsButton;
	private JSlider _effortSlider;
	private transient FilterConfiguration _filterConfig;
	private transient PluginConfiguration _pluginConfig;


	public ConfigurationPanel(final FindBugsPlugin plugin) {
		super(new BorderLayout());

		if (plugin == null) {
			throw new IllegalArgumentException("Plugin may not be null.");
		}

		_plugin = plugin;

		initGui();

		final FindBugsPreferences preferences = getPreferences();
		if (!preferences.getBugCategories().containsValue("true") && !preferences.getDetectors().containsValue("true")) {  // NON-NLS
			restoreDefaultPreferences();
		}
	}


	private void initGui() {
		add(getMainPanel());
	}


	private Component getMainPanel() {
		if (_mainPanel == null) {

			final double border = 5;
			final double rowsGap = 0;
			final double colsGap = 10;
			final double[][] size = {{border, TableLayout.PREFERRED, colsGap, TableLayout.FILL, border}, // Columns
									 {border, TableLayout.PREFERRED, rowsGap, TableLayout.PREFERRED, 10, TableLayout.FILL, 10, TableLayout.PREFERRED, border}};// Rows
			final LayoutManager tbl = new TableLayout(size);

			_mainPanel = new JPanel();
			_mainPanel.setLayout(tbl);
			//panel.setBorder(BorderFactory.createTitledBorder("Inspections"));
			_mainPanel.setBorder(BorderFactory.createTitledBorder("Configuration options"));
			//_compileBeforeAnalyseChkb = new JCheckBox("Compile before inspect");

			updatePreferences();

			//_detectorThresholdChkb = new JCheckBox("Set DetectorThreshold");

			//_mainPanel.add(_compileBeforeAnalyseChkb);
			final Container checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			checkboxPanel.add(getRunInBgCheckbox());
			checkboxPanel.add(getAnalyzeAfterCompileCheckbox());
			checkboxPanel.add(getToolwindowToFrontCheckbox());
			_mainPanel.add(checkboxPanel, "1, 1, 1, 1");
			//_mainPanel.add(_detectorThresholdChkb);
			_mainPanel.add(getEffortPanel(), "1, 3, 1, 3");

			final Container tabPanel = new JPanel(new BorderLayout());
			tabPanel.add(getTabbedPane(), BorderLayout.CENTER);
			_mainPanel.add(tabPanel, "1, 5, 3, 5");
			_mainPanel.add(getRestoreDefaultsButton(), "3, 7, 3, 7, R, T");  // NON-NLS

		}

		return _mainPanel;
	}


	private void updatePreferences() {
		getEffortSlider().setValue(AnalysisEffort.valueOfLevel(getPreferences().getProperty(FindBugsPreferences.ANALYSIS_EFFORT_LEVEL, AnalysisEffort.DEFAULT.getEffortLevel())).getValue());
		getRunInBgCheckbox().setSelected(getPreferences().getBooleanProperty(FindBugsPreferences.RUN_ANALYSIS_IN_BACKGROUND, false));
		getAnalyzeAfterCompileCheckbox().setSelected(getPreferences().getBooleanProperty(FindBugsPreferences.ANALYZE_AFTER_COMPILE, false));
		getToolwindowToFrontCheckbox().setSelected(getPreferences().getBooleanProperty(FindBugsPreferences.TOOLWINDOW_TOFRONT, true));
		getEffortLevelCombobox().setSelectedItem(AnalysisEffort.valueOfLevel(getPreferences().getProperty(FindBugsPreferences.ANALYSIS_EFFORT_LEVEL, AnalysisEffort.DEFAULT.getEffortLevel())));
		//((FindBugsPluginImpl) _plugin).setPreferences(FindBugsPreferences.createDefaultPreferences());
		getReporterConfig().updatePreferences();
		getDetectorConfig().updatePreferences();
		getFilterConfig().updatePreferences();
		if (!_plugin.isModuleComponent()) {
			getPluginConfig().updatePreferences();
		}
	}


	private FindBugsPreferences getPreferences() {
		return _plugin.getPreferences();
	}


	private AbstractButton getRunInBgCheckbox() {
		if (_runInBackgroundChkb == null) {
			_runInBackgroundChkb = new JCheckBox("Run analysis in background");  // NON-NLS
			_runInBackgroundChkb.setFocusable(false);
			_runInBackgroundChkb.addItemListener(new ItemListener() {
				public void itemStateChanged(final ItemEvent e) {
					getPreferences().setProperty(FindBugsPreferences.RUN_ANALYSIS_IN_BACKGROUND, e.getStateChange() == ItemEvent.SELECTED);
				}
			});
		}
		return _runInBackgroundChkb;
	}


	private AbstractButton getAnalyzeAfterCompileCheckbox() {
		if (_analyzeAfterCompileChkb == null) {
			_analyzeAfterCompileChkb = new JCheckBox("Analyze affected files after compile");  // NON-NLS
			_analyzeAfterCompileChkb.setFocusable(false);
			_analyzeAfterCompileChkb.addItemListener(new ItemListener() {
				public void itemStateChanged(final ItemEvent e) {
					getPreferences().setProperty(FindBugsPreferences.ANALYZE_AFTER_COMPILE, e.getStateChange() == ItemEvent.SELECTED);
				}
			});
		}
		return _analyzeAfterCompileChkb;
	}


	private AbstractButton getToolwindowToFrontCheckbox() {
		if (_toolwindowToFront == null) {
			_toolwindowToFront = new JCheckBox("Activate toolwindow on run");  // NON-NLS
			_toolwindowToFront.setFocusable(false);
			_toolwindowToFront.addItemListener(new ItemListener() {
				public void itemStateChanged(final ItemEvent e) {
					getPreferences().setProperty(FindBugsPreferences.TOOLWINDOW_TOFRONT, e.getStateChange() == ItemEvent.SELECTED);
				}
			});
		}
		return _toolwindowToFront;
	}


	private Component getEffortPanel() {
		if (_effortPanel == null) {

			final double border = 5;
			final double rowsGap = 0;
			final double colsGap = 10;
			final double[][] size = {{border, TableLayout.PREFERRED, colsGap, TableLayout.PREFERRED, border}, // Columns
									 {border, TableLayout.PREFERRED, border}};// Rows
			final LayoutManager tbl = new TableLayout(size);

			_effortPanel = new JPanel(tbl);
			_effortPanel.add(new JLabel("Analysis effort"), "1, 1, 1, 1");  // NON-NLS
			_effortPanel.setBorder(null);
			_effortPanel.add(getEffortSlider(), "3, 1, 3, 1, l, t");
		}
		return _effortPanel;
	}


	@SuppressWarnings({"UseOfObsoleteCollectionType"})
	private JSlider getEffortSlider() {
		if (_effortSlider == null) {
			_effortSlider = new JSlider(JSlider.HORIZONTAL, 10, 30, 20);
			_effortSlider.setMajorTickSpacing(10);
			_effortSlider.setPaintTicks(true);
			_effortSlider.setSnapToTicks(true);
			_effortSlider.setBorder(null);
			_effortSlider.setFocusable(false);

			final Dictionary<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			labelTable.put(10, new JLabel(AnalysisEffort.MIN.getMessage()));
			labelTable.put(20, new JLabel(AnalysisEffort.DEFAULT.getMessage()));
			labelTable.put(30, new JLabel(AnalysisEffort.MAX.getMessage()));
			_effortSlider.setLabelTable(labelTable);
			_effortSlider.setPaintLabels(true);

			_effortSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(final ChangeEvent e) {
					final JSlider source = (JSlider) e.getSource();
					if (!source.getValueIsAdjusting()) {
						final int value = source.getValue();
						getPreferences().setProperty(FindBugsPreferences.ANALYSIS_EFFORT_LEVEL, AnalysisEffort.getEffortLevelByValue(value));
					}
				}
			});

		}
		return _effortSlider;
	}


	private JComboBox getEffortLevelCombobox() {
		if (_effortLevelCombobox == null) {
			_effortLevelCombobox = new JComboBox(new DefaultComboBoxModel(AnalysisEffort.values()));
			_effortLevelCombobox.addItemListener(new ItemListener() {
				public void itemStateChanged(final ItemEvent e) {
					getPreferences().setProperty(FindBugsPreferences.ANALYSIS_EFFORT_LEVEL, ((AnalysisEffort) e.getItem()).getEffortLevel());
				}
			});
		}
		return _effortLevelCombobox;
	}


	private Component getTabbedPane() {
		if (_tabbedPane == null) {
			_tabbedPane = new JTabbedPane();
			_tabbedPane.addTab("Detector configuration", getDetectorConfig().getComponent());  // NON-NLS
			_tabbedPane.addTab("Reporter configuration", getReporterConfig().getComponent());  // NON-NLS
			_tabbedPane.addTab("Filter configuration", getFilterConfig().getComponent());  // NON-NLS

			if (!_plugin.isModuleComponent()) {
				_tabbedPane.addTab("Plugin configuration", getPluginConfig().getComponent());  // NON-NLS
			}
		}
		return _tabbedPane;
	}


	DetectorConfiguration getDetectorConfig() {
		if (_detectorConfig == null) {
			_detectorConfig = new DetectorConfiguration(this, getPreferences());
		}
		return _detectorConfig;
	}


	ReportConfiguration getReporterConfig() {
		if (_reporterConfig == null) {
			_reporterConfig = new ReportConfiguration(this, getPreferences());
		}
		return _reporterConfig;
	}


	FilterConfiguration getFilterConfig() {
		if (_filterConfig == null) {
			_filterConfig = new FilterConfiguration(this, getPreferences());
		}
		return _filterConfig;
	}


	PluginConfiguration getPluginConfig() {
		if (_pluginConfig == null) {
			_pluginConfig = new PluginConfiguration(this, getPreferences());
		}
		return _pluginConfig;
	}


	private Component getRestoreDefaultsButton() {
		if (_restoreDefaultsButton == null) {
			_restoreDefaultsButton = new JButton("Restore defaults");  // NON-NLS
			_restoreDefaultsButton.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					restoreDefaultPreferences();
				}
			});
		}
		return _restoreDefaultsButton;
	}


	private void restoreDefaultPreferences() {
		final FindBugsPreferences bugsPreferences = getPreferences();
		bugsPreferences.setDefaults(FindBugsPreferences.createDefault());
		updatePreferences();
		bugsPreferences.setModified(true);
	}


	@Override
	public void setEnabled(final boolean enabled) {
		super.setEnabled(enabled);
		getRunInBgCheckbox().setEnabled(enabled);
		getAnalyzeAfterCompileCheckbox().setEnabled(enabled);
		getToolwindowToFrontCheckbox().setEnabled(enabled);
		getEffortLevelCombobox().setEnabled(enabled);
		getEffortSlider().setEnabled(enabled);
		getDetectorConfig().setEnabled(enabled);
		getReporterConfig().setEnabled(enabled);
		getFilterConfig().setEnabled(enabled);

		if (!_plugin.isModuleComponent()) {
			getPluginConfig().setEnabled(enabled);
		}

	}

}