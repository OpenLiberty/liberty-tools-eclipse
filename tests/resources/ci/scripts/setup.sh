#!/bin/bash

############################################################################
# Copyright (c) 2022, 2024 IBM Corporation and others.
# 
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0.
# 
# SPDX-License-Identifier: EPL-2.0
# 
# Contributors:
#     IBM Corporation - initial implementation
############################################################################

set -Eexo pipefail

# Operating system.
OS=$(uname -s)
OS_ARCH=$(uname -m)

# Semeru JDK version control constants.
# Example:
# Version (SEMERU_OPEN_JDK_BUILD): 11.0.14.1 / 11.0.14.0
# OpenJDK (SEMERU_OPEN_JDK_BUILD + SEMERU_OPEN_JDK_BUILD): 11.0.14.1+1 / 11.0.14+1
# OpenJ9  (SEMERU_OPENJ9_VERSION): 0.30.1
SEMERU_OPEN_JDK_MAJOR=21
SEMERU_OPEN_JDK_VERSION="${SEMERU_OPEN_JDK_MAJOR}.0.3"
SEMERU_OPEN_JDK_BUILD=9
SEMERU_OPENJ9_VERSION=0.44.0

SEMERU_ARCHIVE_MAC_X86_SHA256=95640346ef677fbdbf40efa0298cc61314cffed0c43d1b3bd329b84d445db869
SEMERU_ARCHIVE_MAC_AARCH64_SHA256=a95896a4ca7b69050a25b1557520f430abc66d098e9fd15cd394e20c4c93e5cf
SEMERU_ARCHIVE_LINUX_SHA256=5cccb39dc7ca6c61a11bd7179c4c3c30b747f9f22129576feef921b59725af25
SEMERU_ARCHIVE_WINDOWS_SHA256=11b82aed353f80752cdb5aaaadf9ac3398af8f7b3c32cfe80fc83d09ae445f6e

# Maven version control constants.
# NOTE: If this version is changed, update the "mvn clean install" command in tests/resources/ci/scripts/exec.sh.
MAVEN_VERSION=3.9.6
MAVEN_ARCHIVE_SHA512=0eb0432004a91ebf399314ad33e5aaffec3d3b29279f2f143b2f43ade26f4db7bd1c0f08e436e9445ac6dc4a564a2945d13072a160ae54a930e90581284d6461

# Gradle version control constants.
# NOTE: If this version is changed, the "mvn clean install" command in tests/resources/ci/scripts/exec.sh.
GRADLE_VERSION=8.8
GRADLE_ARCHIVE_SHA256=a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612

SOFTWARE_INSTALL_DIR="${PWD}/test-tools/liberty-dev-tools"

# main.
main() {
    # If we are not on a supported OS, exit.
    if [[ $OS != "Linux" && $OS != "Darwin" && $OS != "MINGW64_NT"* ]]; then
        echo "ERROR: OS $OS is not supported."
        exit -1
    fi
    
    # Create install directory.
    mkdir -p "$SOFTWARE_INSTALL_DIR"
    
    # Install all required software.
    installBaseSoftware
    installCustomSoftware
}

# installSoftware installs base software.
# MAC: Add `|| true` to failing brew command to bypass issue:
# https://github.com/actions/setup-python/issues/577
# Once the issue is resolved, `|| true' can be removed.
installBaseSoftware() {
    if [[ $OS == "Linux" ]]; then
        sudo apt-get update
        sudo apt-get install curl unzip 
        installXDisplaySoftwareOnLinux
        installDockerOnLinux
    elif [[ $OS == "Darwin" ]]; then
        brew update || true
        brew install curl unzip || true
        brew install docker || true
    else
        # Note: Docker is already installed on the windows VMs provisioned by GHA. 
        # Location: C:\Program Files\Docker\dockerd.exe
        choco install curl
        choco install unzip
    fi
}

# installSoftware installs customizable software.
installCustomSoftware() {	
	installJDK
	installMaven
	installGradle
}  

