package org.nuxeo.ecm.platform.semanticentities;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Data transfer object for the name lookup API of the RemoteEntityService
 */
public class RemoteEntity {

    public static final String URI_PROPERTY = "uri";

    public static final String LABEL_PROPERTY = "label";

    public final String label;

    public final URI uri;

    public RemoteEntity(String label, URI uri) {
        this.label = label;
        this.uri = uri;
    }

    public RemoteEntity(String label, String uri) {
        this.label = label;
        this.uri = URI.create(uri);
    }

    /**
     * Convenience helper method to update a list of linked remote entities
     * stored in a document model as a complex property without duplicating
     * entries the same URI
     *
     * @param doc the document model to update
     * @param entitiesPropertyName property holding a list of maps with keys
     *            "label" and "uri"
     * @throws ClientException
     */
    @SuppressWarnings("unchecked")
    public void addToEntities(DocumentModel doc, String entitiesPropertyName)
            throws ClientException {
        List<Map<String, String>> entities = (List<Map<String, String>>) doc.getPropertyValue(entitiesPropertyName);
        if (entities == null) {
            entities = new ArrayList<Map<String, String>>();
        }
        String uriString = uri.toString();
        boolean foundSameUri = false;
        for (Map<String, String> oldEntity : entities) {
            if (uriString.equals(oldEntity.get(URI_PROPERTY))) {
                // update the label
                oldEntity.put(LABEL_PROPERTY, label);
                foundSameUri = true;
                break;
            }
        }
        if (!foundSameUri) {
            Map<String, String> newEntity = new HashMap<String, String>();
            newEntity.put(URI_PROPERTY, uriString);
            newEntity.put(LABEL_PROPERTY, label);
            entities.add(newEntity);
        }
        doc.setPropertyValue(entitiesPropertyName, (Serializable) entities);
    }

    /**
     * Static helper to turn a document complex property into a list of
     * RemoteEntity instances suitable to the UI layer.
     */
    @SuppressWarnings("unchecked")
    public static List<RemoteEntity> fromDocument(DocumentModel doc,
            String entitiesPropertyName) throws ClientException {
        List<Map<String, String>> entityValues = (List<Map<String, String>>) doc.getPropertyValue(entitiesPropertyName);
        List<RemoteEntity> entities = new ArrayList<RemoteEntity>();
        if (entityValues == null) {
            entityValues = Collections.emptyList();
        }
        for (Map<String, String> entityValue : entityValues) {
            entities.add(new RemoteEntity(entityValue.get(LABEL_PROPERTY),
                    entityValue.get(URI_PROPERTY)));
        }
        return entities;
    }

}
