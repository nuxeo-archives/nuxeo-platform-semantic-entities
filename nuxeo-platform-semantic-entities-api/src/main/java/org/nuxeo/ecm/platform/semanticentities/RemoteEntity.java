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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Data transfer object for the name lookup API of the RemoteEntityService
 */
public class RemoteEntity {

    public static final String SAMEAS_URI_PROPERTY = "entity:sameas";

    public static final String SAMEAS_LABEL_PROPERTY = "entity:sameasDisplayLabel";

    public static final Log log = LogFactory.getLog(RemoteEntity.class);

    public final String label;

    public final URI uri;

    public final Set<String> admissibleTypes;

    public RemoteEntity(String label, URI uri, Set<String> admissibleTypes) {
        if (label == null || uri == null) {
            throw new IllegalArgumentException("label and uri must not be null");
        }
        this.label = label;
        this.uri = uri;
        this.admissibleTypes = admissibleTypes != null ? Collections.unmodifiableSet(admissibleTypes)
                : null;
    }

    public RemoteEntity(String label, URI uri) {
        this(label, uri, null);
    }

    public RemoteEntity(String label, String uri) {
        this(label, URI.create(uri), null);
    }

    /**
     * Static helper to turn a document complex property into a list of
     * RemoteEntity instances suitable to the UI layer.
     */
    public static List<RemoteEntity> fromDocument(DocumentModel doc)
            throws ClientException {
        String[] entityURIs = doc.getProperty(SAMEAS_URI_PROPERTY).getValue(
                String[].class);
        String[] entityLabels = doc.getProperty(SAMEAS_LABEL_PROPERTY).getValue(
                String[].class);

        List<RemoteEntity> entities = new ArrayList<RemoteEntity>();
        if (entityURIs.length != entityLabels.length) {
            log.warn(String.format(
                    "inconsistent linked remote entities for local entity '%s': (%s) and (%s)",
                    doc.getTitle(), StringUtils.join(entityURIs),
                    StringUtils.join(entityLabels)));
            return entities;
        }
        for (int i = 0; i < entityURIs.length; i++) {
            entities.add(new RemoteEntity(entityLabels[i], entityURIs[i]));
        }
        return entities;
    }

    // getters are required by JSF-EL expression resolution

    public String getLabel() {
        return label;
    }

    public URI getUri() {
        return uri;
    }

    /**
     * precomputed list of admissible types or null
     */
    public Set<String> getAdmissibleTypes() {
        return admissibleTypes;
    }

}