# installJDK installs the set version of the Semeru JDK.
installJDK() {
	local javaHome="${SOFTWARE_INSTALL_DIR}/jdk-${SEMERU_OPEN_JDK_VERSION}+${SEMERU_OPEN_JDK_BUILD}"

    # Download, validate, and expand the JDK archive.
	if [[ $OS == "Linux" ]]; then
        local url="https://github.com/ibmruntimes/semeru${SEMERU_OPEN_JDK_MAJOR}-binaries/releases/download/jdk-${SEMERU_OPEN_JDK_VERSION}%2B${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}/ibm-semeru-open-jdk_x64_linux_${SEMERU_OPEN_JDK_VERSION}_${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}.tar.gz"
        curl -fsSL -o /tmp/liberty-dev-tool-semeru-jdk.tar.gz "$url"
        echo "${SEMERU_ARCHIVE_LINUX_SHA256}  /tmp/liberty-dev-tool-semeru-jdk.tar.gz" | sha256sum -c - 
        tar -xzf /tmp/liberty-dev-tool-semeru-jdk.tar.gz -C "$SOFTWARE_INSTALL_DIR"
    elif [[ $OS == "Darwin" ]]; then
        javaHome="$javaHome"/Contents/Home

        if [[ $OS_ARCH == "arm64" ]]; then
            local url="https://github.com/ibmruntimes/semeru${SEMERU_OPEN_JDK_MAJOR}-binaries/releases/download/jdk-${SEMERU_OPEN_JDK_VERSION}%2B${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}/ibm-semeru-open-jdk_aarch64_mac_${SEMERU_OPEN_JDK_VERSION}_${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}.tar.gz"
            curl -fsSL -o /tmp/liberty-dev-tool-semeru-jdk.tar.gz "$url"
            echo "${SEMERU_ARCHIVE_MAC_AARCH64_SHA256}  /tmp/liberty-dev-tool-semeru-jdk.tar.gz" | shasum -a 256 -c -
        elif [[ $OS_ARCH == "x86_64" ]]; then
            local url="https://github.com/ibmruntimes/semeru${SEMERU_OPEN_JDK_MAJOR}-binaries/releases/download/jdk-${SEMERU_OPEN_JDK_VERSION}%2B${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}/ibm-semeru-open-jdk_x64_mac_${SEMERU_OPEN_JDK_VERSION}_${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}.tar.gz"
            curl -fsSL -o /tmp/liberty-dev-tool-semeru-jdk.tar.gz "$url"
            echo "${SEMERU_ARCHIVE_MAC_X86_SHA256}  /tmp/liberty-dev-tool-semeru-jdk.tar.gz" | shasum -a 256 -c -
        else
            echo "ERROR: Unknow architecture ${OS_ARCH}"
            exit -1
        fi

        tar -xzf /tmp/liberty-dev-tool-semeru-jdk.tar.gz -C "$SOFTWARE_INSTALL_DIR"
    else
        local url="https://github.com/ibmruntimes/semeru${SEMERU_OPEN_JDK_MAJOR}-binaries/releases/download/jdk-${SEMERU_OPEN_JDK_VERSION}%2B${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}/ibm-semeru-open-jdk_x64_windows_${SEMERU_OPEN_JDK_VERSION}_${SEMERU_OPEN_JDK_BUILD}_openj9-${SEMERU_OPENJ9_VERSION}.zip"
        curl -fsSL -o /tmp/liberty-dev-tool-semeru-jdk.zip "$url"
        local shaAll=$(certutil -hashfile /tmp/liberty-dev-tool-semeru-jdk.zip SHA256)
        local downloadedZipSha=$(echo $(echo $shaAll | tr '\r' ' ') | cut -d " " -f 5)
        if [ "$SEMERU_ARCHIVE_WINDOWS_SHA256" != "$downloadedZipSha" ]; then
            echo "ERROR: expected SHA: $SEMERU_ARCHIVE_WINDOWS_SHA256 is not equal to downloaded file calculated SHA of: $downloadedZipSha"
            exit -1
        fi
        unzip /tmp/liberty-dev-tool-semeru-jdk.zip -d "$SOFTWARE_INSTALL_DIR"
    fi

	# Set the JAVA_HOME environment variable and make it available to other steps within the executing job.
	echo "JAVA_HOME=${javaHome}" >> $GITHUB_ENV

	# Prepend the JDK installation's bin dir location to PATH and make it available to other steps within the executing job.
	echo "${javaHome}/bin" >> $GITHUB_PATH
}

