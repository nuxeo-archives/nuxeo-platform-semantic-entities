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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedHTTPEntitySource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFReader;

/**
 * Implementation of the RemoteEntitySource interface that is able to suggest DBpedia entities by name using the
 * http://lookup.dbpedia.org RESTful service and dereference DBpedia URIs using the official DBpedia sparql endpoint.
 * This implementation uses the SPARQL endpoint instead of HTTP GET based queries since the virtuoso implementation
 * arbitrarily truncates the entity graph to around 2000 triples for entities with many properties.
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
    public Set<String> getAdmissibleTypes(URI remoteEntity) throws DereferencingException {
        Model rdfModel = fetchRDFDescription(remoteEntity);
        return extractMappedTypesFromModel(remoteEntity, rdfModel);
    }

    @Override
    public boolean dereferenceInto(DocumentModel localEntity, URI remoteEntity, boolean override,
            boolean lazyResourceFetch) throws DereferencingException {

        // fetch and parse the RDF payload describing the remote entity
        Model rdfModel = fetchRDFDescription(remoteEntity);

        // fill in the localEntity document with the content of the RDF payload
        // using the property mapping defined in the source descriptor
        return dereferenceIntoFromModel(localEntity, remoteEntity, rdfModel, override, lazyResourceFetch);
    }

    protected Model fetchRDFDescription(URI remoteEntity) throws DereferencingException {

        Model rdfModel = cachedModels.get(remoteEntity);
        if (rdfModel != null) {
            return rdfModel;
        }

        InputStream bodyStream = null;
        try {

            StringBuilder constructPredicates = new StringBuilder();
            StringBuilder wherePredicates = new StringBuilder();

            constructPredicates.append(String.format("<%s> a ?t . ", remoteEntity));
            constructPredicates.append("\n");

            wherePredicates.append(String.format("<%s> a ?t . ", remoteEntity));
            wherePredicates.append("\n");
            int i = 0;
            for (String property : new TreeSet<String>(descriptor.getMappedProperties().values())) {
                constructPredicates.append(String.format("<%s> <%s> ?v%d . ", remoteEntity, property, i));
                constructPredicates.append("\n");
                wherePredicates.append(String.format("OPTIONAL { <%s> <%s> ?v%d } . ", remoteEntity, property, i));
                wherePredicates.append("\n");
                i++;
            }

            StringBuilder sparqlQuery = new StringBuilder();
            sparqlQuery.append("CONSTRUCT { ");
            sparqlQuery.append(constructPredicates);
            sparqlQuery.append(" } WHERE { ");
            sparqlQuery.append(wherePredicates);
            sparqlQuery.append(" }");

            String encodedQuery = URLEncoder.encode(sparqlQuery.toString(), "UTF-8");

            String format = "application/rdf+xml";
            String encodedFormat = URLEncoder.encode(format, "UTF-8");

            URI sparqlURI = URI.create(String.format(SPARQL_URL_PATTERN, SPARQL_ENDPOINT, encodedQuery, encodedFormat));
            bodyStream = doHttpGet(sparqlURI, format);

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

    @Override
    public List<EntitySuggestion> suggestRemoteEntity(String keywords, String type, int maxSuggestions)
            throws IOException {

        Map<String, String> localTypes = descriptor.getReverseMappedTypes();
        Set<String> acceptedLocalTypes = new TreeSet<String>();
        if (type != null) {
            acceptedLocalTypes.add(type);
        } else {
            acceptedLocalTypes.addAll(localTypes.values());
        }
        // remove the overly generic type Entity for now
        acceptedLocalTypes.remove("Entity");

        // fetch more suggestions than requested since we will do type
        // post-filtering afterwards
        log.debug("suggestion query for keywords: " + keywords);
        InputStream bodyStream = fetchSuggestions(keywords, maxSuggestions * 3);
        if (bodyStream == null) {
            throw new IOException(String.format("Unable to fetch suggestion response for '%s'", keywords));
        }
        // Fetch the complete payload to make it easier for debugging (should
        // not be big anyway)
        String content = IOUtils.toString(bodyStream);
        log.trace(content);

        List<EntitySuggestion> suggestions = new ArrayList<EntitySuggestion>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(content.getBytes("utf-8")));
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList resultNodes = (NodeList) xpath.evaluate(RESULT_NODE_XPATH, document, XPathConstants.NODESET);
            for (int i = 0; i < resultNodes.getLength(); i++) {
                Node resultNode = resultNodes.item(i);
                String label = null;
                String uri = null;
                Node labelNode = (Node) xpath.evaluate("Label/text()", resultNode, XPathConstants.NODE);
                if (labelNode != null) {
                    label = labelNode.getNodeValue();
                }
                Node uriNode = (Node) xpath.evaluate("URI/text()", resultNode, XPathConstants.NODE);
                if (uriNode != null) {
                    uri = uriNode.getNodeValue();
                }
                NodeList typeNodes = (NodeList) xpath.evaluate("Classes/Class/URI/text()", resultNode,
                        XPathConstants.NODESET);
                String matchingLocalType = null;
                for (int k = 0; k < typeNodes.getLength(); k++) {
                    Node typeNode = typeNodes.item(k);
                    String localType = localTypes.get(typeNode.getNodeValue());
                    if (localType != null && acceptedLocalTypes.contains(localType)) {
                        matchingLocalType = localType;
                        break;
                    }
                }
                if (matchingLocalType != null && label != null && uri != null) {
                    suggestions.add(new EntitySuggestion(label, uri, matchingLocalType));
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
            throw new IOException(String.format("Invalid suggestion response for '%s' with type '%s'", keywords, type),
                    e);
        } finally {
            bodyStream.close();
        }
        return suggestions;
    }

    protected InputStream fetchSuggestions(String keywords, int maxSuggestions) throws UnsupportedEncodingException,
            MalformedURLException, IOException {
        String escapedKeywords = URLEncoder.encode(keywords, "UTF-8");
        String query = String.format(SUGGESTION_URL_PATTERN, escapedKeywords, maxSuggestions);
        log.debug(query);

        URL url = new URL(query);
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("Accept", "application/xml");
        return connection.getInputStream();
    }

}
