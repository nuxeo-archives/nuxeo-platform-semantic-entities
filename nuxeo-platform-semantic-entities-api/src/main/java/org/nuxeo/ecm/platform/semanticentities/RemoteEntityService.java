/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
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

import java.net.URI;

import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Service to suggest by name, lookup and fetch data by URI from entities definitions hosted on remote HTTP servers
 * using the Linked Data philosophy. See {@link http://en.wikipedia.org/wiki/Linked_Data }.
 */
public interface RemoteEntityService extends RemoteEntitySource {

    // The service features methods that aggregate the same operations from
    // potentially many registered resources

    /**
     * Helper API to unlink a local entity from a remote entity identified by it's URI. It is the responsability of the
     * caller to save the change in back to the repository if persistence is required.
     */
    void removeSameAsLink(DocumentModel localEntity, URI remoteEntityURI);
}
