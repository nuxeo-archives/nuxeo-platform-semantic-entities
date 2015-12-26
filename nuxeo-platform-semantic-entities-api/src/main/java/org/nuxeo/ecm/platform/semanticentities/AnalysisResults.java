/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.semanticentities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;

/**
 * Data transfer object with a builder API to gather the outcome of a semantic analysis.
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

    public void savePropertiesToDocument(CoreSession session, DocumentModel doc) throws PropertyException {
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            if (property.getValue() != null) {
                doc.setPropertyValue(property.getKey(), (Serializable) property.getValue());
            }
        }
        // Avoid triggering analysis loops by event listeners
        doc.getContextData().put(SemanticAnalysisService.SKIP_SEMANTIC_ANALYSIS, Boolean.TRUE);
        if (!properties.isEmpty()) {
            session.saveDocument(doc);
        }

    }

    public boolean isEmpty() {
        return properties.isEmpty() && groups.isEmpty();
    }

}
