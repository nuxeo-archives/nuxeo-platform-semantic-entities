/*
 * (C) Copyright 2012 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.semanticentities.listener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.runtime.api.Framework;

/**
 * Normalize the names of the entity for better suggestions.
 */
public class EntityNameNormalizerListener implements EventListener {

    @SuppressWarnings("unchecked")
    @Override
    public void handleEvent(Event event) throws ClientException {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc.hasSchema("entity")) {
            if (doc.getPropertyValue("entity:normalizednames") != null
                    && !doc.getProperty("dc:title").isDirty()
                    && !doc.getProperty("entity:altnames").isDirty()) {
                // nothing to update
                return;
            }
            Set<String> names = new LinkedHashSet<String>();
            Set<String> normalized = new LinkedHashSet<String>();
            names.add(doc.getTitle());
            if (doc.getPropertyValue("entity:altnames") != null) {
                names.addAll(doc.getProperty("entity:altnames").getValue(
                        List.class));
            }
            LocalEntityService entityService = Framework.getLocalService(LocalEntityService.class);
            for (String name : names) {
                normalized.add(entityService.normalizeName(name));
            }
            doc.setPropertyValue("entity:normalizednames", normalized.toArray());
        }
    }

}
