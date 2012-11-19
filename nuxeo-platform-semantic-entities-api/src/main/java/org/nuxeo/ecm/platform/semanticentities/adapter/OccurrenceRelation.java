/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.adapter;

import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * Helper interface to adapt a document model with the occurrence schema and
 * facet to help the manipulation of occurrence of names of entities in the text
 * rendition of a document.
 *
 * @author ogrisel
 */
public interface OccurrenceRelation {

    /**
     * @return the adapted occurrence relation document
     */
    DocumentModel getOccurrenceDocument();

    /**
     * @return the reference of the document that holds the text quotes
     *         mentioning the entity
     */
    DocumentRef getSourceDocumentRef() throws ClientException;

    /**
     * @return the reference of the entity that is mentioned by the document
     */
    DocumentRef getTargetEntityRef() throws ClientException;

    /**
     * @return the snippet info and the precise locations of the mentioned names
     *         inside those of snippets
     */
    List<OccurrenceInfo> getOccurrences() throws ClientException;

    List<OccurrenceInfo> getOccurrences(int maxOccurrences) throws ClientException;

    /**
     * Merge the list of occurrences with the existing occurrence info held by
     * the underlying document model using. Remove the duplicated entries
     * without altering the ordering.
     */
    void addOccurrences(List<OccurrenceInfo> occurrences)
            throws ClientException;

    /**
     * Replace existing occurrences with the a new list of occurrence info.
     * Remove the duplicated entries without altering the ordering.
     */
    void setOccurrences(List<OccurrenceInfo> occurrences)
            throws ClientException;

    /**
     * Lazy fetch the DocumentModel of the entity being mentioned.
     */
    public DocumentModel getTargetEntity() throws ClientException;

    /**
     * Lazy fetch the DocumentModel of the document carrying the entity mentions.
     */
    public DocumentModel getSourceDocument() throws ClientException;

}