# installMaven installs the set version of Maven.
installMaven() {
    local mavenHome="${SOFTWARE_INSTALL_DIR}/apache-maven-${MAVEN_VERSION}"
	local url="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.zip"

    # Download the Maven archive.
	curl -fsSL -o /tmp/liberty-dev-tool-apache-maven.zip "$url"

	# Check the downloaded archive's SHA against the expected value.
	if [[ $OS == "Linux" ]]; then
        echo "${MAVEN_ARCHIVE_SHA512}  /tmp/liberty-dev-tool-apache-maven.zip" | sha512sum -c - 	
    elif [[ $OS == "Darwin" ]]; then
        echo "${MAVEN_ARCHIVE_SHA512}  /tmp/liberty-dev-tool-apache-maven.zip" | shasum -a 512 -c - 
    else
        local shaAll=$(certutil -hashfile /tmp/liberty-dev-tool-apache-maven.zip SHA512)
        local downloadedZipSha=$(echo $(echo $shaAll | tr '\r' ' ') | cut -d " " -f 5)
        if [ "$MAVEN_ARCHIVE_SHA512" != "$downloadedZipSha" ]; then
            echo "ERROR: expected SHA: $MAVEN_ARCHIVE_SHA512 is not equal to downloaded file calculated SHA of: $downloadedZipSha"
            exit -1
        fi
    fi

    # Expand the archive.
    unzip -d "$SOFTWARE_INSTALL_DIR" /tmp/liberty-dev-tool-apache-maven.zip

    # Prepend the Maven installation's bin dir location to PATH and make it available to other steps within the executing job.
    echo "${mavenHome}/bin" >> $GITHUB_PATH
}

# installGradle installs the set version of Gradle.
installGradle() {
    local gradleHome="${SOFTWARE_INSTALL_DIR}/gradle-${GRADLE_VERSION}"
	local url="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

    # Download the Gradle archive.
    curl -fsSL -o /tmp/liberty-dev-tool-gradle.zip "$url"
      
    # Check the downloaded archive's SHA against the expected value.
	if [[ $OS == "Linux" ]]; then
        echo "${GRADLE_ARCHIVE_SHA256}  /tmp/liberty-dev-tool-gradle.zip" | sha256sum -c -
    elif [[ $OS == "Darwin" ]]; then
        echo "${GRADLE_ARCHIVE_SHA256}  /tmp/liberty-dev-tool-gradle.zip" | shasum -a 256 -c -
    else
        local shaAll=$(certutil -hashfile /tmp/liberty-dev-tool-gradle.zip SHA256)
        local downloadedZipSha=$(echo $(echo $shaAll | tr '\r' ' ') | cut -d " " -f 5)
        if [ "$GRADLE_ARCHIVE_SHA256" != "$downloadedZipSha" ]; then
            echo "ERROR: expected SHA: $GRADLE_ARCHIVE_SHA256 is not equal to downloaded file calculated SHA of: $downloadedZipSha"
            exit -1
        fi
    fi

    # Expand the archive.
    unzip -d "$SOFTWARE_INSTALL_DIR" /tmp/liberty-dev-tool-gradle.zip

    # Prepend the Gradle installation's bin dir location to PATH and make it available to other steps within the executing job.
    echo "${gradleHome}/bin" >> $GITHUB_PATH
}

# installXDisplaySoftwareOnLinux Installs a X display, a windows manager, and other pre-req software.
installXDisplaySoftwareOnLinux() {
    sudo apt-get install dbus-x11 xvfb metacity at-spi2-core 
} 

# installDocker installs docker.
installDockerOnLinux() {
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
