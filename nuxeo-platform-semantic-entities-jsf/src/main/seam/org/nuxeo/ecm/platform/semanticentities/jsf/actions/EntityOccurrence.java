/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.jsf.actions;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;

/**
 * Data transfer object that provides all the required data to build JSF views to introspect the details of an
 * occurrence and review it.
 */
public class EntityOccurrence {

    protected final DocumentModel documentModel;

    protected final DocumentModel entityModel;

    protected final DocumentModel occurrenceModel;

    protected int maxDisplayedOccurences;

    protected final List<OccurrenceInfo> occurrences = new ArrayList<OccurrenceInfo>();

    public EntityOccurrence(DocumentModel doc, DocumentModel entity, DocumentModel occurrence,
            List<OccurrenceInfo> occurrences, int maxDisplayedOccurrences) {
        documentModel = doc;
        entityModel = entity;
        occurrenceModel = occurrence;
        this.occurrences.addAll(occurrences);
        maxDisplayedOccurences = maxDisplayedOccurrences;
    }

    // Getters for Seam / JSF

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public DocumentModel getEntityModel() {
        return entityModel;
    }

    public DocumentModel getOccurrenceModel() {
        return occurrenceModel;
    }

    public List<OccurrenceInfo> getOccurrences() {
        if (occurrences.size() > maxDisplayedOccurences) {
            return occurrences.subList(0, maxDisplayedOccurences);
        }
        return occurrences;
    }

    public int getExtractOccurrences() {
        int extra = occurrences.size() - maxDisplayedOccurences;
        return extra > 0 ? extra : 0;
    }
}
