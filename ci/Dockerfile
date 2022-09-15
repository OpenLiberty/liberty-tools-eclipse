FROM icr.io/appcafe/ibm-semeru-runtimes:open-17-jdk-ubi

# Install base pre-reqs.
RUN yum upgrade --disableplugin=subscription-manager -y \
   && yum clean --disableplugin=subscription-manager packages \
   && yum install --disableplugin=subscription-manager -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm \
   && yum install --disableplugin=subscription-manager -y unzip curl wget git

# Install maven.
ARG MAVEN_VERSION=3.8.4
ARG SHA=a9b2d825eacf2e771ed5d6b0e01398589ac1bfa4171f36154d1b5787879605507802f699da6f7cfc80732a5282fd31b28e4cd6052338cbef0fa1358b48a5e3c8
ARG BASE_URL=https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

# Clone the repo.
RUN mkdir eclipse-ltp \
    && cd eclipse-ltp \
    && git clone https://github.com/OpenLiberty/liberty-tools-eclipse.git
 
WORKDIR /eclipse-ltp/liberty-tools-eclipse

# build the repo and generate release artifacts.
RUN mvn clean install -DskipTests

# Move repository zip to a dedicated directory. This allows us to copy
# the zip without knowing the specific version 
RUN mkdir releng/io.openliberty.tools.update/target/repo_zip \
    && mv releng/io.openliberty.tools.update/target/io.openliberty.tools.update.eclipse-repository-*.zip releng/io.openliberty.tools.update/target/repo_zip

# command to run to make container available
CMD ["sleep", "9000"]
