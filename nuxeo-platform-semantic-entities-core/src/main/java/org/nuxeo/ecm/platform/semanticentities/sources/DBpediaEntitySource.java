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
package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntity;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedHTTPEntitySource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.RDFReader;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Implementation of the RemoteEntitySource interface that is able to suggest
 * DBpedia entities by name using the http://lookup.dbpedia.org RESTful service
 * and dereference DBpedia URIs using the official DBpedia sparql endpoint.
 * 
 * This implementation uses the SPARQL endpoint instead of HTTP GET based
 * queries since the virtuoso implementation arbitrarily truncates the entity
 * graph to around 2000 triples for entities with many properties.
 */
public class DBpediaEntitySource extends ParameterizedHTTPEntitySource {

    private static final Log log = LogFactory.getLog(DBpediaEntitySource.class);

    protected static final String SPARQL_URL_PATTERN = "%s?query=%s&format=%s";

    protected String SUGGESTION_URL_PATTERN = "http://lookup.dbpedia.org/api/search.asmx/KeywordSearch?QueryString=%s&MaxHits=%d";

    protected String SPARQL_ENDPOINT = "http://dbpedia.org/sparql";

    protected String RESULT_NODE_XPATH = "//Result";

    protected Map<URI, Model> cachedModels = new WeakHashMap<URI, Model>();

    public DBpediaEntitySource() {
        initHttpClient();
    }

    @Override
    public Set<String> getAdmissibleTypes(URI remoteEntity)
            throws DereferencingException {
        Model rdfModel = fetchRDFDescription(remoteEntity);
        return extractMappedTypesFromModel(remoteEntity, rdfModel);
    }

    @Override
    public void dereferenceInto(DocumentModel localEntity, URI remoteEntity,
            boolean override) throws DereferencingException {

        // fetch and parse the RDF payload describing the remote entity
        Model rdfModel = fetchRDFDescription(remoteEntity);

        // check that the remote entity has a type that is compatible with
        // the local entity document model
        Collection<String> possibleTypes = extractMappedTypesFromModel(
                remoteEntity, rdfModel);
        if (!possibleTypes.contains(localEntity.getType())) {
            throw new DereferencingException(String.format(
                    "Remote entity '%s' can be mapped to types:"
                            + " ('%s') but not to '%s'", remoteEntity,
                    StringUtils.join(possibleTypes, "', '"),
                    localEntity.getType()));
        }

        // fill in the localEntity document with the content of the RDF payload
        // using the property mapping defined in the source descriptor
        syncPropertiesFromModel(remoteEntity, rdfModel, localEntity, override);
    }

    protected Model fetchRDFDescription(URI remoteEntity)
            throws DereferencingException {

        Model rdfModel = cachedModels.get(remoteEntity);
        if (rdfModel != null) {
            return rdfModel;
        }

        InputStream bodyStream = null;
        try {

            StringBuilder constructPredicates = new StringBuilder();
            StringBuilder wherePredicates = new StringBuilder();

            constructPredicates.append(String.format("<%s> a ?t . ",
                    remoteEntity));
            constructPredicates.append("\n");

            wherePredicates.append(String.format("<%s> a ?t . ", remoteEntity));
            wherePredicates.append("\n");
            int i = 0;
            for (String property : new TreeSet<String>(
                    descriptor.getMappedProperties().values())) {
                constructPredicates.append(String.format("<%s> <%s> ?v%d . ",
                        remoteEntity, property, i));
                constructPredicates.append("\n");
                wherePredicates.append(String.format(
                        "OPTIONAL { <%s> <%s> ?v%d } . ", remoteEntity,
                        property, i));
                wherePredicates.append("\n");
                i++;
            }

            StringBuilder sparqlQuery = new StringBuilder();
            sparqlQuery.append("CONSTRUCT { ");
            sparqlQuery.append(constructPredicates);
            sparqlQuery.append(" } WHERE { ");
            sparqlQuery.append(wherePredicates);
            sparqlQuery.append(" }");

            String encodedQuery = URLEncoder.encode(sparqlQuery.toString(),
                    "UTF-8");

            String format = "application/rdf+xml";
            String encodedFormat = URLEncoder.encode(format, "UTF-8");

            URI sparqlURI = URI.create(String.format(SPARQL_URL_PATTERN,
                    SPARQL_ENDPOINT, encodedQuery, encodedFormat));
            bodyStream = fetchResourceAsStream(sparqlURI, format);

            rdfModel = ModelFactory.createDefaultModel();
            RDFReader reader = rdfModel.getReader();
            reader.read(rdfModel, bodyStream, null);
            if (cachedModels.size() > 1000) {
                cachedModels.clear();
            }
            cachedModels.put(remoteEntity, rdfModel);
        } catch (MalformedURLException e) {
            throw new DereferencingException(e);
        } catch (IOException e) {
            throw new DereferencingException(e);
        } finally {
            if (bodyStream != null) {
                try {
                    bodyStream.close();
                } catch (IOException e) {
                    log.error(e, e);
                }
            }
        }
        return rdfModel;
    }

