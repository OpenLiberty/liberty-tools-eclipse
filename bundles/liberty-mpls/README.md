**Liberty-Lemminx extensions for server.xml support**

This jar was built from the https://github.com/OpenLiberty/liberty-language-server.git github repositoy.
This jar was built form the following SHA:
`commit 5105daae6083201ff6c47a7b0e7c3900ca5b6b49`

Process for re-building the liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar file:

1. git clone the repository into a local location
2. cd into the lemminx-liberty subdirectory
3. execute the mvn build: `> mvn verify`
4. Copy the resulting jar file 'liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar' from the resulting target subdirectoy into the following https://github.com/OpenLiberty/liberty-dev-tools-eclipse.git repository location: `liberty-dev-tools-eclipse/bundles/liberty-mpls/lib/`
5. manually insert this README file with an update to the current SHA that the liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar file was built at, if required

Ship the jar as part of the liberty-dev-tools-eclipse repository updatesThis the the README for the liberty mpls plugin