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

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

public interface RemoteEntityService {

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
    public DocumentModel dereference(CoreSession session, URI remoteEntity) throws DereferencingException;

}
