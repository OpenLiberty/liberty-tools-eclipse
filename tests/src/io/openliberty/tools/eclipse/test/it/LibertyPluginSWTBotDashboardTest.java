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
package io.openliberty.tools.eclipse.test.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.*;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotDashboardTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {
        commonSetup();
    }

    /**
     * Tests opening the dashboard using the main toolbar icon.
     */
    @Test
    public void testOpenDashboardWithToolbarIcon() {
        openDashboardUsingToolbar();
    }

}
