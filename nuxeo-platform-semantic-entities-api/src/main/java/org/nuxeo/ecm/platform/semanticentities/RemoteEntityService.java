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

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Service to suggest by name, lookup and fetch data by URI from entities
 * definitions hosted on remote HTTP servers using the Linked Data philosophy.
 *
 * See {@link http://en.wikipedia.org/wiki/Linked_Data }.
 */
public interface RemoteEntityService extends RemoteEntitySource {

    // The service features methods that aggregate the same operations from
    // potentially many registered resources

    /**
     * Helper API to unlink a local entity from a remote entity identified by
     * it's URI. It is the responsability of the caller to save the change in
     * back to the repository if persistence is required.
     */
    void removeSameAsLink(DocumentModel localEntity, URI remoteEntityURI)
            throws ClientException;
}
