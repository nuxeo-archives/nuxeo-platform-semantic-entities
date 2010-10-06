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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntity;
import org.nuxeo.ecm.platform.semanticentities.service.ParameterizedRemoteEntitySource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Implementation of the RemoteEntitySource interface that is able to suggest
 * DBpedia entities by name using the http://lookup.dbpedia.org RESTful service
 * and dereference DBpedia URIs using the official DBpedia sparql endpoint.
 */
public class DBpediaEntitySource extends ParameterizedRemoteEntitySource {

    private static final Log log = LogFactory.getLog(DBpediaEntitySource.class);

    protected static final String SUGGESTION_URL_PATTERN = "http://lookup.dbpedia.org/api/search.asmx/KeywordSearch?QueryString=%s&QueryClass=%s&MaxHits=%d";

    private static final String RESULT_NODE_XPATH = "//Result";

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
