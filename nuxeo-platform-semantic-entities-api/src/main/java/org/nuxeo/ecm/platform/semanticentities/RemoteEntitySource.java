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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.api.DocumentModel;

import com.hp.hpl.jena.rdf.model.Model;

public interface RemoteEntitySource {

    /**
     * @return true if the features suggestions by name (a.k.a. entity label)
     */
    boolean canSuggestRemoteEntity();

    /**
     * @return true if one of the registered remote sources can handle the provided URI
     */
    boolean canDereference(URI remoteEntity);

    /**
     * Introspect the referenced entity to suggest which Nuxeo types can be used to dereference this entity. The first
     * element of the list should be the type the user should expect in front facing label (e.g. the most specific).
     * Hence the set implementation should imply unicity as all set but also preserver ordering (e.g. a LinkedHashSet
     * for instance).
     *
     * @param remoteEntity the URI of the entity to dereference
     * @return an ordered set of Nuxeo Core type names that can be used to dereference the entity into a local copy
     * @throws DereferencingException
     */
    Set<String> getAdmissibleTypes(URI remoteEntity) throws DereferencingException;

    /**
     * Dereference a remote entity into an existing document model. Only non empty local fields are updated, unless
     * {@code override} is set to {@code true}. It is the responsibility of the method caller to save the updated
     * document model back to the repository.
     *
     * @param localEntity local document model to store a copy of the entity attribute
     * @param remoteEntity the URI of the entity to dereference
     * @param override replace non-empty local fields with values from the remote entity
     * @param lazyResourceFetch if true, delay the fetch of the content of referenced resources (e.g. JPEG images) to
     *            first access.
     * @return true if a suitable remote entity description was found in the source, false otherwise.
     */
    boolean dereferenceInto(DocumentModel localEntity, URI remoteEntity, boolean override, boolean lazyResourceFetch)
            throws DereferencingException;

    /**
     * Dereference a remote entity into an existing document model from a pre-fetched RDF description of the entity.
     * Only non empty local fields are updated, unless {@code override} is set to {@code true}. This is typically useful
     * for the SemanticAnalysisService that might receive pre-fetched entity link suggestion and description from the
     * enhancement engines. It is the responsibility of the method caller to save the updated document model back to the
     * repository.
     *
     * @param localEntity local document model to store a copy of the entity attribute
     * @param remoteEntity the URI of the entity to dereference
     * @param override replace non-empty local fields with values from the remote entity
     * @param lazyResourceFetch if true, delay the fetch of the content of referenced resources (e.g. JPEG images) to
     *            first access.
     * @return true if a suitable remote entity description was found in the source, false otherwise.
     */
    public boolean dereferenceIntoFromModel(DocumentModel localEntity, URI remoteEntity, Model rdfModel,
            boolean override, boolean lazyResourceFetch) throws DereferencingException;

    /**
     * Perform query on registered remote entity sources to suggests entity definitions that match the name given as
     * keywords and the requested entity type. The caller will then typically ask the user to select one of the
     * suggestions and then dereference of the remote entity into a local copy for easy off-line reuse and indexing in
     * the local repository. The suggestion backend should order the results by a mix of keyword relevance and
     * popularity.
     */
    List<EntitySuggestion> suggestRemoteEntity(String keywords, String type, int maxSuggestions) throws IOException;

}
