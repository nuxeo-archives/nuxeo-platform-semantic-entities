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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntityService;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntitySource;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * {@inheritDoc} Default implementation for the RemoteEntityService component. Can be used to register remote entity
 * sources for linked data dereferencing.
 */
public class RemoteEntityServiceImpl extends DefaultComponent implements RemoteEntityService {

    private static final Log log = LogFactory.getLog(RemoteEntityServiceImpl.class);

    public static final String REMOTESOURCES_XP_NAME = "remoteSources";

    protected final List<RemoteEntitySourceDescriptor> registeredSourceDescriptors = new ArrayList<RemoteEntitySourceDescriptor>();

    protected HashMap<String, ParameterizedHTTPEntitySource> activeSources;

    protected HashMap<String, ParameterizedHTTPEntitySource> getActiveSources() {
        if (activeSources == null) {
            activeSources = new LinkedHashMap<String, ParameterizedHTTPEntitySource>();
            for (RemoteEntitySourceDescriptor descriptor : registeredSourceDescriptors) {
                String name = descriptor.getName();
                if (!descriptor.isEnabled() && activeSources.containsKey(name)) {
                    activeSources.remove(name);
                } else {
                    activeSources.put(name, descriptor.getEntitySource());
                }
            }
        }
        return activeSources;
    }

    protected RemoteEntitySource getSourceFor(URI remoteEntity) {
        for (RemoteEntitySource source : getActiveSources().values()) {
            if (source.canDereference(remoteEntity)) {
                return source;
            }
        }
        return null;
    }

    /*
     * Extension point contribution API
     */

    @Override
    public void registerExtension(Extension extension) {
        if (extension.getExtensionPoint().equals(REMOTESOURCES_XP_NAME)) {
            Object[] contribs = extension.getContributions();
            for (Object contrib : contribs) {
                if (contrib instanceof RemoteEntitySourceDescriptor) {
                    registerRemoteEntitySourceDescriptor((RemoteEntitySourceDescriptor) contrib, extension);
                }
            }
        }
    }

    protected void registerRemoteEntitySourceDescriptor(RemoteEntitySourceDescriptor descriptor, Extension extension) {
        descriptor.initializeInContext(extension.getContext());
        registeredSourceDescriptors.add(descriptor);
        // invalidate the cache of activeSources
        activeSources = null;
        log.info(String.format("Registered entity source '%s' with class '%s'.", descriptor.getName(),
                descriptor.getClassName()));
    }

    @Override
    public void unregisterExtension(Extension extension) {
        if (extension.getExtensionPoint().equals(REMOTESOURCES_XP_NAME)) {
            Object[] contribs = extension.getContributions();
            for (Object contrib : contribs) {
                if (contrib instanceof RemoteEntitySourceDescriptor) {
                    unregisterRemoteEntitySourceDescriptor((RemoteEntitySourceDescriptor) contrib, extension);
                }
            }
        }
    }

    protected void unregisterRemoteEntitySourceDescriptor(RemoteEntitySourceDescriptor descriptor, Extension extension) {
        int index = registeredSourceDescriptors.lastIndexOf(descriptor);
        if (index != -1) {
            RemoteEntitySourceDescriptor removed = registeredSourceDescriptors.remove(index);
            activeSources = null;
            log.info(String.format("Unregistered entity source '%s' with class '%s'.", removed.getName(),
                    removed.getClassName()));
        } else {
            log.warn(String.format("No registered remote source under name '%s'", descriptor.getName()));
        }
    }

    /*
     * API for the RemoteEntityService interface
     */

    @Override
    public boolean canSuggestRemoteEntity() {
        for (RemoteEntitySource source : getActiveSources().values()) {
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
    public boolean dereferenceInto(DocumentModel localEntity, URI remoteEntity, boolean override,
            boolean lazyResourceFetch) throws DereferencingException {
        RemoteEntitySource source = getSourceFor(remoteEntity);
        if (source == null) {
            return false;
        }
        log.debug(String.format("Dereferencing '%s' from source '%s'", remoteEntity, source.getClass().getName()));
        return source.dereferenceInto(localEntity, remoteEntity, override, lazyResourceFetch);
    }

    @Override
    public boolean dereferenceIntoFromModel(DocumentModel localEntity, URI remoteEntity, Model rdfModel,
            boolean override, boolean lazyResourceFetch) throws DereferencingException {
        RemoteEntitySource source = getSourceFor(remoteEntity);
        if (source == null) {
            return false;
        }
        log.debug(String.format("Dereferencing '%s' from source '%s' and prefetched model.", remoteEntity,
                source.getClass().getName()));
        return source.dereferenceIntoFromModel(localEntity, remoteEntity, rdfModel, override, lazyResourceFetch);
    }

    /**
     * {@inheritDoc} This implementation aggregates all the suggestions of the sources that can perform suggestions.
     * Hence the number of aggregate suggestions might be larger than {@literal maxSuggestions}
     */
    @Override
    public List<EntitySuggestion> suggestRemoteEntity(String keywords, String type, int maxSuggestions)
            throws IOException {
        List<EntitySuggestion> suggestions = new ArrayList<EntitySuggestion>();
        for (RemoteEntitySource source : getActiveSources().values()) {
            if (source.canSuggestRemoteEntity()) {
                suggestions.addAll(source.suggestRemoteEntity(keywords, type, maxSuggestions));
            }
        }
        log.debug(String.format("Entity Suggestions for '%s' and type '%s': [%s]", keywords, type,
                StringUtils.join(suggestions.toArray(), ", ")));
        return suggestions;
    }

    @Override
    public Set<String> getAdmissibleTypes(URI remoteEntity) throws DereferencingException {
        Set<String> types = new TreeSet<String>();
        for (RemoteEntitySource source : getActiveSources().values()) {
            if (source.canDereference(remoteEntity)) {
                types.addAll(source.getAdmissibleTypes(remoteEntity));
            }
        }
        return types;
    }

    @Override
    public void removeSameAsLink(DocumentModel doc, URI uriToRemove) {
        if (doc.getPropertyValue(EntitySuggestion.SAMEAS_URI_PROPERTY) == null) {
            return;
        }
        String uriAsString = uriToRemove.toString();
        ArrayList<String> filteredURIs = new ArrayList<String>();
        ArrayList<String> filteredLabels = new ArrayList<String>();
        String[] oldURIs = doc.getProperty(EntitySuggestion.SAMEAS_URI_PROPERTY).getValue(String[].class);
        String[] oldLabels = doc.getProperty(EntitySuggestion.SAMEAS_LABEL_PROPERTY).getValue(String[].class);

        boolean changed = false;
        for (int i = 0; i < oldURIs.length; i++) {
            if (uriAsString.equals(oldURIs[i])) {
                changed = true;
            } else {
                filteredURIs.add(oldURIs[i]);
                filteredLabels.add(oldLabels[i]);
            }
        }
        if (changed) {
            doc.setPropertyValue(EntitySuggestion.SAMEAS_URI_PROPERTY, filteredURIs);
            doc.setPropertyValue(EntitySuggestion.SAMEAS_LABEL_PROPERTY, filteredLabels);
        }
    }
}
