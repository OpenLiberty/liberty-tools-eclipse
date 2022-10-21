# Installation

> NOTE: Starting with the [0.3 early release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-0.3.0), Eclipse version 2022-09 and Java 17 are required. If using Eclipse version 2022-03 or 2022-06, you must use [Liberty Tools version 0.2 ](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-0.2.0.202209201734).

> NOTE: When installing the 0.4 release, the org.eclipse.lsp4jakarta.jdt.core bundle is unsigned and will need to be "Trusted"

The Liberty tools plugin can be installed as new software by using the artifacts provided in the form of an update site or through an archive.

1. Go to menu: `Help`->`Install New Software`.

![Step 1. New software installation](images/install-installNewSotwareEntry.png)

2. Add the location containing the plugin's installation artifacts. This can be done in two ways:

- Using an update site. The update site (`update-site-<version>`) is provided as a [branch on Open Liberty Tools for eclipse git repository](https://github.com/OpenLiberty/liberty-tools-eclipse/branches). Select the version you would like to install and copy a link to the branch. For example, for the 0.4 early release, use the following URL: https://raw.githubusercontent.com/OpenLiberty/liberty-tools-eclipse/update-site-0.4.

Click on the `Add...` button to open the `Add Repository` view. Specify a name, paste the copied link as the location, and click on the `Add` button. 

![Step 2a. Add repository](images/install-addRepoSite.png)
 
 Select the Liberty tools software and click `Next`.

![Step 2a. Select Software to install](images/install-selectLibertyToolsFromSite.png)

- Using an archive. The archive is provided as an asset on the [Open Liberty tools for Eclipse releases page](https://github.com/OpenLiberty/liberty-tools-eclipse/releases). Select the version you would like to install, download the installation archive to your local workstation, and copy the path to the downloaded archive.

Click on the `Add...` button to open the `Add Repository` view. Specify a name, paste the location of the downloaded archive, and click on the `Add` button. 

![Step 2b. Add repository](images/install-addRepoArchive.png)

Select the Liberty tools software and click `Next`.

 ![Step 2b. Select Software to install](images/install-selectLibertyToolsFromArchive.png)


3. Review the installation details and click `Next`.

4. Review/Accept the License agreement and click `Finish`.

5.  Restart Eclipse.

![Step 5. Reboot](images/install-restartAfterInstall.png)

### Avoid trouble

The plugin uses the available Java, Maven, Gradle, and Docker executables to run the command actions associated with each project on the dashboard. 

#### Java

The plugin detects what Java executable to use by first checking the `JAVA_HOME` environment variable. If that variable is not set, the JRE used to run the Eclipse IDE itself is used. 

#### Maven/Gradle 

The plugin first looks if there is a Maven or Gradle wrapper in the project and uses that to run the command actions. For Maven, a wrapper can be created with the following command:
> mvn org.apache.maven.plugins:maven-wrapper-plugin:3.1.1:wrapper 

If no wrapper is included, the Maven and Gradle executables defined on the PATH environment variable are used. 

#### Docker 

The plugin detects what Docker executable to use by checking the Docker executable defined on the PATH environment variable.

Consequently, to fully setup your environment, be sure to check that your local PATH environment variable contains the paths to the Java, Maven, Gradle, and Docker executables. Be sure that the PATH is also visible to the terminal in your Eclipse IDE.

### Next steps

See: [Getting Started](../getting-started/getting-started.md)
