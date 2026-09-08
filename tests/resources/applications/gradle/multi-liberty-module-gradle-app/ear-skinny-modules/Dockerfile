FROM icr.io/appcafe/open-liberty:kernel-slim-java11-openj9-ubi

# Add config
COPY --chown=1001:0  build/wlp/usr/servers/defaultServer/server.xml /config/
COPY --chown=1001:0  build/wlp/usr/servers/defaultServer/configDropins/overrides/liberty-plugin-variable-config.xml /config/configDropins/overrides/

# Install features declared in server.xml
RUN features.sh

# Add application
COPY --chown=1001:0  build/libs/guide-gradle-multimodules-custmm-ear-skinny-modules-1.0-SNAPSHOT.ear /config/apps/
