FROM icr.io/appcafe/open-liberty:kernel-slim-java11-openj9-ubi
COPY --chown=1001:0  target/liberty/wlp/usr/servers/maven.liberty.web.appServer/server.xml /config/
RUN features.sh