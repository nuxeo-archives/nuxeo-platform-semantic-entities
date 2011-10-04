/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     ogrisel
 */
package org.nuxeo.ecm.platform.semanticentities.listener;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.SemanticAnalysisService;
import org.nuxeo.runtime.api.Framework;

/**
 * Event listener that tries to find occurrences of entities in document.
 */
public class SemanticEntitiesCoreListener implements PostCommitEventListener {

    private static final Log log = LogFactory.getLog(SemanticEntitiesCoreListener.class);

    @Override
    public void handleEvent(EventBundle events) throws ClientException {

        SemanticAnalysisService saService;
        Set<String> typesToIgnore = new HashSet<String>(Arrays.asList("Occurrence"));
        try {
            saService = Framework.getService(SemanticAnalysisService.class);
            LocalEntityService leService = Framework.getService(LocalEntityService.class);
            typesToIgnore.addAll(leService.getEntityTypeNames());
        } catch (Exception e) {
            log.error(e, e);
            return;
        }

        CoreSession session = null;
        Set<Serializable> ids = new HashSet<Serializable>();
        for (Event event : events) {
            if (!event.getName().equals(DocumentEventTypes.DOCUMENT_CREATED)) {
                continue;
            }
            EventContext eventContext = event.getContext();
            CoreSession s = eventContext.getCoreSession();
            DocumentModel dm = (DocumentModel) eventContext.getArguments()[0];
            if (dm.isVersion() || dm.isProxy()) {
                // do not perform analysis on archived versions: a separate
                // event listener should be used to
                // copy any previously existing the analysis results and manual
                // corrections instead.
                continue;
            }
            if (typesToIgnore.contains(dm.getType())) {
                continue;
            }
            ids.add(dm.getId());
            if (session == null) {
                session = s;
            } else if (session != s) {
                // cannot happen given current ReconnectedEventBundleImpl
                throw new ClientException(
                        "Several CoreSessions in one EventBundle");
            }
        }
        if (session == null) {
            if (ids.isEmpty()) {
                return;
            }
            throw new ClientException("Missing CoreSession");
        }

        for (Serializable id : ids) {
            IdRef docRef = new IdRef((String) id);
            // perform the entity extraction and linking operation
            try {
                saService.launchAnalysis(session.getRepositoryName(), docRef);
            } catch (Exception e) {
                log.error(e, e);
            }
        }
    }

}
