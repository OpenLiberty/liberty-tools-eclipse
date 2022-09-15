#!/bin/bash

set -Eexo pipefail

DOCKER_WORK_DIR="eclipse-ltp"
DOCKER_REL_REPO_DIR_LOC="/liberty-tools-eclipse/releng/io.openliberty.tools.update/target/repository"
DOCKER_REL_ZIP_LOC="/liberty-tools-eclipse/releng/io.openliberty.tools.update/target/repo_zip"
DOCKER_DOMAIN="release"
DOCKER_IMG_NAME="tmp-build-image-liberty-tools-eclipse:0.1.1"
DOCKER_IMG="${DOCKER_DOMAIN}/${DOCKER_IMG_NAME}"

RELEASE_OUTPUT_DIR="release-artifacts"

# Create the output directory to contain the release repository.
if [ ! -d  "$RELEASE_OUTPUT_DIR" ]; then
  mkdir "$RELEASE_OUTPUT_DIR"
fi

# Build the image.
docker build --no-cache --progress plain -t "$DOCKER_IMG" .

# Create a container.
container_id=$(docker create "$DOCKER_IMG")

# Copy the release artifacts locally for signing.
docker cp "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_REPO_DIR_LOC}/" "$RELEASE_OUTPUT_DIR"
docker cp "${container_id}:/${DOCKER_WORK_DIR}/${DOCKER_REL_ZIP_LOC}/." "$RELEASE_OUTPUT_DIR"

# Delete the container.
docker rm -v "$container_id"