    /**
     * @param remoteEntity URI of the remote entity
     * @param rdfModel RDF model describing the remote entity
     * @return list of local types that are compatible with the remote entity
     *         according to the type mapping configuration for this source
     */
    protected Set<String> extractMappedTypesFromModel(URI remoteEntity,
            Model rdfModel) {
        Resource resource = rdfModel.getResource(remoteEntity.toString());
        com.hp.hpl.jena.rdf.model.Property type = rdfModel.getProperty(RDF_TYPE);

        TreeSet<String> typeURIs = new TreeSet<String>();
        TreeSet<String> mappedLocalTypes = new TreeSet<String>();
        NodeIterator it = rdfModel.listObjectsOfProperty(resource, type);
        while (it.hasNext()) {
            RDFNode node = it.nextNode();
            if (node.isURIResource()) {
                typeURIs.add(((Resource) node.as(Resource.class)).getURI());
            }
        }
        Set<Entry<String, String>> typeMapping = descriptor.getMappedTypes().entrySet();
        for (Entry<String, String> typeMapEntry : typeMapping) {
            if (typeURIs.contains(typeMapEntry.getValue())) {
                mappedLocalTypes.add(typeMapEntry.getKey());
            }
        }
        return mappedLocalTypes;
    }

    @SuppressWarnings("unchecked")
    protected void syncPropertiesFromModel(URI remoteEntity, Model rdfModel,
            DocumentModel localEntity, boolean override)
            throws DereferencingException {
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
                localEntity.setPropertyValue("entity:sameas",
                        (Serializable) samesas);

                String titlePropUri = descriptor.getMappedProperties().get(
                        "dc:title");
                String label = localEntity.getTitle();
                label = label != null ? label : "Missing label";
                if (titlePropUri != null) {
                    String labelFromRDF = (String) readDecodedLiteral(rdfModel,
                            resource, titlePropUri, StringType.INSTANCE, "en");
                    label = labelFromRDF != null ? labelFromRDF : label;
                }
                sameasDisplayLabel.add(label);
                localEntity.setPropertyValue("entity:sameasDisplayLabel",
                        (Serializable) sameasDisplayLabel);
            }
        } catch (Exception e) {
            throw new DereferencingException(e);
        }

        HashMap<String, String> mapping = new HashMap<String, String>(
                descriptor.getMappedProperties());
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
                    for (String value : readStringList(rdfModel, resource,
                            remotePropertyUri)) {
                        if (!newValues.contains(value)) {
                            newValues.add(value);
                        }
                    }
                    localEntity.setPropertyValue(localPropertyName,
                            (Serializable) newValues);
                } else {
                    if (localProperty.getValue() == null
                            || "".equals(localProperty.getValue()) || override) {
                        if (type.isComplexType()
                                && "content".equals(type.getName())) {
                            Serializable linkedResource = (Serializable) readLinkedResource(
                                    rdfModel, resource, remotePropertyUri);
                            if (linkedResource != null) {
                                localEntity.setPropertyValue(localPropertyName,
                                        linkedResource);
                            }
                        } else {
                            Serializable literal = readDecodedLiteral(rdfModel,
                                    resource, remotePropertyUri, type, "en");
                            if (literal != null) {
                                localEntity.setPropertyValue(localPropertyName,
                                        literal);
                            }
                        }
                    }
                }
            } catch (PropertyException e) {
                // ignore missing properties
            } catch (ClientException e) {
                throw new DereferencingException(e);
            }
        }
    }

    protected Serializable readDecodedLiteral(Model rdfModel,
            Resource resource, String remotePropertyUri, Type type,
            String requestedLang) {
        com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(remotePropertyUri);
        NodeIterator it = rdfModel.listObjectsOfProperty(resource,
                remoteProperty);
        while (it.hasNext()) {
            RDFNode node = it.nextNode();
            if (node.isLiteral()) {
                Literal literal = ((Literal) node.as(Literal.class));
                String lang = literal.getLanguage();
                if (lang == null || lang.equals("")
                        || lang.equals(requestedLang)) {
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

    protected Blob readLinkedResource(Model rdfModel, Resource resource,
            String remotePropertyUri) {
        // download depictions or other kind of linked
        // resources
        com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(remotePropertyUri);
        NodeIterator it = rdfModel.listObjectsOfProperty(resource,
                remoteProperty);
        if (it.hasNext()) {
            String contentURI = ((Resource) it.nextNode().as(Resource.class)).getURI();

            InputStream is = null;
            try {
                is = fetchResourceAsStream(URI.create(contentURI), null);
                if (is == null) {
                    log.warn("failed to fetch resource: " + contentURI);
                    return null;
                }
                Blob blob = StreamingBlob.createFromStream(is).persist();
                int lastSlashIndex = contentURI.lastIndexOf('/');
                if (lastSlashIndex != -1) {
                    blob.setFilename(contentURI.substring(lastSlashIndex + 1));
                }
                return blob;
            } catch (IOException e) {
                log.warn(e.getMessage());
                return null;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        log.error(e, e);
                    }
                }
            }
        }
        return null;
    }

    protected List<String> readStringList(Model rdfModel, Resource resource,
            String remotePropertyUri) {

        com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(remotePropertyUri);
        NodeIterator it = rdfModel.listObjectsOfProperty(resource,
                remoteProperty);

        List<String> collectedValues = new ArrayList<String>();
        while (it.hasNext()) {
            RDFNode node = it.nextNode();
            String value = null;
            if (node.isLiteral()) {
                value = ((Literal) node.as(Literal.class)).getString();
                value = StringEscapeUtils.unescapeHtml(value);
            } else if (node.isURIResource()) {
                value = ((Resource) node.as(Resource.class)).getURI();
            } else {
                continue;
            }
            if (value != null && !collectedValues.contains(value)) {
                collectedValues.add(value);
            }
        }
        return collectedValues;
    }

    @Override
    public List<RemoteEntity> suggestRemoteEntity(String keywords, String type,
            int maxSuggestions) throws IOException {

        Set<String> acceptedTypes = new TreeSet<String>();
        if (type != null) {
            acceptedTypes.add(descriptor.getMappedTypes().get(type));
        } else {
            acceptedTypes.addAll(descriptor.getMappedTypes().values());
        }

        // fetch more suggestions than requested since we will do type
        // post-filtering afterwards
        log.debug("suggestion query for keywords: " + keywords);
        InputStream bodyStream = fetchSuggestions(keywords, maxSuggestions * 3);
        if (bodyStream == null) {
            throw new IOException(String.format(
                    "Unable to fetch suggestion response for '%s'", keywords));
        }
        // Fetch the complete payload to make it easier for debugging (should
        // not be big anyway)
        String content = IOUtils.toString(bodyStream);
        log.debug(content);

        List<RemoteEntity> suggestions = new ArrayList<RemoteEntity>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(
                    content.getBytes("utf-8")));
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList resultNodes = (NodeList) xpath.evaluate(RESULT_NODE_XPATH,
                    document, XPathConstants.NODESET);
            for (int i = 0; i < resultNodes.getLength(); i++) {
                Node resultNode = resultNodes.item(i);
                String label = null;
                URI uri = null;
                boolean hasMatchingType = false;
                Node labelNode = (Node) xpath.evaluate("Label/text()",
                        resultNode, XPathConstants.NODE);
                if (labelNode != null) {
                    label = labelNode.getNodeValue();
                }
                Node uriNode = (Node) xpath.evaluate("URI/text()", resultNode,
                        XPathConstants.NODE);
                if (uriNode != null) {
                    uri = URI.create(uriNode.getNodeValue());
                }
                NodeList typeNodes = (NodeList) xpath.evaluate(
                        "Classes/Class/URI/text()", resultNode,
                        XPathConstants.NODESET);
                for (int k = 0; k < typeNodes.getLength(); k++) {
                    Node typeNode = typeNodes.item(k);
                    if (acceptedTypes.contains(typeNode.getNodeValue())) {
                        hasMatchingType = true;
                        break;
                    }
                }
                if (hasMatchingType && label != null && uri != null) {
                    suggestions.add(new RemoteEntity(label, uri));
                    if (suggestions.size() >= maxSuggestions) {
                        break;
                    }
                }
            }
        } catch (ParserConfigurationException e) {
            log.error(e, e);
            return Collections.emptyList();
        } catch (FactoryConfigurationError e) {
            log.error(e, e);
            return Collections.emptyList();
        } catch (XPathExpressionException e) {
            log.error(e, e);
            return Collections.emptyList();
        } catch (SAXException e) {
            throw new IOException(String.format(
                    "Invalid suggestion response for '%s' with type '%s'",
                    keywords, type), e);
        } finally {
            bodyStream.close();
        }
        return suggestions;
    }

    /*
     * submethods to be overridden in mock object for the tests
     */
    protected InputStream fetchResourceAsStream(URI sparqlURI, String format)
            throws MalformedURLException, IOException {
        HttpGet get = new HttpGet(sparqlURI);
        try {
            get.setHeader("Accept", format);
            HttpResponse response = httpClient.execute(get);
            if (response.getStatusLine().getStatusCode() == 200) {
                InputStream content = response.getEntity().getContent();
                return content;
            } else {
                String errorMsg = String.format("Error resolving '%s' : ",
                        sparqlURI);
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

    protected InputStream fetchSuggestions(String keywords, int maxSuggestions)
            throws UnsupportedEncodingException, MalformedURLException,
            IOException {
        String escapedKeywords = URLEncoder.encode(keywords, "UTF-8");
        String query = String.format(SUGGESTION_URL_PATTERN, escapedKeywords,
                maxSuggestions);
        log.debug(query);

        URL url = new URL(query);
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("Accept", "application/xml");
        return connection.getInputStream();
    }

}
