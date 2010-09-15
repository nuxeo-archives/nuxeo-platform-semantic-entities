package org.nuxeo.ecm.platform.semanticentities;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Data transfer object for the name lookup API of the RemoteEntityService
 */
public class RemoteEntity {

    public final String label;

    public final URI uri;

    public RemoteEntity(String label, URI uri) {
        this.label = label;
        this.uri = uri;
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
            if (uriString.equals(oldEntity.get("uri"))) {
                // update the label
                oldEntity.put("label", label);
                foundSameUri = true;
                break;
            }
        }
        if (!foundSameUri) {
            Map<String, String> newEntity = new HashMap<String, String>();
            newEntity.put("uri", uriString);
            newEntity.put("label", label);
            entities.add(newEntity);
        }
        doc.setPropertyValue(entitiesPropertyName, (Serializable) entities);
    }

}
