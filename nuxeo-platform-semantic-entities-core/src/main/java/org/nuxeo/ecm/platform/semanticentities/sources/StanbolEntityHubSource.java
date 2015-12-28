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
package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedHTTPEntitySource;
import org.nuxeo.ecm.platform.semanticentities.service.RemoteEntitySourceDescriptor;

/**
 * Implementation of the RemoteEntitySource interface from the HTTP endpoint of the EntityHub of a Stanbol instance.
 */
public class StanbolEntityHubSource extends ParameterizedHTTPEntitySource {

    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    private static final Log log = LogFactory.getLog(StanbolEntityHubSource.class);

    protected final ObjectMapper mapper = new ObjectMapper();

    protected String endpointURL;

    public StanbolEntityHubSource() {
        initHttpClient();
    }

    @Override
    public synchronized void setDescriptor(RemoteEntitySourceDescriptor descriptor) {
        this.descriptor = descriptor;
        endpointURL = descriptor.getParameters().get("stanbolURL");
        if ("${org.nuxeo.ecm.platform.semanticentities.stanbolUrl}".equals(endpointURL)) {
            // no property defined, use some default value instead
            endpointURL = "https://stanbol.demo.nuxeo.com";
        }
        if (endpointURL == null || endpointURL.isEmpty()) {
            throw new RuntimeException("stanbolURL parameter is missing for the" + " StanbolEntityHubSource ");
        }
        if (!endpointURL.endsWith("/")) {
            endpointURL += "/";
        }
        endpointURL += "entityhub/";
        String site = descriptor.getParameters().get("site");
        if (site != null) {
            if ("*".equals(site)) {
                endpointURL += "sites/";
            } else {
                endpointURL += "site/" + site + "/";
            }
        }
        log.info(String.format("Configured '%s' to endpoint: '%s'", this.getClass().getName(), endpointURL));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> fetchJSONDescription(URI remoteEntity) throws JsonParseException,
            JsonMappingException, IOException {
        // TODO: make the format configurable and upgrade to JSON-LD once
        // the spec is stabilizing a bit

        // force a %-encoding of the URI that will be passed as a query
        // parameter since the JAX-RS server will decode it (once) while
        // UriBuilder will refuse 'double' encode occurrences of % followed by
        // 2 consecutive hexa digits.
        String encodedResourceUri = URLEncoder.encode(remoteEntity.toString(), "UTF-8");
        URI resourceUri = UriBuilder.fromPath(endpointURL).path("entity").queryParam("id", encodedResourceUri).build();
        return mapper.readValue(doHttpGet(resourceUri, "application/json"), Map.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getAdmissibleTypes(URI remoteEntity) throws DereferencingException {
        Map<String, Object> representation;
        try {
            representation = (Map<String, Object>) fetchJSONDescription(remoteEntity).get("representation");
        } catch (JsonParseException e) {
            throw new DereferencingException(e);
        } catch (JsonMappingException e) {
            throw new DereferencingException(e);
        } catch (IOException e) {
            throw new DereferencingException(e);
        }
        return getAdmissibleTypes(representation);
    }

    @SuppressWarnings("unchecked")
    protected Set<String> getAdmissibleTypes(Map<String, Object> jsonRepresentation) throws DereferencingException {
        try {
            List<Map<String, String>> typeInfos = (List<Map<String, String>>) jsonRepresentation.get(RDF_TYPE);
            if (typeInfos == null) {
                log.warn("Missing type information in JSON description for " + jsonRepresentation.get("id"));
                return Collections.emptySet();
            }
            Set<String> admissibleTypes = new TreeSet<String>();
            Map<String, String> reverseTypeMapping = descriptor.getReverseMappedTypes();
            for (Map<String, String> typeInfo : typeInfos) {
                String localType = reverseTypeMapping.get(typeInfo.get("value"));
                if (localType != null && !"Entity".equals(localType)) {
                    admissibleTypes.add(localType);
                }
            }
            return admissibleTypes;
        } catch (Exception e) {
            throw new DereferencingException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean dereferenceInto(DocumentModel localEntity, URI remoteEntity, boolean override,
            boolean lazyResourceFetch) throws DereferencingException {
        Map<String, Object> representation = Collections.emptyMap();
        try {
            Map<String, Object> jsonDescription = fetchJSONDescription(remoteEntity);
            representation = (Map<String, Object>) jsonDescription.get("representation");
            if (representation == null) {
                throw new DereferencingException("Invalid JSON response from Stanbol server:"
                        + " missing 'representation' key: " + mapper.writeValueAsString(jsonDescription));
            }
            Set<String> possibleTypes = getAdmissibleTypes(representation);
            if (!possibleTypes.contains(localEntity.getType())) {
                throw new DereferencingException(String.format("Remote entity '%s' can be mapped to types:"
                        + " ('%s') but not to '%s'", remoteEntity, StringUtils.join(possibleTypes, "', '"),
                        localEntity.getType()));
            }
            // special handling for the entity:sameas property
            // XXX: the following code should be factorized somewhere
            List<String> samesas = new ArrayList<String>();
            List<String> sameasDisplayLabel = new ArrayList<String>();
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
                    String labelFromRDF = readDecodedLiteral(representation, titlePropUri, StringType.INSTANCE, "en").toString();
                    label = labelFromRDF != null ? labelFromRDF : label;
                }
                sameasDisplayLabel.add(label);
                localEntity.setPropertyValue("entity:sameasDisplayLabel", (Serializable) sameasDisplayLabel);
            }
        } catch (DereferencingException e) {
            throw e;
        } catch (Exception e) {
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
                    for (String value : readStringList(representation, remotePropertyUri)) {
                        if (!newValues.contains(value)) {
                            newValues.add(value);
                        }
                    }
                    localEntity.setPropertyValue(localPropertyName, (Serializable) newValues);
                } else {
                    if (localProperty.getValue() == null || "".equals(localProperty.getValue()) || override) {
                        if (type.isComplexType() && "content".equals(type.getName())) {
                            if (lazyResourceFetch) {
                                // TODO: store the resource and property
                                // info in a DocumentModel context data entry to
                                // be used later by the entity serializer
                            } else {
                                Serializable linkedResource = readLinkedResource(representation, remotePropertyUri);
                                if (linkedResource != null) {
                                    localEntity.setPropertyValue(localPropertyName, linkedResource);
                                }
                            }
                        } else {
                            Serializable literal = readDecodedLiteral(representation, remotePropertyUri, type, "en");
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

    @SuppressWarnings("unchecked")
    protected Serializable readLinkedResource(Map<String, Object> jsonRepresentation, String propertyUri) {
        // download depictions or other kind of linked resources
        List<Map<String, String>> propInfos = (List<Map<String, String>>) jsonRepresentation.get(propertyUri);
        if (propInfos == null) {
            return null;
        }
        for (Map<String, String> propInfo : propInfos) {
            String contentURI = propInfo.get("value");
            if (contentURI.endsWith(".svg")) {
                // hardcoded skip for vectorial depictions
                return null;
            }
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
                return (Serializable) blob;
            } catch (IOException e) {
                // DBpedia links to commons.wikimedia.org hosted resources are
                // not always up to date, skip them without crashing
                log.warn(e.getMessage());
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected List<String> readStringList(Map<String, Object> jsonRepresentation, String propertyUri) {
        Set<String> values = new LinkedHashSet<String>();
        List<Map<String, String>> propInfos = (List<Map<String, String>>) jsonRepresentation.get(propertyUri);
        for (Map<String, String> propInfo : propInfos) {
            String value = propInfo.get("value");
            if (value != null) {
                values.add(value);
            }
        }
        return new ArrayList<String>(values);
    }

    @SuppressWarnings("unchecked")
    protected Serializable readDecodedLiteral(Map<String, Object> jsonRepresentation, String propertyUri, Type type,
            String filterLang) {
        List<Map<String, String>> propInfos = (List<Map<String, String>>) jsonRepresentation.get(propertyUri);
        if (propInfos == null) {
            return null;
        }
        Serializable defaultLiteralValue = null;
        for (Map<String, String> propInfo : propInfos) {
            String lang = propInfo.get("xml:lang");
            if (lang == null) {
                String value = propInfo.get("value");
                defaultLiteralValue = (Serializable) type.decode(value);
                if (defaultLiteralValue instanceof String) {
                    defaultLiteralValue = StringEscapeUtils.unescapeHtml((String) defaultLiteralValue);
                }
            }
            if (lang != null && !filterLang.equals(lang)) {
                continue;
            }
            String value = propInfo.get("value");
            Serializable decoded = (Serializable) type.decode(value);
            if (decoded instanceof String) {
                decoded = StringEscapeUtils.unescapeHtml((String) decoded);
            }
            return decoded;
        }
        return defaultLiteralValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<EntitySuggestion> suggestRemoteEntity(String keywords, String type, int maxSuggestions)
            throws IOException {
        // build a field query on the entity hub
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        List<Map<String, String>> constraints = new ArrayList<Map<String, String>>();
        String namePropertyUri = descriptor.getMappedProperties().get("dc:title");
        Map<String, String> nameTextConstraint = new LinkedHashMap<String, String>();
        nameTextConstraint.put("type", "text");
        nameTextConstraint.put("field", namePropertyUri);
        nameTextConstraint.put("text", keywords);
        constraints.add(nameTextConstraint);
        if (type != null) {
            String remoteType = descriptor.getMappedTypes().get(type);
            if (remoteType == null) {
                return Collections.emptyList();
            }
            Map<String, String> typeReferenceConstraint = new LinkedHashMap<String, String>();
            typeReferenceConstraint.put("type", "reference");
            typeReferenceConstraint.put("field", RDF_TYPE);
            typeReferenceConstraint.put("value", remoteType);
            constraints.add(typeReferenceConstraint);
        }
        List<String> selected = Arrays.asList(namePropertyUri, RDF_TYPE);
        query.put("selected", selected);
        query.put("limit", maxSuggestions);
        query.put("constraints", constraints);
        String queryPayload = mapper.writeValueAsString(query);
        InputStream responseStream = doHttpPost(URI.create(endpointURL + "query"), "application/json",
                "application/json", queryPayload);
        Map<String, Object> response = mapper.readValue(responseStream, Map.class);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null) {
            throw new DereferencingException("Stanbol EntityHub is missing a 'response' key: " + response);
        }
        List<EntitySuggestion> suggestions = new ArrayList<EntitySuggestion>();
        for (Map<String, Object> result : results) {
            Serializable nameLiteral = readDecodedLiteral(result, namePropertyUri, StringType.INSTANCE, "en");
            if (nameLiteral == null) {
                continue;
            }
            String name = nameLiteral.toString();
            String uri = result.get("id").toString();
            if (type == null) {
                Set<String> admissibleTypes = getAdmissibleTypes(result);
                if (admissibleTypes.isEmpty()) {
                    continue;
                }
                // primary type assignment is currently arbitrary: planned fix
                // it to use secondary types with "Entity" as primary types
                // instead
                type = admissibleTypes.iterator().next();
            }
            suggestions.add(new EntitySuggestion(name, uri, type));
        }
        return suggestions;
    }

}
