/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     ogrisel
 */
package org.nuxeo.ecm.platform.semanticentities.listener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
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

    protected List<String> eventNames = Arrays.asList(DocumentEventTypes.DOCUMENT_CREATED);

    protected List<String> documentTypes = new ArrayList<String>();

    @Override
    public void handleEvent(EventBundle events) {
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
            if (!eventNames.isEmpty() && !eventNames.contains(event.getName())) {
                continue;
            }
            EventContext eventContext = event.getContext();
            CoreSession s = eventContext.getCoreSession();
            DocumentModel dm = (DocumentModel) eventContext.getArguments()[0];
            if (!documentTypes.isEmpty() && !documentTypes.contains(dm.getType())) {
                continue;
            }
            if (dm.isVersion() || dm.isProxy() || dm.isFolder()) {
                // do not perform analysis on archived versions: a separate
                // event listener should be used to
                // copy any previously existing the analysis results and manual
                // corrections instead.
                continue;
            }
            if (dm.getContextData(SemanticAnalysisService.SKIP_SEMANTIC_ANALYSIS) != null) {
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
                throw new NuxeoException("Several CoreSessions in one EventBundle");
            }
        }
        if (session == null) {
            if (ids.isEmpty()) {
                return;
            }
            throw new NuxeoException("Missing CoreSession");
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
