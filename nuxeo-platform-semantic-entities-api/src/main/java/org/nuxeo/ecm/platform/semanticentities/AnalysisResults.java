package org.nuxeo.ecm.platform.semanticentities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;

/**
 * Data transfer object with a builder API to gather the outcome of a semantic
 * analysis.
 */
public class AnalysisResults {

    public final Map<String, Object> properties = new HashMap<String, Object>();

    public final List<OccurrenceGroup> groups = new ArrayList<OccurrenceGroup>();

    public AnalysisResults() {
    }

    public static AnalysisResults newInstance() {
        return new AnalysisResults();
    }

    public AnalysisResults withProperty(String path, Object value) {
        properties.put(path, value);
        return this;
    }

    public AnalysisResults withOccurrenceGroup(OccurrenceGroup group) {
        groups.add(group);
        return this;
    }

    public void savePropertiesToDocument(CoreSession session, DocumentModel doc)
            throws PropertyException, ClientException {
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            if (property.getValue() != null) {
                doc.setPropertyValue(property.getKey(),
                        (Serializable) property.getValue());
            }
        }
        if (!properties.isEmpty()) {
            session.saveDocument(doc);
        }

    }

    public boolean isEmpty() {
        return properties.isEmpty() && groups.isEmpty();
    }

}
