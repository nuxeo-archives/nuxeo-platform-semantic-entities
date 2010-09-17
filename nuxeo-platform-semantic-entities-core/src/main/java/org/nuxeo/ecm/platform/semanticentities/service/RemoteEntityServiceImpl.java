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
package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntity;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntityService;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntitySource;

/**
 * {@inheritDoc}
 *
 * Default implementation for the RemoteEntityService component. Can be used to
 * register remote entity sources for linked data dereferencing.
 */
public class RemoteEntityServiceImpl implements RemoteEntityService {

    public static final Log log = LogFactory.getLog(RemoteEntityServiceImpl.class);

    public static final String REMOTESOURCES_XP_NAME = "remoteSources";

    public List<ParameterizedRemoteEntitySource> activeSources;

    protected List<ParameterizedRemoteEntitySource> getActiveSources() {
        if (activeSources == null) {
            activeSources = new ArrayList<ParameterizedRemoteEntitySource>();
            // TODO: merge registered and enabled resources here
        }
        return activeSources;
    }

    protected RemoteEntitySource getSourceFor(URI remoteEntity) {
        for (RemoteEntitySource source : getActiveSources()) {
            if (source.canDereference(remoteEntity)) {
                return source;
            }
        }
        return null;
    }

    @Override
    public boolean canSuggestRemoteEntity() {
        for (RemoteEntitySource source : getActiveSources()) {
            if (source.canSuggestRemoteEntity()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canDereference(URI remoteEntity) {
        return getSourceFor(remoteEntity) != null;
    }

    @Override
    public DocumentModel dereference(CoreSession session, URI remoteEntity)
            throws DereferencingException {
        return getSourceFor(remoteEntity).dereference(session, remoteEntity);
    }

    @Override
    public void dereferenceInto(DocumentModel localEntity, URI remoteEntity,
            boolean override) throws DereferencingException {
        getSourceFor(remoteEntity).dereferenceInto(localEntity, remoteEntity,
                override);
    }

    /**
     * {@inheritDoc}
     *
     * This implementation aggregates all the suggestions of the sources that
     * can perform suggestions. Hence the number of aggregate suggestions might
     * be larger than {@literal maxSuggestions}
     */
    @Override
    public List<RemoteEntity> suggestRemoteEntity(String keywords, String type,
            int maxSuggestions) throws IOException {
        List<RemoteEntity> suggestions = new ArrayList<RemoteEntity>();
        for (RemoteEntitySource source : getActiveSources()) {
            if (source.canSuggestRemoteEntity()) {
                suggestions.addAll(source.suggestRemoteEntity(keywords, type,
                        maxSuggestions));
            }
        }
        return suggestions;
    }
}
