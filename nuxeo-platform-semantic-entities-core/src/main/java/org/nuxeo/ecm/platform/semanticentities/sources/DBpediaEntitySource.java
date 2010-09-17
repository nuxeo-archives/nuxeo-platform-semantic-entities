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
package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntity;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedRemoteEntitySource;

/**
 * Implementation of the RemoteEntitySource interface that is able to suggest
 * DBpedia entities by name using the http://lookup.dbpedia.org RESTful service
 * and dereference DBpedia URIs using the official DBpedia sparql endpoint.
 */
public class DBpediaEntitySource extends ParameterizedRemoteEntitySource {

    @Override
    public boolean canSuggestRemoteEntity() {
        // TODO: implement the version that can
        return false;
    }

    @Override
    public DocumentModel dereference(CoreSession session, URI remoteEntity)
            throws DereferencingException {
        return null;
    }

    @Override
    public void dereferenceInto(DocumentModel localEntity, URI remoteEntity,
            boolean override) throws DereferencingException {
    }

    @Override
    public List<RemoteEntity> suggestRemoteEntity(String keywords, String type,
            int maxSuggestions) throws IOException {
        return null;
    }

}
