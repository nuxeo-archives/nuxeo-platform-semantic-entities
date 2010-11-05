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

import java.util.LinkedHashSet;
import java.util.Set;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Data transfer object for the name lookup API of the LocalEntityService: it is
 * represent both local entities (DocumentModel) and remote entities that don't
 * have a match in the local repo.
 */
public class EntitySuggestion implements Comparable<EntitySuggestion> {

    public String label;

    public DocumentModel localEntity;

    public String type;

    public final Set<String> remoteEntityUris = new LinkedHashSet<String>();

    public double score = 0.0;

    public EntitySuggestion(DocumentModel localEntity) throws ClientException {
        this.localEntity = localEntity;
        this.label = localEntity.getTitle();
        this.type = localEntity.getType();
    }

    public EntitySuggestion(String label, String remoteEntityUri, String type) {
        this.label = label;
        this.remoteEntityUris.add(remoteEntityUri);
        this.type = type;
    }

    public EntitySuggestion withScore(double score) {
        this.score = score;
        return this;
    }

    public boolean isLocal() {
        return localEntity != null;
    }

    @Override
    public int compareTo(EntitySuggestion o) {
        return (int) Math.signum(score - o.score);
    }
}
