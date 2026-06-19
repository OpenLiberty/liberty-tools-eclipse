package io.openliberty.tools.eclipse.test.it.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ConstantsForAutomatedTest {
    /**
     * Application name - using Maven app
     */
    public static final String MAVEN_APP_NAME = "liberty.maven.test.app";

    /**
     * Test app relative path.
     */
    public static final Path mavenProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    public static String mpClassSnippet = "import org.eclipse.microprofile.health.Liveness;\n"
                                          + "\n"
                                          + "import jakarta.enterprise.context.ApplicationScoped;\n"
                                          + "\n"
                                          + "@Liveness\n"
                                          + "@ApplicationScoped";

    public static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Expected type-ahead options when at highest level in class.
     */
    public static String[] mptypeAheadOptions_classLevel = new String[] { "mpliveness", "mpreadiness", "mpnrc" };

    /**
     * Expected type-ahead options within REST class
     */
    public static String[] mptypeAheadOptions_inClass = new String[] { "mpliveness", "mpreadiness", "mpnrc" };
    public static String mpDiagnostics = "implementing the HealthCheck interface should use the @Liveness, @Readiness or @Startup annotation. [HealthAnnotationMissing]";

    public static String[] mpSnippet_quickFixes = new String[] { "Insert @Liveness" };

    public static String contentForMpDiagnostics = "package test.rest;\n"
                                                   + "\n"
                                                   + "import org.eclipse.microprofile.health.HealthCheck;\n"
                                                   + "import org.eclipse.microprofile.health.HealthCheckResponse;\n"
                                                   + "\n"
                                                   + "import jakarta.enterprise.context.ApplicationScoped;\n"
                                                   + "\n"
                                                   + "@ApplicationScoped\n"
                                                   + "public class RestApplicationTest implements HealthCheck {\n"
                                                   + "\n"
                                                   + " @Override\n"
                                                   + " public HealthCheckResponse call() {\n"
                                                   + "         return HealthCheckResponse.named(FieldConstraintValidation.class.getSimpleName()).withData(\"live\",true).up().build();\n"
                                                   + " }\n"
                                                   + "}";

    public static String contentForMpConfigDiagnostics = "mp.jwt.token.header=header";

    public static String mpConfigDiagnostics = "Configuration property to specify the HTTP header name expected to contain the JWT token.";

    /**
     * Sample MicroProfile Config properties content
     */
    public static String mpConfigPropertiesContent = "mp.jwt.token.header";

    /**
     * Content with invalid property format for diagnostics testing
     */
    public static String invalidPropertyContent = "# Invalid property format\n"
                                                  + "app.name=TestApp\n"
                                                  + "invalid property without equals\n"
                                                  + "app.version=1.0.0";

    /**
     * Expected diagnostic message for invalid property
     */
    public static String invalidPropertyDiagnostic = "Invalid property format";

    /**
     * Expected property key suggestions for content assist
     */
    public static String[] propertyKeySuggestions = new String[] { "mp.jwt.token.header", "mp.jwt.decrypt.key.location",
                                                                   "mp.jwt.verify.issuer",
                                                                   "mp.metrics.appName" };

    /**
     * Expected quick fixes for invalid properties
     */
    public static String[] invalidProperty_quickFixes = new String[] { "Remove invalid line", "Add equals sign" };

    /**
     * Expected type-ahead options when at highest level in class.
     */
    public static String[] typeAheadOptions_mpConfig = new String[] { "mp.jwt.token.header", "mp.jwt.decrypt.key.location", "mp.jwt.verify.issuer", "mp.metrics.appName" };

    /**
     * Expected type-ahead options when typing "rest_" in an empty file (class-level only)
     */
    public static String[] restClassSnippetOptions = new String[] { "rest_class" };

    /**
     * Expected type-ahead options when typing "rest_" inside a class (method-level)
     */
    public static String[] restMethodSnippetOptions = new String[] { "rest_head", "rest_get",
                                                                     "rest_post", "rest_put", "rest_delete" };

    public static final String invalidField = "@AssertTrue\n"
                                              + "    private int isHappy;";

    /**
     * Expected quick-fixes
     */
    public static String[] invalidField_quickFixes = new String[] { "Remove constraint annotation AssertTrue from element" };

    /**
     * Expected type-ahead options when at highest level in class.
     */
    public static String[] jakartaTypeAheadOptions_classLevel = new String[] { "rest_class", "persist_entity", "servlet_doget", "servlet_dopost",
                                                                               "servlet_generic", "servlet_webfilter" };

    /**
     * Expected type-ahead options within REST class
     */
    public static String[] jakartaTypeAheadOptions_inClass = new String[] { "persist_context", "persist_context_extended",
                                                                            "persist_context_extended_unsync", "rest_head", "rest_get", "rest_post", "rest_put", "rest_delete",
                                                                            "tx_user_inject",
                                                                            "tx_user_jndi" };

    public static String[] jakartaTypeAheadOptions_mpProperties = new String[] { "mp.jwt.token.cookie", "mp.jwt.token.header", "mp.jwt.decrypt.key.location",
                                                                                 "mp.health.disable-default-procedures",
                                                                                 "mp.jwt.verify.issuer", "servlet_webfilter" };

    public static String restClassSnippet = "import jakarta.ws.rs.GET;\n"
                                            + "import jakarta.ws.rs.Path;\n"
                                            + "import jakarta.ws.rs.Produces;\n"
                                            + "import jakarta.ws.rs.core.MediaType;\n"
                                            + "\n"
                                            + "@Path(\"/path\")\n";

    public static String restMethodSnippetDel = "@DELETE\n"
                                                + "@Consumes(MediaType.TEXT_PLAIN)";

    public static String assertTrueDiagnostics = "The @AssertTrue annotation can only be used on boolean and Boolean type fields. [InvalidAnnotationOnNonBooleanMethodOrField]";

    public static String[] bootstrapTypeAheadOptions = new String[] { "com.ibm.ws.logging.console.source",
                                                                      "com.ibm.hpel.log.bufferingEnabled",
                                                                      "com.ibm.ws.logging.console.log.level",
                                                                      "com.ibm.ws.logging.console.format",
                                                                      "com.ibm.ws.logging.trace.format",
                                                                      "com.ibm.hpel.trace.outOfSpaceAction" };
    public static String bootstrapPropertiesContent = "com.ibm.ws.logging.console.format";

    public static String[] serverEnvTypeAheadOptions = new String[] { "WLP_DEBUG_ADDRESS",
                                                                      "WLP_LOGGING_CONSOLE_FORMAT",
                                                                      "WLP_LOGGING_CONSOLE_LOGLEVEL",
                                                                      "WLP_LOGGING_JSON_ACCESS_LOG_FIELDS",
                                                                      "WLP_LOGGING_MESSAGE_FORMAT" };
    public static String serverEnvPropertiesContent = "WLP_LOGGING_CONSOLE_LOGLEVEL";
    public static String restClassSnippetToAdd = "package test.maven.liberty.web.app;\n"
                                                 + "\n"
                                                 + "import jakarta.ws.rs.GET;\n"
                                                 + "import jakarta.ws.rs.Path;\n"
                                                 + "import jakarta.ws.rs.Produces;\n"
                                                 + "import jakarta.ws.rs.core.MediaType;\n"
                                                 + "\n"
                                                 + "@Path(\"\"\n"
                                                 + "         + \"\")\n"
                                                 + "public class RestTestClass {\n"
                                                 + "\n"
                                                 + " @GET\n"
                                                 + " @Produces(MediaType.TEXT_PLAIN)\n"
                                                 + " public String methodname() {\n"
                                                 + "         return \"hello\";\n"
                                                 + " }\n"
                                                 + "}";

}
