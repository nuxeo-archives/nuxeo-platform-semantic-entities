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
package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PageProvider;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.platform.semanticentities.Constants;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceRelation;
import org.nuxeo.runtime.model.DefaultComponent;

public class LocalEntityServiceImpl extends DefaultComponent implements
        LocalEntityService {

    public static final Log log = LogFactory.getLog(LocalEntityServiceImpl.class);

    // TODO: make me configurable in an extension point
    public static final String ENTITY_CONTAINER_PATH = "/entities";

    public static final String ENTITY_CONTAINER_TITLE = "%i18nEntities";

    public DocumentModel getEntityContainer(CoreSession session)
            throws ClientException {
        final PathRef ref = new PathRef(ENTITY_CONTAINER_PATH);
        if (!session.exists(ref)) {
            // either the container has not been created yet or the current user
            // cannot see it because of a lack of permissions to do so

            int lastSlashIdx = ENTITY_CONTAINER_PATH.lastIndexOf('/');
            final String id = ENTITY_CONTAINER_PATH.substring(lastSlashIdx + 1);
            final String parentPath = ENTITY_CONTAINER_PATH.substring(0,
                    lastSlashIdx + 1);

            UnrestrictedSessionRunner runner = new UnrestrictedSessionRunner(
                    session) {
                @Override
                public void run() throws ClientException {
                    if (!session.exists(ref)) {
                        DocumentModel container = session.createDocumentModel(
                                parentPath, id, Constants.ENTITY_CONTAINER_TYPE);
                        container.setPropertyValue("dc:title",
                                ENTITY_CONTAINER_TITLE);
                        session.createDocument(container);
                        session.save();
                    }
                }
            };
            runner.runUnrestricted();
        }

        if (!session.exists(ref)) {
            // the user does not have the right to see the container
            return null;
        }
        return session.getDocument(ref);
    }

    public OccurrenceRelation addOccurrence(CoreSession session,
            DocumentRef docRef, DocumentRef entityRef, String quoteContext,
            int startPosInContext, int endPosInContext) throws ClientException {
        OccurrenceInfo info = new OccurrenceInfo(quoteContext,
                startPosInContext, endPosInContext);
        return addOccurrences(session, docRef, entityRef, Arrays.asList(info));
    }

    public OccurrenceRelation getOccurrenceRelation(CoreSession session,
            DocumentRef docRef, DocumentRef entityRef) throws ClientException {
        return getOccurrenceRelation(session, docRef, entityRef, false);
    }

    public OccurrenceRelation getOccurrenceRelation(CoreSession session,
            DocumentRef docRef, DocumentRef entityRef, boolean createIfMissing)
            throws ClientException {
        String q = String.format("SELECT * FROM Occurrence"
                + " WHERE relation:source = '%s'"
                + " AND relation:target = '%s'"
                + " ORDER BY dc:created LIMIT 2", docRef, entityRef);
        DocumentModelList occurrences = session.query(q);
        if (occurrences.isEmpty()) {
            if (createIfMissing) {
                // create an empty document model in memory and adapt it to the
                // OccurrenceRelation interface
                DocumentModel occ = session.createDocumentModel("Occurrence");
                occ.setPropertyValue("relation:source", docRef.toString());
                occ.setPropertyValue("relation:target", entityRef.toString());
                return occ.getAdapter(OccurrenceRelation.class);
            } else {
                return null;
            }
        } else {
            if (occurrences.size() > 1) {
                log.warn(String.format(
                        "more than one occurrence found linking document"
                                + " '%s' to entity '%s'", docRef, entityRef));
            }
            return occurrences.get(0).getAdapter(OccurrenceRelation.class);
        }
    }

    public OccurrenceRelation addOccurrences(CoreSession session,
            DocumentRef docRef, DocumentRef entityRef,
            List<OccurrenceInfo> occurrences) throws ClientException {
        if (!session.hasPermission(docRef, Constants.ADD_OCCURRENCE_PERMISSION)) {
            // check the permission on the source document
            throw new SecurityException(String.format(
                    "%s has not the permission to add an entity"
                            + " occurrence on document with id '%s'",
                    session.getPrincipal().getName(), docRef));
        }
        OccurrenceRelation relation = getOccurrenceRelation(session, docRef,
                entityRef, true);
        if (occurrences != null && !occurrences.isEmpty()) {
            relation.addOccurrences(occurrences);
        }
        UpdateOrCreateOccurrenceRelation op = new UpdateOrCreateOccurrenceRelation(
                session, relation);
        op.runUnrestricted();
        return session.getDocument(op.occRef).getAdapter(
                OccurrenceRelation.class, true);
    }

    protected static class UpdateOrCreateOccurrenceRelation extends
            UnrestrictedSessionRunner {

        protected final OccurrenceRelation relation;

        protected DocumentRef occRef;

        public UpdateOrCreateOccurrenceRelation(CoreSession session,
                OccurrenceRelation relation) {
            super(session);
            this.relation = relation;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() throws ClientException {
            // update the entity aggregated alternative names for better
            // fulltext indexing
            DocumentModel entity = session.getDocument(relation.getTargetEntityRef());
            List<String> altnames = entity.getProperty("entity:altnames").getValue(
                    List.class);
            for (OccurrenceInfo occInfo : relation.getOccurrences()) {
                if (!occInfo.mention.equals(entity.getPropertyValue("dc:title"))) {
                    if (!altnames.contains(occInfo.mention)) {
                        altnames = new ArrayList<String>(altnames);
                        altnames.add(occInfo.mention);
                    }
                }
            }
            entity.setPropertyValue("entity:altnames", (Serializable) altnames);

            if (relation.getOccurrenceDocument().getId() == null) {
                // this is a creation of a new relation between a document and
                // the entity
                occRef = session.createDocument(
                        relation.getOccurrenceDocument()).getRef();
                // update the popularity estimate
                Long newPopularity = entity.getProperty("entity:popularity").getValue(
                        Long.class) + 1;
                entity.setPropertyValue("entity:popularity", newPopularity);

            } else {
                // this is an update of an existing relation
                occRef = session.saveDocument(relation.getOccurrenceDocument()).getRef();

            }
            session.saveDocument(entity);
            session.save();
        }
    }

    public PageProvider<DocumentModel> getRelatedDocuments(CoreSession session,
            DocumentRef entityRef, String documentType) throws ClientException {
        if (documentType == null) {
            documentType = "cmis:document";
        }
        if (!(entityRef instanceof IdRef)) {
            throw new NotImplementedException(
                    "Only IdRef instance are currently supported, got "
                            + entityRef.getClass().getName());
        }
        String query = String.format(
                "SELECT Doc.cmis:objectId FROM %s Doc "
                        + "JOIN Relation Rel ON Rel.relation:source = Doc.cmis:objectId "
                        + "WHERE Rel.relation:target = '%s' "
                        + "ORDER BY Doc.dc:modified DESC", documentType,
                entityRef);
        return new CMISQLDocumentPageProvider(session, query,
                "Doc.cmis:objectId");
    }

    public PageProvider<DocumentModel> getRelatedEntities(CoreSession session,
            DocumentRef docRef, String entityType) throws ClientException {
        if (entityType == null) {
            entityType = "Entity";
        }
        if (!(docRef instanceof IdRef)) {
            throw new NotImplementedException(
                    "Only IdRef instance are currently supported, got "
                            + docRef.getClass().getName());
        }

        // order by number of incoming links instead?
        String query = String.format(
                "SELECT Ent.cmis:objectId FROM %s Ent "
                        + "JOIN Relation Rel ON Rel.relation:target = Ent.cmis:objectId "
                        + "WHERE Rel.relation:source = '%s' "
                        + "ORDER BY Ent.dc:title", entityType, docRef);
        return new CMISQLDocumentPageProvider(session, query,
                "Ent.cmis:objectId");
    }

    public List<DocumentModel> suggestEntity(CoreSession session,
            String keywords, String type, int maxSuggestions)
            throws ClientException {
        if (type == null) {
            type = "Entity";
        }
        String q = String.format("SELECT * FROM %s WHERE ecm:fulltext = '%s'"
                + " ORDER BY entity:popularity DESC, dc:title" + " LIMIT %d",
                type, keywords.replace("'", "\'"), maxSuggestions);
        return session.query(q);
    }

    public List<DocumentModel> suggestDocument(CoreSession session,
            String keywords, String type, int maxSuggestions)
            throws ClientException {
        if (type == null) {
            type = "Document";
        }
        String query = String.format(
                "SELECT cmis:objectId, SCORE() relevance FROM %s "
                        + "WHERE CONTAINS('%s') " + "ORDER BY relevance", type,
                keywords.replace("'", "\'"));
        PageProvider<DocumentModel> provider = new CMISQLDocumentPageProvider(
                session, query, "cmis:objectId");
        provider.setPageSize(maxSuggestions);
        return provider.getCurrentPage();
    }

}
