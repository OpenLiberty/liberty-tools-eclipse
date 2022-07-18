# Getting Started

## Before you begin

### Software requirements
- Eclipse IDE. Minimum Eclipse IDE version: 2022-03. Tested with Eclipse IDE for Enterprise Java and Web Developers packages.
- Java runtime. Tested versions: 11, 17.
- [Open Liberty tools for Eclipse plugin](../installation/installation.md).

### Application requirements

- Define a Liberty server.xml configuration file at location src/main/liberty/config.
- Configure the [Liberty Maven Plugin](https://github.com/OpenLiberty/ci.maven#configuration) or [Liberty Gradle Plugin](https://github.com/OpenLiberty/ci.gradle#adding-the-plugin-to-the-build-script)



## Opening the Liberty tools dashboard view

Click on the Open Liberty icon on the toolbar.

![Dashboard context menu for Maven Project](images/openLibertyIconOnToolbar.png)

Alternatively, click on the `Open Dashboard` option in the `Liberty` menu. 

![Dashboard context menu for Maven Project](images/openLibertyToolMenuDashboardView.png)

If the dashboard is opened and there are application projects that already properly configured to run on Liberty and use Liberty dev mode, those projects are automatically added to the dashboard when it opens.

If you add new projects or make changes, and you need the dashboard to be refreshed, use the refresh icon in the dashboard toolbar.

![Dashboard Refresh highlighted](images/maven-dashboardOpenedRefreshHighlight.png)

The Liberty tools dashboard provides a context menu to the projects in it. Users can choose the menu contents to perform a series of operations aiming to speed up application development.

Maven Projects             | Gradle Projects
:-------------------------:|:-------------------------:
![Dashboard context menu Gradle](images/maven-projectContextMenu.png) | ![Dashboard context menu Maven](images/gradle-projectContextMenu.png)

## Running your application on Liberty using dev mode

There are 3 ways to start your application in dev mode by using the dashboard menu actions associated with each of your application projects.

### Start

If you want to start your application project in dev mode, right click on the project name listed in the dashboard, and click on the `Start` action. A new terminal tab will open to run the application in dev mode.

![Start action started](images/maven-devModeStarted.png)

### Start with parameters

If you want to start your application project in dev mode with custom parameters such as -DhotTests=true, right click on the project name listed in the dashboard, and click on the  `Start...` action. 

A popup view will appear prompting you for custom parameters. Add the needed parameters and click `OK`. 

![Start with parameters popup](images/maven-startWithParmsPopupView.png)

A new terminal tab will open to run the application in dev mode. 

![Start with parameters started](images/maven-devModeITRun.png)

For a full list of supported parameters, check out the [Liberty Maven dev goal parameters](https://github.com/OpenLiberty/ci.maven/blob/main/docs/dev.md#additional-parameters) or the [Liberty Gradle dev task parameters](https://github.com/OpenLiberty/ci.gradle/blob/main/docs/libertyDev.md#command-line-parameters).

### Start in container

If you want to make use of dev mode for containers, right click on the project name listed in the dashboard, and click on the `Start in container` action. 

![Start in container](images/maven-devModeWithContainerStarted.png)

![Start in container ps command](images/maven-devModeWithContainerDockerProcess.png)

For more information on dev mode for containers, check out the [Liberty Maven devc goal](https://github.com/OpenLiberty/ci.maven/blob/main/docs/dev.md#devc-container-mode) or the [Liberty Gradle libertyDevc task](https://github.com/OpenLiberty/ci.gradle/blob/main/docs/libertyDev.md#libertydevc-task-container-mode).

## Running your application's tests

Once you are [running your application on Liberty using dev mode](#running-your-application-on-liberty-using-dev-mode), you can easily run the tests provided by your application. 

To do this, go to the Liberty tools dashboard, right click on the listed application project name, and select the `Run tests` action. The tests are run in the corresponding terminal.

![Run tests](images/maven-devModeITRun.png)

## Viewing your application's test reports

Once you are done [running your application's tests](#running-your-applications-tests), you can access the produced test reports.

### Maven built Application.

To view the integration test report right click on the project name listed in the dashboard, and click on the `View integration test report` action.

![Integration test report on browser](images/maven-devModeITReportShown.png)

To view the unit test report for Maven built applications, right click on the project name listed in the dashboard, and click on the `View unit test report` action.

![Integration test report on browser](images/maven-devModeUTReportShown.png)

### Gradle built Application.

To view the test report, right click on the project name listed in the dashboard, and click on the `View test report` action.

![Integration test report on browser](images/gradle-devModeTestReportShown.png)

Note that test reports are opened either in your Eclipse IDE's internal browser or an external browser depending on your Eclipse IDE's browser configuration preferences.

## Stopping your application.

To stop your application project, go to the Liberty tools dashboard, right click on the project you wish to stop, and select the `Stop` action.

![Stop action](images/maven-devModeStopped.png)

Once the project is stopped, the terminal in which it ran is closed.