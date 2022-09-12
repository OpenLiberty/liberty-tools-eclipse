#!/bin/bash

set -Eexo pipefail

DOCKER_WORK_DIR="eclipse-ltp"
DOCKER_REL_REPO_DIR_LOC="/liberty-tools-eclipse/releng/io.openliberty.tools.update/target/repository"
DOCKER_REL_ZIP_LOC="/liberty-tools-eclipse/releng/io.openliberty.tools.update/target/io.openliberty.tools.update.eclipse-repository-0.1.1.zip"
DOCKER_DOMAIN="release"
DOCKER_IMG_NAME="tmp-build-image-liberty-tools-eclipse:0.1.1"
DOCKER_IMG="${DOCKER_DOMAIN}/${DOCKER_IMG_NAME}"

RELEASE_OUTPUT_DIR="release-artifacts"

# Create the output directory to contain the release repository.
if [ ! -d  "$RELEASE_OUTPUT_DIR" ]; then
  mkdir "$RELEASE_OUTPUT_DIR"
fi

# Build the image.
#docker build --progress plain -t "$DOCKER_IMG" .

# Create a container.
container_id=$(docker create "$DOCKER_IMG")

# Copy the newly signed release artifacts for re-packaging.
docker cp "$RELEASE_OUTPUT_DIR/repository/features/io.openliberty.tools.eclipse.feature_0.1.1.jar" "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_REPO_DIR_LOC}/features"
docker cp "$RELEASE_OUTPUT_DIR/repository/plugins/io.openliberty.tools.eclipse.lsp4e_0.1.1.jar" "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_REPO_DIR_LOC}/plugins"
docker cp "$RELEASE_OUTPUT_DIR/repository/plugins/io.openliberty.tools.eclipse.ui_0.1.1.jar" "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_REPO_DIR_LOC}/plugins"

# start the container
docker start $container_id

#run the re-package mvn command
docker exec $container_id /bin/bash -c "mvn -pl releng tycho-p2-repository:fix-artifacts-metadata -Drepository.home=/eclipse-ltp/liberty-tools-eclipse/releng/io.openliberty.tools.update"

# Copy the re-packaged release artifacts.xmls locally
docker cp "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_REPO_DIR_LOC}/artifacts.xml.xz" "$RELEASE_OUTPUT_DIR/repository"
docker cp "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_REPO_DIR_LOC}/artifacts.jar" "$RELEASE_OUTPUT_DIR/repository"

# stop the container - this will remove it as well
docker stop $container_id

