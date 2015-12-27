/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Data transfer object for the name lookup API of the LocalEntityService: it is represent both local entities
 * (DocumentModel) and remote entities that don't have a match in the local repo represented as in memory DocumentModel
 * or dereferenceable URIs.
 */
public class EntitySuggestion implements Comparable<EntitySuggestion>, Serializable {

    private static final Log log = LogFactory.getLog(EntitySuggestion.class);

    public static final String SAMEAS_URI_PROPERTY = "entity:sameas";

    public static final String SAMEAS_LABEL_PROPERTY = "entity:sameasDisplayLabel";

    public static final String ALTNAMES_PROPERTY = "entity:altnames";

    private static final long serialVersionUID = 1L;

    public String label;

    public DocumentModel entity;

    public String type;

    public final Set<String> alternativeNames = new LinkedHashSet<String>();

    public final Set<String> remoteEntityUris = new LinkedHashSet<String>();

    public double score = 0.0;

    public boolean automaticallyCreated = false;

    @SuppressWarnings("unchecked")
    public EntitySuggestion(DocumentModel entity) {
        this.entity = entity;
        label = entity.getTitle();
        type = entity.getType();
        remoteEntityUris.addAll(entity.getProperty(SAMEAS_URI_PROPERTY).getValue(List.class));
        alternativeNames.addAll(entity.getProperty(ALTNAMES_PROPERTY).getValue(List.class));
    }

    public EntitySuggestion(String label, String remoteEntityUri, String type) {
        this.label = label;
        this.type = type;
        remoteEntityUris.add(remoteEntityUri);
    }

    public EntitySuggestion withScore(double score) {
        this.score = score;
        return this;
    }

    public EntitySuggestion withAutomaticallyCreated(boolean automaticallyCreated) {
        this.automaticallyCreated = automaticallyCreated;
        return this;
    }

    public boolean isLocal() {
        return entity != null && entity.getRef() != null;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object) Largest scores fist.
     */
    @Override
    public int compareTo(EntitySuggestion o) {
        return -(int) Math.signum(score - o.score);
    }

    // public getters for Seam

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    /**
     * @return the id of the local entity or null
     */
    public String getLocalId() {
        if (isLocal()) {
            return entity.getId();
        } else {
            return null;
        }
    }

    /**
     * @return the first registered remote URI or null
     */
    public String getRemoteUri() {
        if (!remoteEntityUris.isEmpty()) {
            return remoteEntityUris.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("EntitySuggestion(%s, %s, %s)", label, getRemoteUri(), type);
    }

    /**
     * Static helper to turn a document complex property into a list of RemoteEntity instances suitable to the UI layer.
     */
    public static List<EntitySuggestion> fromDocument(DocumentModel doc) {
        String[] entityURIs = doc.getProperty(SAMEAS_URI_PROPERTY).getValue(String[].class);
        String[] entityLabels = doc.getProperty(SAMEAS_LABEL_PROPERTY).getValue(String[].class);

        List<EntitySuggestion> entities = new ArrayList<EntitySuggestion>();
        if (entityURIs.length != entityLabels.length) {
            log.warn(String.format("inconsistent linked remote entities for local entity '%s': (%s) and (%s)",
                    doc.getTitle(), StringUtils.join(entityURIs), StringUtils.join(entityLabels)));
            return entities;
        }
        for (int i = 0; i < entityURIs.length; i++) {
            entities.add(new EntitySuggestion(entityLabels[i], entityURIs[i], doc.getType()));
        }
        return entities;
    }

}
