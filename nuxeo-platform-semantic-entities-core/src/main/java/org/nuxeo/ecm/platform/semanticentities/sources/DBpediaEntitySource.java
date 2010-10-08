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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntity;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedRemoteEntitySource;
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
 */
public class DBpediaEntitySource extends ParameterizedRemoteEntitySource {

    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    private static final Log log = LogFactory.getLog(DBpediaEntitySource.class);

    protected static final String SPARQL_URL_PATTERN = "%s?query=%s&format=%s";

    protected String SUGGESTION_URL_PATTERN = "http://lookup.dbpedia.org/api/search.asmx/KeywordSearch?QueryString=%s&QueryClass=%s&MaxHits=%d";

    protected String SPARQL_ENDPOINT = "http://dbpedia.org/sparql";

    protected String RESULT_NODE_XPATH = "//Result";

    @Override
    public boolean canSuggestRemoteEntity() {
        return true;
    }

    @Override
    public DocumentModel dereference(CoreSession session, URI remoteEntity)
            throws DereferencingException {
        return null;
    }

    @Override
    public void dereferenceInto(DocumentModel localEntity, URI remoteEntity,
            boolean override) throws DereferencingException {
        StringBuilder sparqlQuery = new StringBuilder();
        sparqlQuery.append("CONSTRUCT { <");
        sparqlQuery.append(remoteEntity);
        sparqlQuery.append("> ?p ?o } WHERE { <");
        sparqlQuery.append(remoteEntity);
        sparqlQuery.append("> ?p ?o }");

        InputStream bodyStream = null;
        try {
            String encodedQuery = URLEncoder.encode(sparqlQuery.toString(),
                    "UTF-8");

            String format = "application/rdf+xml";
            String encodedFormat = URLEncoder.encode(format, "UTF-8");

            URI sparqlURI = URI.create(String.format(SPARQL_URL_PATTERN,
                    SPARQL_ENDPOINT, encodedQuery, encodedFormat));
            bodyStream = fetchSparqlResults(sparqlURI, format);

            Model rdfModel = ModelFactory.createDefaultModel();
            RDFReader reader = rdfModel.getReader();
            reader.read(rdfModel, bodyStream, format);

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
            syncPropertiesFromModel(remoteEntity, rdfModel, localEntity,
                    override);

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
    }

    /**
     * @param remoteEntity URI of the remote entity
     * @param rdfModel RDF model describing the remote entity
     * @return list of local types that are compatible with the remote entity
     *         according to the type mapping configuration for this source
     */
    protected Collection<String> extractMappedTypesFromModel(URI remoteEntity,
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
        Set<Entry<String, String>> mapping = descriptor.getMappedProperties().entrySet();
        Resource resource = rdfModel.getResource(remoteEntity.toString());
        for (Entry<String, String> mappedProperty : mapping) {
            String localPropertyName = mappedProperty.getKey();
            com.hp.hpl.jena.rdf.model.Property remoteProperty = rdfModel.getProperty(mappedProperty.getValue());

            try {
                Property localProperty = localEntity.getProperty(localPropertyName);
                NodeIterator it = rdfModel.listObjectsOfProperty(resource,
                        remoteProperty);
                if (localProperty.getType().isListType()) {
                    // only synchronize string lists right now
                    List<String> newValues = new ArrayList<String>(
                            localProperty.getValue(List.class));
                    if (override) {
                        newValues.clear();
                    }
                    while (it.hasNext()) {
                        RDFNode node = it.nextNode();
                        String value = null;
                        if (node.isLiteral()) {
                            value = ((Literal) node.as(Literal.class)).getString();
                        } else if (node.isURIResource()) {
                            value = ((Resource) node.as(Resource.class)).getURI();
                        } else {
                            continue;
                        }
                        if (value != null && !newValues.contains(value)) {
                            newValues.add(value);
                        }
                    }
                    localEntity.setPropertyValue(localPropertyName,
                            (Serializable) newValues);
                } else {
                    if (localProperty.getValue() == null || override) {
                        while (it.hasNext()) {
                            RDFNode node = it.nextNode();
                            if (node.isLiteral()) {
                                Literal literal = ((Literal) node.as(Literal.class));
                                // XXX: make the requested language configurable
                                String lang = literal.getLanguage();
                                if (lang == null || lang.equals("")
                                        || lang.equals("en")) {
                                    Type type = localProperty.getType();
                                    Object value = type.decode(literal.getString());
                                    localEntity.setPropertyValue(
                                            localPropertyName,
                                            (Serializable) value);
                                }
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

    /*
     * submethod to be overridden in mock object for the tests
     */
    protected InputStream fetchSparqlResults(URI sparqlURI, String format)
            throws MalformedURLException, IOException {
        URLConnection connection = sparqlURI.toURL().openConnection();
        connection.addRequestProperty("Accept", format);
        return connection.getInputStream();
    }

    @Override
    public List<RemoteEntity> suggestRemoteEntity(String keywords, String type,
            int maxSuggestions) throws IOException {

        if (type == null) {
            type = descriptor.getDefaultType();
        }

        String mappedType = descriptor.getMappedTypes().get(type);
        if (mappedType == null) {
            throw new IllegalArgumentException(String.format(
                    "Type '%s' is not mapped to any DBpedia class", type));
        } else {
            int lastSlashIndex = mappedType.lastIndexOf("/");
            if (lastSlashIndex != -1) {
                mappedType = mappedType.substring(lastSlashIndex + 1);
            }
        }

        InputStream bodyStream = fetchSuggestions(keywords, mappedType,
                maxSuggestions);
        if (bodyStream == null) {
            throw new IOException(
                    String.format(
                            "Unable to fetch suggestion response for '%s' with type '%s'",
                            keywords, type));
        }

        List<RemoteEntity> suggestions = new ArrayList<RemoteEntity>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(bodyStream);
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList resultNodes = (NodeList) xpath.evaluate(RESULT_NODE_XPATH,
                    document, XPathConstants.NODESET);
            for (int i = 0; i < resultNodes.getLength(); i++) {
                Node resultNode = resultNodes.item(i);
                NodeList nodes = resultNode.getChildNodes();
                String label = null;
                URI uri = null;
                for (int j = 0; j < nodes.getLength(); j++) {
                    Node node = nodes.item(j);
                    if ("Label".equals(node.getNodeName())) {
                        label = node.getFirstChild().getNodeValue();
                    } else if ("URI".equals(node.getNodeName())) {
                        uri = URI.create(node.getFirstChild().getNodeValue());
                    }
                }
                // TODO: add a post filtering of the result to check the
                // ontology class of the suggestion as the lookup service does
                // not do the filtering on the server side (known bug)
                if (label != null && uri != null) {
                    suggestions.add(new RemoteEntity(label, uri));
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
     * submethod to be overridden in mock object for the tests
     */
    protected InputStream fetchSuggestions(String keywords, String type,
            int maxSuggestions) throws UnsupportedEncodingException,
            MalformedURLException, IOException {
        String escapedKeywords = URLEncoder.encode(keywords, "UTF-8");
        String escapedType = URLEncoder.encode(type, "UTF-8");

        // XXX: the escapedType value is not taken into account by the service
        String query = String.format(SUGGESTION_URL_PATTERN, escapedKeywords,
                escapedType, maxSuggestions);

        URL url = new URL(query);
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("Accept", "application/xml");
        return connection.getInputStream();
    }
}
