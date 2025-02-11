/*******************************************************************************
 * Copyright (c) 2023, 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.tools.eclipse.messages;

import org.eclipse.osgi.util.NLS;

/**
 * Translated messages.
 */
public class Messages extends NLS {

    /** DebugModeHandler */
    public static String multiple_server_env;

    /** DevModeOperations */
    public static String start_no_project_found;
    public static String start_already_issued;
    public static String start_general_error;

    public static String start_container_no_project_found;
    public static String start_container_already_issued;
    public static String start_container_general_error;

    public static String stop_no_project_found;
    public static String stop_already_issued;
    public static String stop_general_error;

    public static String run_tests_no_project_found;
    public static String run_tests_no_prior_start;
    public static String run_tests_general_error;

    public static String mvn_int_test_report_no_project_found;
    public static String mvn_int_test_report_none_found;
    public static String mvn_int_test_report_general_error;

    public static String mvn_unit_test_report_no_project_found;
    public static String mvn_unit_test_report_none_found;
    public static String mvn_unit_test_report_general_error;

    public static String gradle_test_report_no_project_found;
    public static String gradle_test_report_none_found;
    public static String gradle_test_report_general_error;

    public static String issue_stop_prompt;
    public static String plugin_stop_issue_error;
    public static String plugin_stop_timeout;
    public static String plugin_stop_failed;
    public static String plugin_stop_general_error;

    /** WorkspaceProjectsModel */
    public static String project_analyze_error;

    /** DashboardView */
    public static String project_not_gradle_or_maven;
    public static String image_descriptions_error;
    public static String action_general_error;
    public static String dashboard_refresh_error;

    /** JRETab */
    public static String java_default_set_error;
    public static String java_resolution_error;

    /** LaunchConfigurationDelegateLauncher */
    public static String launch_config_error;

    /** StartTab */
    public static String start_parm_retrieve_error;
    public static String project_name_error;
    public static String run_config_initialize_error;

    /** All *Action classes */
    public static String launch_shortcut_error;

    /** ExplorerMenuHandler */
    public static String project_not_valid;
    public static String menu_command_retrieve_error;
    public static String menu_command_process_error;

    /** CommandBuilder */
    public static String maven_exec_not_found;
    public static String gradle_exec_not_found;

    /** Project */
    public static String determine_java_project_error;
    public static String liberty_nature_add_error;

    static {
        NLS.initializeMessages("io.openliberty.tools.eclipse.messages.Messages", Messages.class);
    }
}