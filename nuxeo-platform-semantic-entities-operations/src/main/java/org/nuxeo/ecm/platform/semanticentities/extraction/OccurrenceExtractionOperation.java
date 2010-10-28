package org.nuxeo.ecm.platform.semanticentities.extraction;

/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     ogrisel
 */

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentRefList;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.runtime.api.Framework;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Use a semantic engine to extract the occurrences of semantic entities from
 * the text content of a document.
 * 
 * The semantic engine is assumed to accept an HTTP POST request on a fixed URL
 * and synchronously return the result of the analysis as an RDF/XML graph in
 * the body of the response.
 * 
 * The label, type and context text snippet of each occurrence is then extracted
 * by performing a configurable SPARQL query on the resulting RDF model loaded
 * in a temporary RDF graph.
 * 
 * This pattern should work for semantic engines such as:
 * <ul>
 * <li>fise from the project http://iks-project.eu</li>
 * <li>OpenCalais (untested)</li>
 * <li>Maybe more</li>
 * </ul>
 * 
 * @author <a href="mailto:ogrisel@nuxeo.com">Olivier Grisel</a>
 */
@Operation(id = OccurrenceExtractionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Extract occurrences", description = "Extract the text and launch an use a semantic engine to extract and link occurrences of semantic entities. Returns back the analyzed document.")
public class OccurrenceExtractionOperation {

    private static final Log log = LogFactory.getLog(OccurrenceExtractionOperation.class);

    public static final String ID = "Document.ExtractSemanticEntitiesOccurrences";

    private static final String ANY2TEXT = "any2text";

    protected static final String DEFAULT_ENGINE_URL = "http://fise.demo.nuxeo.com/engines";

    protected static final String DEFAULT_SPARQL_QUERY = "SELECT ?label ?type ?context ";

    protected static final String DEFAULT_SOURCE_NAME = "dbpedia";

    protected static final String DEFAULT_ENGINE_OUTPUT_FORMAT = "application/rdf+xml";

    protected ConversionService conversionService;

    protected final HttpClient httpClient;

    public OccurrenceExtractionOperation() throws Exception {
        // constructor to be used by automation runtime
        conversionService = Framework.getService(ConversionService.class);

        // Create and initialize a scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http",
                PlainSocketFactory.getSocketFactory(), 80));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        HttpParams params = new BasicHttpParams();
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
                params, schemeRegistry);

        httpClient = new DefaultHttpClient(cm, params);
    }

    public OccurrenceExtractionOperation(CoreSession session) throws Exception {
        this();
        this.session = session;
    }

    @Context
    protected CoreSession session;

    @Param(name = "engineURL", required = true, values = { DEFAULT_ENGINE_URL })
    protected String engineURL = DEFAULT_ENGINE_URL;

    @Param(name = "sparqlQuery", required = true, values = { DEFAULT_SPARQL_QUERY })
    protected String sparqlQuery = DEFAULT_SPARQL_QUERY;

    @Param(name = "sourceName", required = true, values = { DEFAULT_SOURCE_NAME })
    protected String sourceName = DEFAULT_SOURCE_NAME;

    @Param(name = "engineOutputFormat", required = true, values = { DEFAULT_ENGINE_OUTPUT_FORMAT })
    protected String outputFormat = DEFAULT_ENGINE_OUTPUT_FORMAT;

    @OperationMethod
    public DocumentRef run(DocumentRef docRef) throws Exception {
        DocumentModel doc = session.getDocument(docRef);
        doc = run(doc);
        return doc.getRef();
    }

    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws Exception {
        LocalEntityService leService = Framework.getService(LocalEntityService.class);
        if (leService.getEntityTypeNames().contains(doc.getType())) {
            // do not try to analyze local entities themselves
            return doc;
        }
        String textContent = extractText(doc);
        log.debug("extracted text: " + textContent);
        String output = callSemanticEngine(textContent, outputFormat);
        log.debug(output);

        Model model = ModelFactory.createDefaultModel().read(
                new StringReader(output), null);
        // TODO: implement entity extraction from model and linking to local
        // entities
        return doc;
    }

    /**
     * Find the list of all textual occurrences of entities in the output of the
     * fise engine.
     * 
     * @param model
     * @return
     */
    public List<List<OccurrenceInfo>> findFiseEntityOccurrences(Model model) {

        // Retrieve the existing text annotations handling the sub-sumption
        // relationships
        Map<Resource, List<Resource>> textAnnotations = new HashMap<Resource, List<Resource>>();
        Property type = model.getProperty("TODO");
        Property dcRelation = model.getProperty("TODO");
        Resource textAnnotationType = model.getResource("TODO");
        ResIterator it = model.listSubjectsWithProperty(type,
                textAnnotationType);
        for (; it.hasNext();) {
            Resource annotation = it.nextResource();
            if (!model.listObjectsOfProperty(annotation, dcRelation).hasNext()) {
                // this is not the most specific occurrence of this name: skip
            }
            // This is a first occurrence, collect any subsumed annotations
            List<Resource> subsumed = new ArrayList<Resource>();
            ResIterator it2 = model.listSubjectsWithProperty(dcRelation,
                    annotation);
            for (; it2.hasNext();) {
                subsumed.add(it2.nextResource());
            }
            textAnnotations.put(annotation, subsumed);
        }
        return null;
    }

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) throws Exception {
        DocumentModelList result = new DocumentModelListImpl(
                (int) docs.totalSize());
        for (DocumentModel doc : docs) {
            result.add(run(doc));
        }
        return result;
    }

    @OperationMethod
    public DocumentModelList run(DocumentRefList docRefs) throws Exception {
        DocumentModelList result = new DocumentModelListImpl(
                (int) docRefs.totalSize());
        for (DocumentRef docRef : docRefs) {
            result.add(session.getDocument(run(docRef)));
        }
        return result;
    }

    protected String callSemanticEngine(String textContent, String outputFormat)
            throws ClientProtocolException, IOException {
        HttpPost post = new HttpPost(engineURL);
        try {
            post.setHeader("Accept", outputFormat);
            // TODO: fix the fise engine handling of charset in mimetype
            // negociation
            // post.setHeader("Content-Type", "text/plain; charset=utf-8");
            post.setHeader("Content-Type", "text/plain");
            post.setEntity(new ByteArrayEntity(textContent.getBytes("utf-8")));
            HttpResponse response = httpClient.execute(post);
            String body = IOUtils.toString(response.getEntity().getContent());
            if (response.getStatusLine().getStatusCode() == 200) {
                return body;
            } else {
                String errorMsg = response.getStatusLine().toString();
                log.error(errorMsg + ":\n" + body);
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

    protected String extractText(DocumentModel doc) throws ClientException {
        StringBuilder sb = new StringBuilder();
        // TODO: un-hardcode me and refactorize into a reuseable, standalone
        // operation
        sb.append(doc.getTitle());
        sb.append("\n\n");
        Serializable description = doc.getPropertyValue("dc:description");
        if (description != null) {
            sb.append(description);
            sb.append("\n\n");
        }

        // special handling of the HTML payload of notes
        try {
            String noteContent = (String) doc.getPropertyValue("note:note");
            StreamingBlob blob = StreamingBlob.createFromString(noteContent);
            blob.setMimeType("text/html");
            BlobHolder bh = new SimpleBlobHolder(blob);
            BlobHolder converted = conversionService.convert(ANY2TEXT, bh, null);
            sb.append(converted.getBlob().getString());
            sb.append("\n\n");
        } catch (PropertyException pe) {
            // ignore, not a note document
        } catch (IOException e) {
            throw new ClientException(e);
        }

        BlobsExtractor extractor = new BlobsExtractor();
        sb.append(blobsToText(extractor.getBlobs(doc)));
        return sb.toString();
    }

    protected String blobsToText(List<Blob> blobs) {
        List<String> strings = new LinkedList<String>();
        for (Blob blob : blobs) {
            try {
                SimpleBlobHolder bh = new SimpleBlobHolder(blob);
                BlobHolder result = conversionService.convert(ANY2TEXT, bh,
                        null);
                if (result == null) {
                    continue;
                }
                blob = result.getBlob();
                if (blob == null) {
                    continue;
                }
                String string = new String(blob.getByteArray(), "UTF-8");
                // strip '\0 chars from text
                if (string.indexOf('\0') >= 0) {
                    string = string.replace("\0", " ");
                }
                strings.add(string);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                continue;
            }
        }
        return StringUtils.join(strings, "\n\n");
    }

}
