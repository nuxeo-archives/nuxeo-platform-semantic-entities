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
import java.io.InputStream;
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
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.nuxeo.common.utils.StringUtils;
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
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.platform.semanticentities.Constants;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.runtime.api.Framework;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

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
 * <li>Stanbol from the project http://incubator.apache.org/stanbol</li>
 * <li>OpenCalais (untested)</li>
 * <li>Maybe more</li>
 * </ul>
 *
 * @author <a href="mailto:ogrisel@nuxeo.com">Olivier Grisel</a>
 */
@Operation(id = OccurrenceExtractionOperation.ID, category = org.nuxeo.ecm.automation.core.Constants.CAT_DOCUMENT, label = "Extract occurrences", description = "Extract the text and launch an use a semantic engine to extract and link occurrences of semantic entities. Returns back the analyzed document.")
public class OccurrenceExtractionOperation {

    private static final Log log = LogFactory.getLog(OccurrenceExtractionOperation.class);

    public static final String ID = "Document.ExtractSemanticEntitiesOccurrences";

    private static final String ANY2TEXT = "any2text";

    protected static final String DEFAULT_ENGINE_URL = "https://stanbol.demo.nuxeo.com/engines";
    
    protected static final String ENGINE_URL_PROPERTY = "org.nuxeo.ecm.platform.semanticentities.stanbolUrl";

    protected static final String DEFAULT_SPARQL_QUERY = "SELECT ?label ?type ?context ";

    protected static final String DEFAULT_SOURCE_NAME = "dbpedia";

    protected static final String DEFAULT_ENGINE_OUTPUT_FORMAT = "application/rdf+xml";

    protected ConversionService conversionService;

    protected HttpClient httpClient;

    // TODO: factorize this configuration to either local entity service or
    // somewhere else configurable
    protected static final Map<String, String> localTypes = new HashMap<String, String>();
    static {
        localTypes.put("http://dbpedia.org/ontology/Place", "Place");
        localTypes.put("http://dbpedia.org/ontology/Person", "Person");
        localTypes.put("http://dbpedia.org/ontology/Organisation",
                "Organization");
    }

    public OccurrenceExtractionOperation() throws Exception {
        // constructor to be used by automation runtime
        conversionService = Framework.getService(ConversionService.class);
        initHttpClient();
    }

    public OccurrenceExtractionOperation(CoreSession session) throws Exception {
        this();
        this.session = session;
    }

