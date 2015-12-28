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
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.adapter;

import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * Helper interface to adapt a document model with the occurrence schema and facet to help the manipulation of
 * occurrence of names of entities in the text rendition of a document.
 *
 * @author ogrisel
 */
public interface OccurrenceRelation {

    /**
     * @return the adapted occurrence relation document
     */
    DocumentModel getOccurrenceDocument();

    /**
     * @return the reference of the document that holds the text quotes mentioning the entity
     */
    DocumentRef getSourceDocumentRef();

    /**
     * @return the reference of the entity that is mentioned by the document
     */
    DocumentRef getTargetEntityRef();

    /**
     * @return the snippet info and the precise locations of the mentioned names inside those of snippets
     */
    List<OccurrenceInfo> getOccurrences();

    List<OccurrenceInfo> getOccurrences(int maxOccurrences);

    /**
     * Merge the list of occurrences with the existing occurrence info held by the underlying document model using.
     * Remove the duplicated entries without altering the ordering.
     */
    void addOccurrences(List<OccurrenceInfo> occurrences);

    /**
     * Replace existing occurrences with the a new list of occurrence info. Remove the duplicated entries without
     * altering the ordering.
     */
    void setOccurrences(List<OccurrenceInfo> occurrences);

    /**
     * Lazy fetch the DocumentModel of the entity being mentioned.
     */
    public DocumentModel getTargetEntity();

    /**
     * Lazy fetch the DocumentModel of the document carrying the entity mentions.
     */
    public DocumentModel getSourceDocument();

}
