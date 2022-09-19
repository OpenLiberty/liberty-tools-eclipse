**Liberty-Lemminx extensions for server.xml support**

This jar was built from the https://github.com/OpenLiberty/liberty-language-server.git repository at commit:
SHA: 9a13e723471b89aae4a4b82f0d0629b9c3721c33

Process for re-building the liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar file:

1. git clone the repository into a local location
   > git clone https://github.com/OpenLiberty/liberty-language-server.git
2. cd into the lemminx-liberty subdirectory
   > cd liberty-language-server/lemminx-liberty
3. Capture the SHA that you are going to build from. Here is a one-liner to obtain this value:
   >  git rev-parse HEAD
4. execute the mvn build: 
   > mvn verify
5. Copy the resulting jar file 'liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar' from the resulting target subdirectoy into the following https://github.com/OpenLiberty/liberty-dev-tools-eclipse.git repository location: `liberty-dev-tools-eclipse/bundles/liberty-mpls/lib/`
   > cp target/liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar .../liberty-dev-tools-eclipse/bundles/liberty-mpls/lib/
6. Manually edit this README file.  Update the SHA in line 4 with the value captured in step 3. 
   > vi .../liberty-dev-tools-eclipse/bundles/liberty-mpls/README.md
7. manually insert this README file with an update to the current SHA that the liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar file was built at, if required
   >  cd .../liberty-dev-tools-eclipse/bundles/liberty-mpls;  jar uvf lib/liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar README.md
8. Ship the README and jar as part of the liberty-dev-tools-eclipse repository updates.  
   > git add README lib/liberty-langserver-lemminx-1.0-SNAPSHOT-jar-with-dependencies.jar; git commit ...
