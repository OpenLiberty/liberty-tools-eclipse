/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial implementation
*******************************************************************************/
package io.openliberty.tools.eclipse.test.it.utils;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DisableOnMacCondition implements ExecutionCondition {
    
    /* This class introduces a JUNIT5 mecahnism for turning off individual testcases if running on MacOS
     * via an annotation. See DisabledOnMac for the annotation class which uses this class
     */
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
       String osName = System.getProperty("os.name");
       if(osName.equalsIgnoreCase("Mac OS X")) {
          return ConditionEvaluationResult.disabled("Test disabled on mac");
       } else {
          return ConditionEvaluationResult.enabled("Test enabled");
       }
    }
 }