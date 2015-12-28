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
package org.nuxeo.ecm.platform.semanticentities.adapter;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;

/**
 * Data Transfer Object to suggest the creation of entities that do not yet exist in the local DB along with the list of
 * mentions
 *
 * @author ogrisel
 */
public class OccurrenceGroup implements Comparable<OccurrenceGroup> {

    public String name;

    public String type;

    public final List<OccurrenceInfo> occurrences = new ArrayList<OccurrenceInfo>();

    public final List<EntitySuggestion> entitySuggestions = new ArrayList<EntitySuggestion>();

    protected boolean hasPrefetchedSuggestions = false;

    public OccurrenceGroup(String name, String type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("OccurrenceGroup(\"%s\", \"%s\")", name, type);
    }

    public void setPretchedSuggestions(List<EntitySuggestion> entitySuggestions) {
        entitySuggestions.clear();
        entitySuggestions.addAll(entitySuggestions);
        hasPrefetchedSuggestions = true;
    }

    public boolean hasPrefetchedSuggestions() {
        return hasPrefetchedSuggestions;
    }

    @Override
    public int compareTo(OccurrenceGroup o) {
        return (type + " " + name).compareTo(o.type + " " + o.name);
    }
}
