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
package org.twodividedbyzero.idea.findbugs.common;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.0.1
 */
@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "StringConcatenation", "CallToPrintStackTrace", "CallToPrintStackTrace"})
public class VersionManager {

	private static final long _major = 0;
	private static final long _minor = 9;
	private static final long _build = 96;

	private static final String _branch = "";


	private static final String NAME = "FindBugs-IDEA";

	private static final String WEBSITE = "http://findbugs-idea.dev.java.net";

	private static final String DOWNLOAD_WEBSITE = "http://plugins.intellij.net/plugin/?id=3847";

	private static final String SUPPORT_EMAIL = "andrepdo@dev.java.net";

	private static final long REVISION;

	private static final String FULL_VERSION_INTERNAL;

	private static final String MAJOR_MINOR_BUILD = _major + "." + _minor + "." + _build;

	private static final String MAJOR_MINOR_BUILD_REVISION;


	static {
		final String revisionString = VersionManager.class.getPackage().getImplementationVersion();
		long parsedRevision = -1;
		if (revisionString != null) {
			try {
				parsedRevision = Long.parseLong(revisionString);
				System.out.println("Revision: " + revisionString);
				System.out.println("parsedRevision: " + parsedRevision);
			} catch (final RuntimeException ignore) {
			}
		}
		REVISION = parsedRevision;
		MAJOR_MINOR_BUILD_REVISION = MAJOR_MINOR_BUILD + (REVISION == -1 ? "" : "." + REVISION);
		FULL_VERSION_INTERNAL = NAME + " " + MAJOR_MINOR_BUILD_REVISION + ("".equals(_branch) ? "" : "-" + _branch);
	}


	/** e.g. "0.9.21".
	 * @return*/
	private static String getVersion() {
		return MAJOR_MINOR_BUILD;
	}


	/** e.g. "0.9.21.26427" if revision is available, else "0.9.21".
	 * @return*/
	public static String getVersionWithRevision() {
		return MAJOR_MINOR_BUILD_REVISION;
	}


	public static String getBranch() {
		return _branch;
	}


	/* e.g. "FindBugs-IDEA 0.9.21.26427". */


	public static String getFullVersion() {
		return FULL_VERSION_INTERNAL;
	}


	public static long getRevision() {
		return REVISION;
	}


	public static String getName() {
		return NAME;
	}


	public static String getWebsite() {
		return WEBSITE;
	}


	public static String getDownloadWebsite() {
		return DOWNLOAD_WEBSITE;
	}


	public static String getSupportEmail() {
		return SUPPORT_EMAIL;
	}


	@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
	public static void main(final String[] args) {
		if (args.length == 1) {
			final File file = new File(args[0]);
			System.out.println("version string file: " + args[0]);
			FileWriter writer = null;
			try {
				writer = new FileWriter(file);
				writer.write(getVersion());
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (writer != null) {
						writer.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}
		System.out.println(getVersion());
		System.out.println(getFullVersion());
		System.out.println(getVersionWithRevision());
	}
}