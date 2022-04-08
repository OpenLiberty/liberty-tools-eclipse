#!/bin/bash

set -Eexo pipefail

# Semeru JDK version control constants.
# Example:
# Version: 11.0.14.1 
# OpenJDK 11.0.14.1+1
# OpenJ9 0.30.1
SEMERU_OPEN_JDK_MAJOR=11
SEMERU_OPEN_JDK_VERSION="${SEMERU_OPEN_JDK_MAJOR}.0.14.1"
SEMERU_OPEN_JDK_BUILD=1
SEMERU_OPENJ9_VERSION=0.30.1
SEMERU_ARCHIVE_SHA256=25f3a8475b1f0b0ef54ff0247c7839fa4d6e7363adc2956d383a981aaa491b70

# Maven version control constants.
MAVEN_VERSION=3.8.5
MAVEN_ARCHIVE_SHA512=89ab8ece99292476447ef6a6800d9842bbb60787b9b8a45c103aa61d2f205a971d8c3ddfb8b03e514455b4173602bd015e82958c0b3ddc1728a57126f773c743

# Gradle version control constants.
GRADLE_VERSION=7.4.2
GRADLE_ARCHIVE_SHA256=29e49b10984e585d8118b7d0bc452f944e386458df27371b49b4ac1dec4b7fda

# Software install directory.
SOFTWARE_INSTALL_DIR="/opt/liberty-dev-tools"

# main.
main() {
    installSoftware
}

# installSoftware installs all required software.
installSoftware() {

    # Create install directory.
    mkdir -p "$SOFTWARE_INSTALL_DIR"
    
    # Install software.
	sudo apt-get update
	sudo apt-get install curl unzip

	installXDisplaySoftware
	installJDK
	installMaven
	installGradle
	installDocker
}

# installXDisplaySoftware Install the required X display related software and pre-reqs.
installXDisplaySoftware() {
	sudo apt-get install dbus-x11 at-spi2-core xvfb metacity
}

# installJDK installs the set version of the Semeru JDK.
installJDK() {
	local javaHome="${SOFTWARE_INSTALL_DIR}/jdk-${SEMERU_OPEN_JDK_VERSION}+${SEMERU_OPEN_JDK_BUILD}"
	local url="https://github.com/ibmruntimes/semeru${SEMERU_OPEN_JDK_MAJOR}-binaries/releases/download/jdk-${SEMERU_OPEN_JDK_VERSION}%2B${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}/ibm-semeru-open-jdk_x64_linux_${SEMERU_OPEN_JDK_VERSION}_${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}.tar.gz"

    # Download, validate, and expand the JDK archive.
	curl -fsSL -o /tmp/liberty-dev-tool-semeru-jdk.tar.gz "$url"
	echo "${SEMERU_ARCHIVE_SHA256}  /tmp/liberty-dev-tool-semeru-jdk.tar.gz" | sha256sum -c - 
	tar -xzf /tmp/liberty-dev-tool-semeru-jdk.tar.gz -C "$SOFTWARE_INSTALL_DIR"

	# Set the JAVA_HOME environment variable and make it available to other steps within the executing job.
	echo "JAVA_HOME=${javaHome}" >> $GITHUB_ENV

	# Prepend the JDK installation's bin dir location to PATH and make it available to other steps within the executing job.
	echo "${javaHome}/bin" >> $GITHUB_PATH
}

# installMaven installs the set version of Maven.
installMaven() {
    local mavenHome="${SOFTWARE_INSTALL_DIR}/apache-maven-${MAVEN_VERSION}"
	local url="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
	curl -fsSL -o /tmp/liberty-dev-tool-apache-maven.tar.gz "$url"

	# Download, validate, and expand the Maven archive.
	echo "${MAVEN_ARCHIVE_SHA512}  /tmp/liberty-dev-tool-apache-maven.tar.gz" | sha512sum -c - 
    tar -xzf /tmp/liberty-dev-tool-apache-maven.tar.gz -C "$SOFTWARE_INSTALL_DIR"

    # Set the MAVEN_HOME and M2_HOME environment variables and make them available to other steps within the executing job.
    echo "MAVEN_HOME=${mavenHome}" >> $GITHUB_ENV
    echo "M2_HOME=${mavenHome}" >> $GITHUB_ENV

    # Prepend the Maven installation's bin dir location to PATH and make it available to other steps within the executing job.
    echo "${mavenHome}/bin" >> $GITHUB_PATH
}

# installGradle installs the set version of Gradle.
installGradle() {
    local gradleHome="${SOFTWARE_INSTALL_DIR}/gradle-${GRADLE_VERSION}"
	local url="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

	# Download, validate, and expand the Maven archive.
	curl -fsSL -o /tmp/liberty-dev-tool-gradle.zip "$url"
	echo "${GRADLE_ARCHIVE_SHA256}  /tmp/liberty-dev-tool-gradle.zip" | sha256sum -c - 
    unzip -d "$SOFTWARE_INSTALL_DIR" /tmp/liberty-dev-tool-gradle.zip

    # Set the GRADLE_HOME environment variable and make it available to other steps within the executing job.
    echo "GRADLE_HOME=${gradleHome}" >> $GITHUB_ENV

    # Prepend the Gradle installation's bin dir location to PATH and make it available to other steps within the executing job.
    echo "${gradleHome}/bin" >> $GITHUB_PATH
}

# installDocker installs docker.
installDocker() {
    # Remove a previous installation of docker.
	sudo apt-get remove docker docker-engine docker.io containerd runc

	# Setup the docker repository before installation.
	sudo apt-get install ca-certificates gnupg lsb-release
	curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
	echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
         $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Install the docker engine.
    sudo apt-get update
    sudo apt-get install docker-ce docker-ce-cli containerd.io
}

main "$@"