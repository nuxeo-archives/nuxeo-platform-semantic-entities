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

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

public interface RemoteEntitySource {

    /**
     * @return true if the features suggestions by name (a.k.a. entity label)
     */
    boolean canSuggestRemoteEntity();

    /**
     * @return true if one of the registered remote sources can handle the
     *         provided URI
     */
    boolean canDereference(URI remoteEntity);

    /**
     * Create a document model holding the dereferenced structure date from an
     * entity identified by a HTTP URI.
     *
     * @param session a CoreSession instance use to load an in memory
     *            DocumentModel of the right type
     * @param remoteEntity the URI of the entity to dereference
     * @return a document model holding the data (not persisted in the repo yet)
     *         or null if the remote resource does not match any remote source
     *         handler.
     */
    DocumentModel dereference(CoreSession session, URI remoteEntity)
            throws DereferencingException;

    /**
     * Dereference a remote entity into an existing document model. Only non
     * empty local fields are updated, unless {@code override} is set to {@code
     * true}.
     *
     * It is the responsibility of the method caller to save the updated
     * document model back to the repository.
     *
     * @param localEntity local document model to store a copy of the entity
     *            attribute
     * @param remoteEntity the URI of the entity to dereference
     * @param override replace non-empty local fields with values from the
     *            remote entity
     */
    void dereferenceInto(DocumentModel localEntity, URI remoteEntity,
            boolean override) throws DereferencingException;

    /**
     * Perform query on registered remote entity sources to suggests entity
     * definitions that match the name given as keywords and the requested
     * entity type.
     *
     * The caller will then typically ask the user to select one of the
     * suggestions and then dereference of the remote entity into a local copy
     * for easy off-line reuse and indexing in the local repository.
     *
     * The suggestion backend should order the results by a mix of keyword
     * relevance and popularity.
     */
    List<RemoteEntity> suggestRemoteEntity(String keywords, String type,
            int maxSuggestions) throws IOException;

}
