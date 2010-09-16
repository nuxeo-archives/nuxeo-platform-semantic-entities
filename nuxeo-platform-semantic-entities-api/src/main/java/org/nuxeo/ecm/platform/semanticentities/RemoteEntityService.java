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

/**
 * Service to suggest by name, lookup and fetch data by URI from entities
 * definitions hosted on remote HTTP servers using the Linked Data philosophy.
 *
 * See {@link http://en.wikipedia.org/wiki/Linked_Data }.
 */
public interface RemoteEntityService extends RemoteEntitySource {

    // The service features methods that aggregate the same operations from
    // potentially many registered resources

}