    protected void initHttpClient() {
        // Create and initialize a scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http",
                PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https",
                SSLSocketFactory.getSocketFactory(), 443));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        HttpParams params = new BasicHttpParams();
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
                params, schemeRegistry);

        httpClient = new DefaultHttpClient(cm, params);
    }

    @Context
    protected CoreSession session;

    @Param(name = "engineURL", required = true, values = { DEFAULT_ENGINE_URL })
    protected String engineURL = null;

    @Param(name = "sparqlQuery", required = true, values = { DEFAULT_SPARQL_QUERY })
    protected String sparqlQuery = DEFAULT_SPARQL_QUERY;

    @Param(name = "sourceName", required = true, values = { DEFAULT_SOURCE_NAME })
    protected String sourceName = DEFAULT_SOURCE_NAME;

    @Param(name = "engineOutputFormat", required = true, values = { DEFAULT_ENGINE_OUTPUT_FORMAT })
    protected String outputFormat = DEFAULT_ENGINE_OUTPUT_FORMAT;

    @Param(name = "linkToUnrecognizedEntities", required = true, values = { "true" })
    protected boolean linkToUnrecognizedEntities = true;

    @Param(name = "linkToAmbiguousEntities", required = true, values = { "true" })
    protected boolean linkToAmbiguousEntities = true;

    @OperationMethod
    public DocumentRef run(DocumentRef docRef) throws Exception {
        DocumentModel doc = session.getDocument(docRef);
        doc = run(doc);
        return doc.getRef();
    }

    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws Exception {
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        if (schemaManager.getDocumentTypeNamesExtending(Constants.ENTITY_TYPE).contains(
                doc.getType())
                || schemaManager.getDocumentTypeNamesExtending(
                        Constants.OCCURRENCE_TYPE).contains(doc.getType())) {
            // do not try to analyze local entities themselves
            return doc;
        }

        String textContent = extractText(doc);
        String output = callSemanticEngine(textContent, outputFormat);

        Model model = ModelFactory.createDefaultModel().read(
                new StringReader(output), null);
        // TODO: implement entity extraction from model and linking to local
        List<OccurrenceGroup> groups = findStanbolEntityOccurrences(model);
        if (groups.isEmpty()) {
            return doc;
        }

        LocalEntityService leService = Framework.getService(LocalEntityService.class);
        DocumentModel entityContainer = leService.getEntityContainer(session);
        for (OccurrenceGroup group : groups) {
            List<EntitySuggestion> suggestions = leService.suggestEntity(
                    session, group.name, group.type, 3);

            if (suggestions.isEmpty() && linkToUnrecognizedEntities) {
                PathSegmentService pathService = Framework.getService(PathSegmentService.class);
                DocumentModel localEntity = session.createDocumentModel(group.type);
                localEntity.setPropertyValue("dc:title", group.name);
                String pathSegment = pathService.generatePathSegment(localEntity);
                localEntity.setPathInfo(entityContainer.getPathAsString(),
                        pathSegment);
                localEntity = session.createDocument(localEntity);
                session.save();
                leService.addOccurrences(session, doc.getRef(),
                        localEntity.getRef(), group.occurrences);
            } else {
                if (suggestions.size() > 1 && !linkToAmbiguousEntities) {
                    continue;
                }
                EntitySuggestion bestGuess = suggestions.get(0);
                leService.addOccurrences(session, doc.getRef(), bestGuess,
                        group.occurrences);
            }
        }
        return doc;
    }

    /**
     * Find the list of all textual occurrences of entities in the output of the
     * Apache Stanbol engine.
     */
    public List<OccurrenceGroup> findStanbolEntityOccurrences(Model model) {
        // Retrieve the existing text annotations handling the subsumption
        // relationships
        Property type = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Property entityType = model.getProperty("http://purl.org/dc/terms/type");
        Property dcRelation = model.getProperty("http://purl.org/dc/terms/relation");
        Resource textAnnotationType = model.getResource("http://fise.iks-project.eu/ontology/TextAnnotation");
        ResIterator it = model.listSubjectsWithProperty(type,
                textAnnotationType);
        List<OccurrenceGroup> groups = new ArrayList<OccurrenceGroup>();
        for (; it.hasNext();) {
            Resource annotation = it.nextResource();
            if (model.listObjectsOfProperty(annotation, dcRelation).hasNext()) {
                // this is not the most specific occurrence of this name: skip
                continue;
            }

            Statement typeStmt = annotation.getProperty(entityType);
            if (typeStmt == null || !typeStmt.getObject().isURIResource()) {
                continue;
            }
            Resource typeResouce = (Resource) typeStmt.getObject().as(
                    Resource.class);
            String localType = localTypes.get(typeResouce.getURI());
            if (localType == null) {
                continue;
            }
            OccurrenceInfo occInfo = getOccurrenceInfo(model, annotation);
            if (occInfo == null) {
                continue;
            }
            OccurrenceGroup group = new OccurrenceGroup(occInfo.mention,
                    localType);
            group.occurrences.add(occInfo);

            // This is a first occurrence, collect any subsumed annotations
            ResIterator it2 = model.listSubjectsWithProperty(dcRelation,
                    annotation);
            for (; it2.hasNext();) {
                OccurrenceInfo subMention = getOccurrenceInfo(model,
                        it2.nextResource());
                if (subMention == null) {
                    continue;
                }
                group.occurrences.add(subMention);
            }
            groups.add(group);
        }
        return groups;
    }

    protected OccurrenceInfo getOccurrenceInfo(Model model, Resource annotation) {
        Property mentionProp = model.getProperty("http://fise.iks-project.eu/ontology/selected-text");
        Statement mentionStmt = annotation.getProperty(mentionProp);
        if (mentionStmt == null || !mentionStmt.getObject().isLiteral()) {
            return null;
        }
        Literal mentionLiteral = (Literal) mentionStmt.getObject().as(
                Literal.class);

        Property contextProp = model.getProperty("http://fise.iks-project.eu/ontology/selection-context");
        Statement contextStmt = annotation.getProperty(contextProp);
        if (contextStmt == null || !contextStmt.getObject().isLiteral()) {
            return null;
        }
        Literal contextLiteral = (Literal) contextStmt.getObject().as(
                Literal.class);
        // TODO: normalize whitespace
        String mention = mentionLiteral.getString().trim();
        String context = contextLiteral.getString().trim();

        if (!context.contains(mention) || context.length() > 500) {
            // context extraction is likely to have failed on some complex
            // layout
            context = mention;
        }

        return new OccurrenceInfo(mention, context);
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
        
        String effectiveEngineUrl = engineURL;
        if (effectiveEngineUrl == null) {
            // no Automation Chain configuration available: use the
            // configuration from a properties file
            effectiveEngineUrl = Framework.getProperty(ENGINE_URL_PROPERTY,
                    DEFAULT_ENGINE_URL);
            if (effectiveEngineUrl.trim().isEmpty()) {
                effectiveEngineUrl = DEFAULT_ENGINE_URL;
            }
        }
        HttpPost post = new HttpPost(effectiveEngineUrl);
        try {
            post.setHeader("Accept", outputFormat);
            // TODO: fix the Stanbol engine handling of charset in mimetype
            // negociation
            // post.setHeader("Content-Type", "text/plain; charset=utf-8");
            post.setHeader("Content-Type", "text/plain");
            post.setEntity(new ByteArrayEntity(textContent.getBytes("utf-8")));
            HttpResponse response = httpClient.execute(post);
            InputStream content = response.getEntity().getContent();
            String body = IOUtils.toString(content);
            content.close();
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
