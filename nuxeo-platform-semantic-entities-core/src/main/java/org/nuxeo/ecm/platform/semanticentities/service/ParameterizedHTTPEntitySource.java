/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntitySource;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Abstract base class to be used by all contributions to the RemoteEntityServiceImpl service. Factorize common mapping
 * logic and offer public methods to help the service set parameters from the descriptor.
 */
public abstract class ParameterizedHTTPEntitySource implements RemoteEntitySource {

    private static final Log log = LogFactory.getLog(ParameterizedHTTPEntitySource.class);

    public static final String OWL_THING = "http://www.w3.org/2002/07/owl#Thing";

    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    protected RemoteEntitySourceDescriptor descriptor;

    protected HttpClient httpClient;

    public void setDescriptor(RemoteEntitySourceDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public boolean canDereference(URI remoteEntity) {
        return remoteEntity.toString().startsWith(descriptor.getUriPrefix());
    }

    protected void initHttpClient() {
        // Create and initialize a scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        HttpParams params = new BasicHttpParams();
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

        httpClient = new DefaultHttpClient(cm, params);
    }

    protected InputStream doHttpGet(URI uri, String accept) throws IOException {
        HttpGet get = new HttpGet(uri);
        try {
            if (accept != null) {
                get.setHeader("Accept", accept);
            }
            HttpResponse response = httpClient.execute(get);
            if (response.getStatusLine().getStatusCode() == 200) {
                return response.getEntity().getContent();
            } else {
                String errorMsg = String.format("Error resolving '%s' : ", uri);
                errorMsg += response.getStatusLine().toString();
                throw new IOException(errorMsg);
            }
        } catch (ClientProtocolException e) {
            get.abort();
            throw e;
        } catch (IOException e) {
            get.abort();
            throw e;
        }
    }

    protected InputStream doHttpPost(URI uri, String accept, String contentType, String payload) throws IOException {
        HttpPost post = new HttpPost(uri);
        try {
            if (accept != null) {
                post.setHeader("Accept", accept);
            }
            if (contentType != null) {
                post.setHeader("Content-Type", contentType);
            }
            if (payload != null) {
                HttpEntity entity = new StringEntity(payload, "UTF-8");
                post.setEntity(entity);
            }
            HttpResponse response = httpClient.execute(post);
            if (response.getStatusLine().getStatusCode() == 200) {
                return response.getEntity().getContent();
            } else {
                String errorMsg = String.format("Error querying '%s' with payload '%s': ", uri, payload);
                errorMsg += response.getStatusLine().toString();
                throw new IOException(errorMsg);
            }
        } catch (ClientProtocolException e) {
            post.abort();
            throw e;
        } catch (IOException e) {
            post.abort();
            throw e;
        }
    }

    @Override
    public boolean canSuggestRemoteEntity() {
        return true;
    }

    // RDF specific handling

    @Override
    @SuppressWarnings("unchecked")
    public boolean dereferenceIntoFromModel(DocumentModel localEntity, URI remoteEntity, Model rdfModel,
            boolean override, boolean lazyResourceFetch) throws DereferencingException {

        // check that the remote entity has a type that is compatible with
        // the local entity document model
        Collection<String> possibleTypes = extractMappedTypesFromModel(remoteEntity, rdfModel);
        if (!possibleTypes.contains(localEntity.getType())) {
            throw new DereferencingException(String.format("Remote entity '%s' can be mapped to types:"
                    + " ('%s') but not to '%s'", remoteEntity, StringUtils.join(possibleTypes, "', '"),
                    localEntity.getType()));
        }

        Resource resource = rdfModel.getResource(remoteEntity.toString());

        // special handling for the entity:sameas property
        List<String> samesas = new ArrayList<String>();
        List<String> sameasDisplayLabel = new ArrayList<String>();
        try {
            Property sameasProp = localEntity.getProperty("entity:sameas");
            if (sameasProp.getValue() != null) {
                samesas.addAll(sameasProp.getValue(List.class));
            }
            Property sameasDisplayLabelProp = localEntity.getProperty("entity:sameasDisplayLabel");
            if (sameasDisplayLabelProp.getValue() != null) {
                sameasDisplayLabel.addAll(sameasDisplayLabelProp.getValue(List.class));
            }
            if (!samesas.contains(remoteEntity.toString())) {
                samesas.add(remoteEntity.toString());
                localEntity.setPropertyValue("entity:sameas", (Serializable) samesas);

                String titlePropUri = descriptor.getMappedProperties().get("dc:title");
                String label = localEntity.getTitle();
                label = label != null ? label : "Missing label";
                if (titlePropUri != null) {
                    String labelFromRDF = (String) readDecodedLiteral(rdfModel, resource, titlePropUri,
                            StringType.INSTANCE, "en");
                    label = labelFromRDF != null ? labelFromRDF : label;
                }
                sameasDisplayLabel.add(label);
                localEntity.setPropertyValue("entity:sameasDisplayLabel", (Serializable) sameasDisplayLabel);
            }
        } catch (PropertyNotFoundException e) {
            throw new DereferencingException(e);
        }

        HashMap<String, String> mapping = new HashMap<String, String>(descriptor.getMappedProperties());
        // as sameas has a special handling, remove it from the list of
        // properties to synchronize the generic way
        mapping.remove("entity:sameas");

        // generic handling of mapped properties
        for (Entry<String, String> mappedProperty : mapping.entrySet()) {
            String localPropertyName = mappedProperty.getKey();
            String remotePropertyUri = mappedProperty.getValue();
            try {
                Property localProperty = localEntity.getProperty(localPropertyName);
                Type type = localProperty.getType();
                if (type.isListType()) {
                    // only synchronize string lists right now
                    List<String> newValues = new ArrayList<String>();
                    if (localProperty.getValue() != null) {
                        newValues.addAll(localProperty.getValue(List.class));
                    }
                    if (override) {
                        newValues.clear();
                    }
                    for (String value : readStringList(rdfModel, resource, remotePropertyUri)) {
                        if (!newValues.contains(value)) {
                            newValues.add(value);
                        }
                    }
                    localEntity.setPropertyValue(localPropertyName, (Serializable) newValues);
                } else {
                    if (localProperty.getValue() == null || "".equals(localProperty.getValue()) || override) {
                        if (type.isComplexType() && "content".equals(type.getName())) {
                            if (lazyResourceFetch) {
                                // TODO: store the resource and property info in
                                // a DocumentModel context data entry to be used
                                // later by the entity serializer
                            } else {
                                Serializable linkedResource = (Serializable) readLinkedResource(rdfModel, resource,
                                        remotePropertyUri);
                                if (linkedResource != null) {
                                    localEntity.setPropertyValue(localPropertyName, linkedResource);
                                }
                            }
                        } else {
                            Serializable literal = readDecodedLiteral(rdfModel, resource, remotePropertyUri, type, "en");
                            if (literal != null) {
                                localEntity.setPropertyValue(localPropertyName, literal);
                            }
                        }
                    }
                }
            } catch (PropertyException e) {
                // ignore missing properties
            }
        }
        return true;
    }

    protected Serializable readDecodedLiteral(Model rdfModel, Resource resource, String remotePropertyUri, Type type,
            String requestedLang) {
        com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(remotePropertyUri);
        NodeIterator it = rdfModel.listObjectsOfProperty(resource, remoteProperty);
        while (it.hasNext()) {
            RDFNode node = it.nextNode();
            if (node.isLiteral()) {
                Literal literal = node.as(Literal.class);
                String lang = literal.getLanguage();
                if (lang == null || lang.equals("") || lang.equals(requestedLang)) {
                    Serializable decoded = (Serializable) type.decode(literal.getString());
                    if (decoded instanceof String) {
                        decoded = StringEscapeUtils.unescapeHtml((String) decoded);
                    }
                    return decoded;
                }
            }
        }
        return null;
    }

    protected Blob readLinkedResource(Model rdfModel, Resource resource, String remotePropertyUri) {
        // download depictions or other kind of linked resources
        com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(remotePropertyUri);
        NodeIterator it = rdfModel.listObjectsOfProperty(resource, remoteProperty);
        if (it.hasNext()) {
            String contentURI = it.nextNode().as(Resource.class).getURI();

            int lastSlashIndex = contentURI.lastIndexOf('/');
            String filename = null;
            if (lastSlashIndex != -1) {
                filename = contentURI.substring(lastSlashIndex + 1);
            }
            try (InputStream in = doHttpGet(URI.create(contentURI), null)) {
                if (in == null) {
                    log.warn("failed to fetch resource: " + contentURI);
                    return null;
                }
                Blob blob = Blobs.createBlob(in);
                blob.setFilename(filename);
                return blob;
            } catch (IOException e) {
                log.warn(e.getMessage());
                return null;
            }
        }
        return null;
    }

    protected List<String> readStringList(Model rdfModel, Resource resource, String remotePropertyUri) {

        com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(remotePropertyUri);
        NodeIterator it = rdfModel.listObjectsOfProperty(resource, remoteProperty);

        List<String> collectedValues = new ArrayList<String>();
        while (it.hasNext()) {
            RDFNode node = it.nextNode();
            String value = null;
            if (node.isLiteral()) {
                value = node.as(Literal.class).getString();
                value = StringEscapeUtils.unescapeHtml(value);
            } else if (node.isURIResource()) {
                value = node.as(Resource.class).getURI();
            } else {
                continue;
            }
            if (value != null && !collectedValues.contains(value)) {
                collectedValues.add(value);
            }
        }
        return collectedValues;
    }

    /**
     * @param remoteEntity URI of the remote entity
     * @param rdfModel RDF model describing the remote entity
     * @return list of local types that are compatible with the remote entity according to the type mapping
     *         configuration for this source
     */
    protected Set<String> extractMappedTypesFromModel(URI remoteEntity, Model rdfModel) {
        Resource resource = rdfModel.getResource(remoteEntity.toString());
        com.hp.hpl.jena.rdf.model.Property type = rdfModel.getProperty(RDF_TYPE);

        TreeSet<String> typeURIs = new TreeSet<String>();
        TreeSet<String> mappedLocalTypes = new TreeSet<String>();
        NodeIterator it = rdfModel.listObjectsOfProperty(resource, type);
        while (it.hasNext()) {
            RDFNode node = it.nextNode();
            if (node.isURIResource()) {
                typeURIs.add(node.as(Resource.class).getURI());
            }
        }
        Set<Entry<String, String>> typeMapping = descriptor.getMappedTypes().entrySet();
        for (Entry<String, String> typeMapEntry : typeMapping) {
            if (typeURIs.contains(typeMapEntry.getValue())) {
                mappedLocalTypes.add(typeMapEntry.getKey());
            }
        }
        mappedLocalTypes.remove("Entity");
        return mappedLocalTypes;
    }

}
