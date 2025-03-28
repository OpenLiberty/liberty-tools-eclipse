# Liberty Tools Eclipse Changelog

## 25.0.3 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/10) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-25.0.3.202503281600) (March 28, 2025)

### Eclipse Platform target release upgrade
 * 4.34 (2025-03)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 2.2.1
 * Language Server for Jakarta EE (LSP4Jakarta) => 0.2.3

### Enhancements
 * New Liberty debugger reconnect support: https://github.com/OpenLiberty/liberty-tools-eclipse/issues/498

 * Switch from using Terminal to Console: https://github.com/OpenLiberty/liberty-tools-eclipse/pull/538

### Bug Fixes
 * Terminate and remove launches in Debug view: https://github.com/OpenLiberty/liberty-tools-eclipse/issues/293

## 24.0.12 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/9) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-24.0.12.202412180117) (Dec 18, 2024)

### Eclipse Platform target release upgrade
 * 4.33 (2024-09)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 2.2
 * Language Server for Jakarta EE (LSP4Jakarta) => 0.2.2
 * Language Server for MicroProfile (LSP4MP) => 0.13.2
 
## 24.0.9 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/8?closed=1) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-24.0.9.202409170137) (Sept. 24, 2024)

### Eclipse Platform target release upgrade
 * 4.32 (2024-06) - moves Eclipse to Java 21 baseline

### Enhancements
 * Compatibility with Maven Surefire Report plugin location and naming [change](https://issues.apache.org/jira/browse/SUREFIRE-2161).

### Bug Fixes
* File watcher added on liberty-plugin-config.xml, *.properties, and *.env files for compatibility with newer LCLS support: https://github.com/OpenLiberty/liberty-tools-eclipse/issues/461

## 24.0.6 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/7?closed=1) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-24.0.6.202406281517) (June. 28, 2024)

### Eclipse Platform target release upgrade
 * 4.31 (2024-03)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 2.1.3

## 24.0.3 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/6?closed=1) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-24.0.3.202403201340) (March. 20, 2024)

### Eclipse Platform target release upgrade
 * 4.30 (2023-12)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 2.1.2
 * Language Server for Jakarta EE (LSP4Jakarta) => 0.2.1

### Enhancements
 * Common Tab added to Run Configurations for saving configuration options to a local file. See [#484](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/484)

### Bug Fixes
* File watcher added on liberty-plugin-config.xml, *.properties, and *.env files for compatibility with newer LCLS support: https://github.com/OpenLiberty/liberty-tools-eclipse/issues/461

## 23.0.12 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/5?closed=1) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-23.0.12.202311281452) (Nov. 28, 2023)

### Eclipse Platform target release upgrade
 * 4.29 (2023-09)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 2.1.1
 * Language Server for Jakarta EE (LSP4Jakarta) => 0.2.0
 * Language Server for MicroProfile (LSP4MP) => 0.10.0

### Enhancements
 * Create new "Liberty Tools" Run/Debug configuration type populating source lookup with project Maven/Gradle dependencies.  See [#400](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/400).
 * Enable support for non-default config file paths (locations) for server.xml, server.env, bootstrap.properties.  See [#429](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/429).

### Bug Fixes
 * LSP4Jakarta quick fixes not appearing. See [#377](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/377).

### Other
 * Add suspend config option for debugging LS processes, and default to NOT suspend.  See [#465](https://github.com/OpenLiberty/liberty-tools-eclipse/pull/465).

## 23.0.9 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/4?closed=1) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-23.0.9.202309271814) (Sept. 27, 2023)

### Eclipse Platform target release upgrade
 * 4.28 (2023-06)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 2.0.1

### Other
 * Test fixes. See [#436](https://github.com/OpenLiberty/liberty-tools-eclipse/pull/436)

## 23.0.7 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/compare/liberty-tools-23.0.6.202306142047...liberty-tools-23.0.7.202307281406) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-23.0.7.202307281406) (July 28, 2023)

### Enhancements
 * Provide new option to stop via Maven/Gradle when server wasn't started by current IDE session. See [#248](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/248).
 * Add keyboard shortcuts for Start tab fields. See [#411](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/411).

### Bug Fixes
 * Increase stop timeout. See [#422](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/422).
 * Debug Configuration fails to persist user-added Source Lookup path entries. See [#372](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/372).

### Build
 * Add Mac aarch64 architecture to Tycho build.  See [#416](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/416).


## 23.0.6 [changes](https://github.com/OpenLiberty/liberty-tools-eclipse/milestone/3?closed=1) / [release](https://github.com/OpenLiberty/liberty-tools-eclipse/releases/tag/liberty-tools-23.0.6.202306142047) (June 20, 2023)

**NOTE:**:  This was the first version released we consider to be of "General Availability" quality, building on previous "Early Release" versions.  
So we begin the history here, but the milestone items only include the changes compared with the previous Early Release version of the feature.

#### Eclipse Platform target release upgrade
 * 4.27 (2023-03)

### Dependency Upgrades
 * Liberty Config Language Server (LCLS) => 1.0
 * Language Server for Jakarta EE (LSP4Jakarta) => 0.1.1
 * Language Server for MicroProfile (LSP4MP) => 0.7.0

### Enhancements
 * General Availability (GA) declaration
 * Populate default JRE selection in Run/Debug config JRE tab from project .classpath config. See [#271](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/271).
 * Add "Debug" and "Debug in container" selection options to Dashboard. See [#328](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/328).

### Bug Fixes
 * After running devc (start in container) the Run/Debug config history is messed up. See [#357](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/357).
 * On Windows, when Eclipse IDE is closed, server JVM started via dev mode dashboard is left running . See [#159](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/159).
 * No need to include trailing '/bin' dir in Maven/Gradle install locations in preferences. See [#330](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/330).

### Other
 * Make sure LSP-related JARs have the included versions easily discoverable.  See [#371](https://github.com/OpenLiberty/liberty-tools-eclipse/issues/371).

