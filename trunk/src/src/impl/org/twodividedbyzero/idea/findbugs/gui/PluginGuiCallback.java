/*
 * Copyright 2010 Andre Pfeiler
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

package org.twodividedbyzero.idea.findbugs.gui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.wm.WindowManager;
import edu.umd.cs.findbugs.BugCollection;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.cloud.Cloud;
import edu.umd.cs.findbugs.cloud.Cloud.CloudListener;
import edu.umd.cs.findbugs.cloud.Cloud.CloudTask;
import edu.umd.cs.findbugs.cloud.Cloud.CloudTaskListener;
import edu.umd.cs.findbugs.gui2.AbstractSwingGuiCallback;
import org.jetbrains.annotations.NotNull;
import org.twodividedbyzero.idea.findbugs.core.FindBugsPlugin;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;

public class PluginGuiCallback extends AbstractSwingGuiCallback {
    private final FindBugsPlugin plugin;
    private Cloud cloud;

    private CloudListener cloudListener = new CloudListener() {
        public void issueUpdated(BugInstance bug) {
            plugin.getToolWindowPanel().getBugDetailsComponents().issueUpdated(bug);
        }

        public void statusUpdated() {
            WindowManager.getInstance().getStatusBar(plugin.getProject()).setInfo(cloud.getStatusMsg());
        }

        public void taskStarted(final CloudTask task) {
            task.setUseDefaultListener(false);
            final Backgroundable backgroundable = new Backgroundable(plugin.getProject(), task.getName(), false) {

                @Override
                public void run(@NotNull final ProgressIndicator progressIndicator) {
                    try {
                        final CountDownLatch latch = new CountDownLatch(1);
                        task.addListener(new CloudTaskListener() {
                            public void taskStatusUpdated(String statusLine, double percentCompleted) {
                                progressIndicator.setText(statusLine);
                                progressIndicator.setFraction(percentCompleted / 100.0);
                            }

                            public void taskFinished() {
                                latch.countDown();
                            }

                            public void taskFailed(String message) {
                                progressIndicator.setText(message);
                                latch.countDown();
                            }
                        });
                        if (!task.isFinished())
                            latch.await();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    backgroundable.queue();
                }
            });
        }
    };

    public PluginGuiCallback(FindBugsPlugin plugin) {
        super(plugin.getToolWindowPanel());
        this.plugin = plugin;
    }

    public void setErrorMessage(String errorMsg) {
    }

    public void registerCloud(final edu.umd.cs.findbugs.Project project, BugCollection collection, final Cloud cloud) {
        this.cloud = cloud;
        cloud.addListener(cloudListener);
    }

    public void unregisterCloud(edu.umd.cs.findbugs.Project project, BugCollection collection, Cloud cloud) {
        //noinspection ObjectEquality
        if (cloud == this.cloud) {
            //noinspection AssignmentToNull
            this.cloud = null;
            cloud.removeListener(cloudListener);
        }
    }
}
