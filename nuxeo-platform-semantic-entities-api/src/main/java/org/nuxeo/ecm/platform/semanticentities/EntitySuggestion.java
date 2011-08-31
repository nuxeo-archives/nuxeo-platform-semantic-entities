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

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Data transfer object for the name lookup API of the LocalEntityService: it is
 * represent both local entities (DocumentModel) and remote entities that don't
 * have a match in the local repo represented as in memory DocumentModel or
 * dereferenceable URIs.
 */
public class EntitySuggestion implements Comparable<EntitySuggestion>,
        Serializable {

    private static final long serialVersionUID = 1L;

    public String label;

    public DocumentModel entity;

    public String type;

    public final Set<String> alternativeNames = new LinkedHashSet<String>();

    public final Set<String> remoteEntityUris = new LinkedHashSet<String>();

    public double score = 0.0;

    @SuppressWarnings("unchecked")
    public EntitySuggestion(DocumentModel entity) throws ClientException {
        this.entity = entity;
        label = entity.getTitle();
        type = entity.getType();
        remoteEntityUris.addAll(entity.getProperty("entity:sameas").getValue(
                List.class));
        alternativeNames.addAll(entity.getProperty("entity:altnames").getValue(
                List.class));
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

    public boolean isLocal() {
        return entity != null && entity.getRef() != null;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     *
     * Largest scores fist.
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
    public String getRemoteURI() {
        if (!remoteEntityUris.isEmpty()) {
            return remoteEntityUris.iterator().next();
        } else {
            return null;
        }
    }
}
