/*******************************************************************************
* Copyright (c) 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.debug;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;
import org.eclipse.jdt.launching.sourcelookup.containers.JavaSourceLookupParticipant;

public class LibertySourceLookupDirector extends AbstractSourceLookupDirector {

    @Override
    public void initializeParticipants() {
        final List<ISourceLookupParticipant> participants = new ArrayList<>();

        participants.add(new JavaSourceLookupParticipant());

        addParticipants(participants.toArray(new ISourceLookupParticipant[participants.size()]));
    }
}
