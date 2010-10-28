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
package org.nuxeo.ecm.platform.semanticentities;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PageProvider;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceRelation;

/**
 * Service to handle semantic entities linked to document in the local
 * repository. Data to seed those entities can come from remote sources by using
 * RemoteEntityService instead.
 * 
 * @author ogrisel
 */
public interface LocalEntityService {

    /**
     * The entity container is a singleton container document where to create
     * the entities.
     * 
     * It is useful to have this has a container document to be able to use the
     * regular ACL system of the Nuxeo repository to tell which group of users
     * has the right to create, edit or browse entities.
     * 
     * @return the DocumentModel of type EntityContainer (create it if missing)
     *         or null if the user does not have the permission to see it
     */
    DocumentModel getEntityContainer(CoreSession session)
            throws ClientException;

    /**
     * Helper method to suggest entities by keyword match on names.
     * 
     * @param keywords keywords to match the entity names
     * @param type the Nuxeo type name of entity to match (or null)
     * @param maxSuggestions maximum number of entities to suggest
     * @return a list of maximum maxSuggestions matching entities
     */
    List<DocumentModel> suggestEntity(CoreSession session, String keywords,
            String type, int maxSuggestions) throws ClientException;

    /**
     * Helper method to suggest documents by keyword match on fulltext content.
     * 
     * @param keywords keywords to match the document names
     * @param type the Nuxeo type name of documents to match (or null)
     * @param maxSuggestions maximum number of entities to suggest
     * @return a list of maximum maxSuggestions matching documents ordered by
     *         relevance
     */
    List<DocumentModel> suggestDocument(CoreSession session, String keywords,
            String type, int maxSuggestions) throws Exception;

    /**
     * Assert that an entity is referred to in the text content of a document.
     * As document content might change the occurrence position is identified by
     * a quoteContext String that contains the surrounding sentences and the
     * position of the expression referring to the entity is relative to this
     * context.
     * 
     * @param session active session to the repository holding the document and
     *            entities
     * @param docRef the id of the document referring to the entity
     * @param entityRef the id of the entity referred to by the document snippet
     * @param quoteContext the text snippet holding the expression pointing to
     *            the entity
     * @param startPosInContext the position of the start of the expression
     * @param endPosInContext
     * @return the DocumentModel of type Occurrence holding the relation
     */
    OccurrenceRelation addOccurrence(CoreSession session, DocumentRef docRef,
            DocumentRef entityRef, String quoteContext, int startPosInContext,
            int endPosInContext) throws ClientException;

    /**
     * Add several occurrences of the same entity in to a given document
     * (occurring in several text snippets).
     * 
     * @param session active session to the repository holding the document and
     *            entities
     * @param docRef the id of the document referring to the entity
     * @param entityRef the id of the entity referred to by the document snippet
     * @param occurrences list of occurrence data to add to the relationship
     * @return an OccurrenceRelation holding the aggregated occurrence data
     */
    OccurrenceRelation addOccurrences(CoreSession session, DocumentRef docRef,
            DocumentRef entityRef, List<OccurrenceInfo> occurrences)
            throws ClientException;

    /**
     * Find the occurrence relation instance linking a document to an entity.
     * Return null if no such relation exist in the repository.
     * 
     * @param session the repository session where the document is stored
     * @param documentRef the reference of the source document
     * @param entityRef the reference of the targeted entity
     * @return an instance of OccurrenceRelation or null
     */
    OccurrenceRelation getOccurrenceRelation(CoreSession session,
            DocumentRef documentRef, DocumentRef entityRef)
            throws ClientException;

    /**
     * Find entities of a given type related to a given document.
     * 
     * @param session the repository session where the document is stored
     * @param docRef the reference of the document to seach entities for
     * @param entityType the Nuxeo type of entities to lookup (can be null)
     * @return a paginated collection of matching entities
     */
    PageProvider<DocumentModel> getRelatedEntities(CoreSession session,
            DocumentRef docRef, String entityType) throws ClientException;

    /**
     * Find entities of a given type related to a given document.
     * 
     * @param session the repository session where the document is stored
     * @param entityRef the reference of the entities to search documents for
     * @param documentType the Nuxeo type of documents to lookup (can be null)
     * @return a paginated collection of matching entities
     */
    PageProvider<DocumentModel> getRelatedDocuments(CoreSession session,
            DocumentRef entityRef, String documentType) throws ClientException;

    /**
     * Lookup the local repo to find a local entity that is linked to the given
     * remote entity URI through a owl:sameAs relationship.
     * 
     * @param remoteEntityURI the entity URI to lookup locally
     * @return the matching local document model or null if none
     * @throws ClientException in case of problem accessing the local repo
     */
    DocumentModel getLinkedLocalEntity(CoreSession session, URI remoteEntityURI)
            throws ClientException;

    /**
     * @return the document type names deriving from the Entity type
     * @throws Exception thrown if the TypeManager is not available
     */
    public Set<String> getEntityTypeNames() throws Exception;

}
