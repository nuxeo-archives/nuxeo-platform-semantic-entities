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
package org.nuxeo.ecm.platform.semanticentities.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object to suggest the creation of entities that do not yet
 * exist in the local DB along with the list of mentions
 *
 * @author ogrisel
 *
 */
public class OccurrenceGroup {

    public String name;

    public String type;

    public final List<OccurrenceInfo> occurrences = new ArrayList<OccurrenceInfo>();

    public OccurrenceGroup(String name, String type) {
        this.name = name;
        this.type = type;
    }
}
