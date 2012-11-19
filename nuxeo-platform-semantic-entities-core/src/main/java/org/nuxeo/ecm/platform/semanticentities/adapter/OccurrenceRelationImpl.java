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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;

public class OccurrenceRelationImpl implements OccurrenceRelation {

    protected DocumentModel doc;

    public OccurrenceRelationImpl(DocumentModel doc) {
        this.doc = doc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OccurrenceInfo> getOccurrences() throws ClientException {
        List<OccurrenceInfo> occurrences = new ArrayList<OccurrenceInfo>();
        List<Map<String, Serializable>> occMaps = doc.getProperty(
                "occurrence:quotes").getValue(List.class);
        for (Map<String, Serializable> occMap : occMaps) {
            OccurrenceInfo info = new OccurrenceInfo(
                    occMap.get("text").toString(),
                    ((Long) occMap.get("startPos")).intValue(),
                    ((Long) occMap.get("endPos")).intValue());
            occurrences.add(info);
        }
        return occurrences;
    }

    @Override
    public List<OccurrenceInfo> getOccurrences(int maxOccurrences) throws ClientException {
        List<OccurrenceInfo> occurrences = getOccurrences();
        if (occurrences.size() <= maxOccurrences) {
            return occurrences;
        }
        return occurrences.subList(0, maxOccurrences);
    }

    @Override
    public void addOccurrences(List<OccurrenceInfo> occurrences) throws ClientException {
        Set<OccurrenceInfo> dedupedOccurrences = new LinkedHashSet<OccurrenceInfo>(
                getOccurrences());
        dedupedOccurrences.addAll(occurrences);
        List<Map<String, Serializable>> quotes = new ArrayList<Map<String, Serializable>>();
        for (OccurrenceInfo info : dedupedOccurrences) {
            quotes.add(info.asQuoteyMap());
        }
        doc.setPropertyValue("occurrence:quotes", (Serializable) quotes);
    }

    @Override
    public void setOccurrences(List<OccurrenceInfo> occurrences)
            throws ClientException {
        List<Map<String, Serializable>> newQuotes = new ArrayList<Map<String, Serializable>>();
        // use a temporary LinkedHashSet to remove any dupe without altering the
        // ordering
        for (OccurrenceInfo info : new LinkedHashSet<OccurrenceInfo>(
                occurrences)) {
            newQuotes.add(info.asQuoteyMap());
        }
        doc.setPropertyValue("occurrence:quotes", (Serializable) newQuotes);
    }

    @Override
    public DocumentRef getSourceDocumentRef() throws ClientException {
        Object source = doc.getPropertyValue("relation:source");
        if (source != null) {
            return new IdRef(source.toString());
        }
        return null;
    }

    @Override
    public DocumentRef getTargetEntityRef() throws ClientException {
        Object target = doc.getPropertyValue("relation:target");
        if (target != null) {
            return new IdRef(target.toString());
        }
        return null;
    }

    @Override
    public DocumentModel getOccurrenceDocument() {
        return doc;
    }

    @Override
    public DocumentModel getTargetEntity() throws ClientException {
        return doc.getCoreSession().getDocument(getTargetEntityRef());
    }

    @Override
    public DocumentModel getSourceDocument() throws ClientException {
        return doc.getCoreSession().getDocument(getSourceDocumentRef());
    }

}
