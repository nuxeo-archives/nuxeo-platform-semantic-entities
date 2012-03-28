package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.platform.semanticentities.AnalysisTask;
import org.nuxeo.ecm.platform.semanticentities.Constants;
import org.nuxeo.ecm.platform.semanticentities.DereferencingException;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.ProgressStatus;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntityService;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntitySource;
import org.nuxeo.ecm.platform.semanticentities.SemanticAnalysisService;
import org.nuxeo.ecm.platform.semanticentities.SerializationTask;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import com.google.common.collect.MapMaker;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

public class SemanticAnalysisServiceImpl extends DefaultComponent implements
        SemanticAnalysisService {

    private static final Log log = LogFactory.getLog(SemanticAnalysisServiceImpl.class);

    Pattern INVALID_XML_CHARS = Pattern.compile("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\uD800\uDC00-\uDBFF\uDFFF]");

    protected final Map<DocumentLocation, String> states = new MapMaker().concurrencyLevel(
            10).expiration(30, TimeUnit.MINUTES).makeMap();

    private static final String ANY2TEXT = "any2text";

    protected static final String DEFAULT_STANBOL_URL = "https://stanbol.demo.nuxeo.com/";

    protected static final String STANBOL_URL_PROPERTY = "org.nuxeo.ecm.platform.semanticentities.stanbolUrl";

    protected static final String DEFAULT_ENGINE_OUTPUT_FORMAT = "application/rdf+xml";

    // TODO: turn the following fields into configurable parameters set by a
    // contribution to an extension point

    protected String outputFormat = DEFAULT_ENGINE_OUTPUT_FORMAT;

    protected boolean linkToUnrecognizedEntities = true;

    protected boolean linkToAmbiguousEntities = true;

    protected boolean linkShortPersonNames = false;

    protected boolean prefetchSuggestion = true;

    // use framework property by default and fallback to public nuxeo instance
    protected String engineURL = null;

    protected HttpClient httpClient;

    protected ConversionService conversionService;

    protected LocalEntityService leService;

    protected PathSegmentService pathService;

    protected SchemaManager schemaManager;

    protected BlockingQueue<Runnable> analysisTaskQueue;

    protected ThreadPoolExecutor analysisExecutor;

    protected BlockingQueue<Runnable> serializationTaskQueue;

    protected ThreadPoolExecutor serializationExecutor;

    protected boolean active = false;

    protected RemoteEntityService reService;

    // TODO: make the following configurable using an extension point
    protected static final Map<String, String> LOCAL_TYPES = new HashMap<String, String>();
    static {
        LOCAL_TYPES.put("http://dbpedia.org/ontology/Place", "Place");
        LOCAL_TYPES.put("http://dbpedia.org/ontology/Person", "Person");
        LOCAL_TYPES.put("http://dbpedia.org/ontology/Organisation",
                "Organization");
        LOCAL_TYPES.put("http://www.w3.org/2004/02/skos/core#Concept", "Topic");
    }

    @Override
    public void activate(ComponentContext context) throws Exception {
        super.activate(context);
        conversionService = Framework.getService(ConversionService.class);
        leService = Framework.getService(LocalEntityService.class);
        pathService = Framework.getService(PathSegmentService.class);
        schemaManager = Framework.getService(SchemaManager.class);
        initHttpClient();

        NamedThreadFactory analysisThreadFactory = new NamedThreadFactory(
                "Nuxeo Async Semantic Analysis");
        analysisTaskQueue = new LinkedBlockingQueue<Runnable>();
        analysisExecutor = new ThreadPoolExecutor(4, 8, 5, TimeUnit.MINUTES,
                analysisTaskQueue, analysisThreadFactory);

        NamedThreadFactory serializationThreadFactory = new NamedThreadFactory(
                "Nuxeo Async Semantic Link Serialization");
        serializationTaskQueue = new LinkedBlockingQueue<Runnable>();
        serializationExecutor = new ThreadPoolExecutor(1, 1, 5,
                TimeUnit.MINUTES, serializationTaskQueue,
                serializationThreadFactory);
        active = true;
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        active = false;
        analysisTaskQueue.clear();
        serializationTaskQueue.clear();
        analysisExecutor.shutdownNow();
        serializationExecutor.shutdownNow();
    }

    @Override
    public void scheduleSerializationTask(SerializationTask task) {
        states.put(task.getDocumentLocation(),
                ProgressStatus.STATUS_LINKING_QUEUED);
        while (serializationTaskQueue.remove(task)) {
            // remove duplicates to only link to the latest version
        }
        serializationExecutor.execute(task);
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

    protected boolean shouldSkip(DocumentModel doc) throws PropertyException,
            ClientException {
        if (schemaManager.getDocumentTypeNamesExtending(Constants.ENTITY_TYPE).contains(
                doc.getType())
                || schemaManager.getDocumentTypeNamesExtending(
                        Constants.OCCURRENCE_TYPE).contains(doc.getType())) {
            // do not try to analyze local entities themselves
            return true;
        }
        return false;
    }

    @Override
    public void launchAnalysis(String repositoryName, DocumentRef docRef)
            throws ClientException {
        AnalysisTask task = new AnalysisTask(repositoryName, docRef, this);
        if (!analysisTaskQueue.contains(task)) {
            states.put(new DocumentLocationImpl(repositoryName, docRef),
                    ProgressStatus.STATUS_ANALYSIS_QUEUED);
            analysisExecutor.execute(task);
        }
    }

    @Override
    public void launchSynchronousAnalysis(DocumentModel doc, CoreSession session)
            throws ClientException, IOException {
        if (shouldSkip(doc)) {
            return;
        }
        try {
            states.put(
                    new DocumentLocationImpl(doc.getRepositoryName(),
                            doc.getRef()),
                    ProgressStatus.STATUS_ANALYSIS_PENDING);
            String textContent = extractText(doc);
            createLinks(doc, session, analyze(session, textContent));
        } finally {
            states.remove(new DocumentLocationImpl(doc.getRepositoryName(),
                    doc.getRef()));
        }
    }

    @Override
    public void createLinks(DocumentModel doc, CoreSession session,
            List<OccurrenceGroup> groups) throws ClientException, IOException {
        if (groups.isEmpty()) {
            return;
        }
        states.put(
                new DocumentLocationImpl(doc.getRepositoryName(), doc.getRef()),
                ProgressStatus.STATUS_LINKING_PENDING);
        DocumentModel entityContainer = leService.getEntityContainer(session);
        for (OccurrenceGroup group : groups) {

            // hardcoded trick to avoid linking to persons just based on their
            // first name or last names for instance
            if (!linkShortPersonNames && "Person".equals(group.type)
                    && group.name.trim().split(" ").length <= 1) {
                continue;
            }
            List<EntitySuggestion> suggestions = leService.suggestEntity(
                    session, group, 3);
            if (suggestions.isEmpty() && linkToUnrecognizedEntities) {
                DocumentModel localEntity = session.createDocumentModel(group.type);
                localEntity.setPropertyValue("dc:title", group.name);
                localEntity.setPropertyValue("entity:automaticallyCreated",
                        true);
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
                leService.addOccurrences(session, doc.getRef(),
                        bestGuess.withAutomaticallyCreated(true),
                        group.occurrences);
            }
        }
    }

    public List<OccurrenceGroup> findStanbolEntityOccurrences(
            CoreSession session, Model model) throws DereferencingException,
            ClientException {
        // Retrieve the existing text annotations handling the subsumption
        // relationships
        Property type = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Property dcType = model.getProperty("http://purl.org/dc/terms/type");
        Property dcRelation = model.getProperty("http://purl.org/dc/terms/relation");
        Property entityReference = model.getProperty("http://fise.iks-project.eu/ontology/entity-reference");
        Property entityLabel = model.getProperty("http://fise.iks-project.eu/ontology/entity-label");
        Property entityType = model.getProperty("http://fise.iks-project.eu/ontology/entity-type");
        Resource textAnnotationType = model.getResource("http://fise.iks-project.eu/ontology/TextAnnotation");
        Resource topicAnnotationType = model.getResource("http://fise.iks-project.eu/ontology/TopicAnnotation");

        ResIterator it = model.listSubjectsWithProperty(type,
                textAnnotationType);
        List<OccurrenceGroup> groups = new ArrayList<OccurrenceGroup>();
        for (; it.hasNext();) {
            Resource annotation = it.nextResource();
            if (model.listObjectsOfProperty(annotation, dcRelation).hasNext()) {
                // this is not the most specific occurrence of this name: skip
                continue;
            }
            Statement typeStmt = annotation.getProperty(dcType);
            if (typeStmt == null || !typeStmt.getObject().isURIResource()) {
                continue;
            }
            Resource typeResouce = typeStmt.getObject().as(Resource.class);
            String localType = LOCAL_TYPES.get(typeResouce.getURI());
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
                Resource linkedResource = it2.nextResource();
                OccurrenceInfo subMention = getOccurrenceInfo(model,
                        linkedResource);
                if (subMention != null) {
                    // this is a sub-mention
                    group.occurrences.add(subMention);
                    continue;
                }
                if (prefetchSuggestion) {
                    // maybe this is a suggestion, try to fetch it
                    EntitySuggestion suggestion = getEntitySuggestion(session,
                            model, linkedResource, localType);
                    if (suggestion != null) {
                        group.entitySuggestions.add(suggestion.withAutomaticallyCreated(true));
                    }
                }
            }
            // sort by score for easy display and testing
            Collections.sort(group.entitySuggestions);

            // sort occurrences by occurrence order for easy display and testing
            Collections.sort(group.occurrences);
            groups.add(group);
        }

        // Second pass to collect the topic style annotations (no explicit
        // mention in the body of the text).
        if (prefetchSuggestion) {
            it = model.listSubjectsWithProperty(type, topicAnnotationType);
            for (; it.hasNext();) {
                Resource topicAnnotation = it.nextResource();
                Statement typeStmt = topicAnnotation.getProperty(entityType);
                if (typeStmt == null || !typeStmt.getObject().isURIResource()) {
                    continue;
                }
                Resource typeResouce = typeStmt.getObject().as(Resource.class);
                String localType = LOCAL_TYPES.get(typeResouce.getURI());
                if (localType == null) {
                    continue;
                }
                Statement labelStmt = topicAnnotation.getProperty(entityLabel);
                if (labelStmt == null) {
                    continue;
                }
                String label = labelStmt.getObject().as(Literal.class).getString();
                Statement refStmt = topicAnnotation.getProperty(entityReference);
                if (refStmt == null) {
                    continue;
                }
                OccurrenceGroup topic = new OccurrenceGroup(label, localType);
                // maybe this is a suggestion, try to fetch it
                EntitySuggestion suggestion = getEntitySuggestion(session,
                        model, topicAnnotation, localType);
                if (suggestion != null) {
                    topic.entitySuggestions.add(suggestion.withAutomaticallyCreated(true));
                }
                groups.add(topic);
            }
        }
        // sort by alphabetic order for names for easy display and testing
        Collections.sort(groups);
        return groups;
    }

    protected EntitySuggestion getEntitySuggestion(CoreSession session,
            Model model, Resource entitySuggestionResource, String localType)
            throws DereferencingException, ClientException {
        Property entityReference = model.getProperty("http://fise.iks-project.eu/ontology/entity-reference");
        Property scoreProperty = model.getProperty("http://fise.iks-project.eu/ontology/confidence");
        Property entityLabelProperty = model.getProperty("http://fise.iks-project.eu/ontology/entity-label");
        Resource linkedResource = entitySuggestionResource.getPropertyResourceValue(entityReference);
        if (linkedResource == null) {
            return null;
        }
        Statement scoreStmt = entitySuggestionResource.getProperty(scoreProperty);
        double score = scoreStmt != null ? scoreStmt.getObject().asLiteral().getDouble()
                : 0.0;
        // check whether the pre-fetched model has additional info on the linked
        // resource
        Property type = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        StmtIterator typeIterator = linkedResource.listProperties(type);
        String remoteEntityUri = linkedResource.getURI();
        if (typeIterator.hasNext()) {
            // delegate the pre-fetch the description using the mapping
            // information from the
            // remote sources
            DocumentModel entity = session.createDocumentModel(localType);
            getRemoteEntityService().dereferenceIntoFromModel(entity,
                    URI.create(remoteEntityUri), model, true, true);
            return new EntitySuggestion(entity).withScore(score);
        } else {
            // treat the suggestion as a lazily fetched remote entity
            Statement labelStmt = entitySuggestionResource.getProperty(entityLabelProperty);
            if (labelStmt != null) {
                String label = labelStmt.getObject().asLiteral().getString();
                return new EntitySuggestion(label, remoteEntityUri, localType).withScore(score);
            }
        }
        return null;
    }

    protected OccurrenceInfo getOccurrenceInfo(Model model, Resource annotation) {
        Property mentionProp = model.getProperty("http://fise.iks-project.eu/ontology/selected-text");
        Property startProp = model.getProperty("http://fise.iks-project.eu/ontology/start");

        Statement mentionStmt = annotation.getProperty(mentionProp);
        if (mentionStmt == null || !mentionStmt.getObject().isLiteral()) {
            return null;
        }
        Literal mentionLiteral = mentionStmt.getObject().as(Literal.class);
        String mention = mentionLiteral.getString().trim();

        double position = 0.0;
        Statement startStmt = annotation.getProperty(startProp);
        if (startStmt != null && startStmt.getObject().isLiteral()) {
            Literal startLiteral = startStmt.getObject().as(Literal.class);
            position = Double.parseDouble(startLiteral.getString());
        }

        Property contextProp = model.getProperty("http://fise.iks-project.eu/ontology/selection-context");
        Statement contextStmt = annotation.getProperty(contextProp);

        if (contextStmt != null && contextStmt.getObject().isLiteral()) {
            Literal contextLiteral = contextStmt.getObject().as(Literal.class);
            // TODO: normalize whitespace
            String context = contextLiteral.getString().trim();

            if (!context.contains(mention) || context.length() > 10000) {
                // context extraction is likely to have failed on some complex
                // layout
                context = mention;
            }
            return new OccurrenceInfo(mention, context).withOrder(position);
        } else {
            return new OccurrenceInfo(mention, mention).withOrder(position);
        }
    }

    @Override
    public List<OccurrenceGroup> analyze(CoreSession session, String textContent)
            throws IOException, ClientException {
        String output = callSemanticEngine(textContent, outputFormat, 2);
        Model model = ModelFactory.createDefaultModel().read(
                new StringReader(output), null);
        return findStanbolEntityOccurrences(session, model);
    }

    @Override
    public List<OccurrenceGroup> analyze(CoreSession session, DocumentModel doc)
            throws IOException, ClientException {
        states.put(
                new DocumentLocationImpl(doc.getRepositoryName(), doc.getRef()),
                ProgressStatus.STATUS_ANALYSIS_PENDING);
        if (shouldSkip(doc)) {
            return Collections.emptyList();
        }
        return analyze(session, extractText(doc));
    }

    public String callSemanticEngine(String textContent, String outputFormat,
            int retry) throws IOException {

        String effectiveEnhancerUrl = engineURL;
        if (effectiveEnhancerUrl == null) {
            // no Automation Chain configuration available: use the
            // configuration from a properties file
            effectiveEnhancerUrl = Framework.getProperty(STANBOL_URL_PROPERTY,
                    DEFAULT_STANBOL_URL);
            if (effectiveEnhancerUrl.trim().isEmpty()) {
                effectiveEnhancerUrl = DEFAULT_STANBOL_URL;
            }
            if (!effectiveEnhancerUrl.endsWith("/")) {
                effectiveEnhancerUrl += "/";
            }
            effectiveEnhancerUrl += "enhancer/";
        }
        HttpPost post = new HttpPost(effectiveEnhancerUrl);
        try {
            post.setHeader("Accept", outputFormat);
            // TODO: fix the Stanbol engine handling of charset in mimetype
            // Negotiation
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
                if (retry > 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // pass
                    }
                    return callSemanticEngine(textContent, outputFormat,
                            retry - 1);
                } else {
                    String errorMsg = String.format(
                            "Unexpected response from '%s': %s\n %s",
                            effectiveEnhancerUrl,
                            response.getStatusLine().toString(), body);
                    throw new IOException(errorMsg);
                }
            }
        } catch (ClientProtocolException e) {
            post.abort();
            throw new ClientProtocolException(String.format(
                    "Error connecting to '%s': %s", effectiveEnhancerUrl,
                    e.getMessage(), e));
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
        boolean extractBlobs = true;
        try {
            String noteContent = (String) doc.getPropertyValue("note:note");
            noteContent = INVALID_XML_CHARS.matcher(noteContent).replaceAll("");
            StreamingBlob blob = StreamingBlob.createFromString(noteContent);
            blob.setMimeType("text/html");
            BlobHolder bh = new SimpleBlobHolder(blob);
            BlobHolder converted = conversionService.convert(ANY2TEXT, bh, null);
            sb.append(converted.getBlob().getString());
            sb.append("\n\n");
            // skip blob extraction to avoid extracting the note text twice in
            // case the blob is related to the
            // note content + structured metadata.
            extractBlobs = false;
        } catch (PropertyException pe) {
            // ignore, not a note document
        } catch (IOException e) {
            throw new ClientException(e);
        }

        if (doc.hasFacet(FacetNames.HAS_RELATED_TEXT)) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> resources = doc.getProperty(
                    "relatedtext:relatedtextresources").getValue(List.class);
            for (Map<String, String> relatedResource : resources) {
                String text = relatedResource.get("relatedtext");
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text);
                    sb.append("\n\n");
                }
            }
        }
        if (extractBlobs) {
            BlobsExtractor extractor = new BlobsExtractor();
            sb.append(blobsToText(extractor.getBlobs(doc)));
        }
        // remove any invisible control characters that can be not accepted
        // inside XML 1.0 payload (e.g. in SOAP) and are useless for text
        // analysis anyway.
        return INVALID_XML_CHARS.matcher(sb.toString()).replaceAll("");
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

    @Override
    public ProgressStatus getProgressStatus(String repositoryName,
            DocumentRef docRef) {
        DocumentLocation loc = new DocumentLocationImpl(repositoryName, docRef);
        String status = states.get(loc);
        if (status == null) {
            // early return
            return null;
        }
        @SuppressWarnings("rawtypes")
        Queue q = null;
        if (ProgressStatus.STATUS_ANALYSIS_QUEUED.equals(status)) {
            q = analysisTaskQueue;
        } else if (ProgressStatus.STATUS_LINKING_QUEUED.equals(status)) {
            q = serializationTaskQueue;
        }
        int posInQueue = 0;
        int queueSize = 0;
        if (q != null) {
            Object[] queuedItems = q.toArray();
            queueSize = queuedItems.length;
            for (int i = 0; i < queueSize; i++) {
                if (queuedItems[i].equals(loc)) {
                    posInQueue = i + 1;
                    break;
                }
            }
        }
        return new ProgressStatus(status, posInQueue, queueSize);
    }

    @Override
    public void clearProgressStatus(String repositoryName, DocumentRef docRef) {
        states.remove(new DocumentLocationImpl(repositoryName, docRef));
    }

    /**
     * Creates non-daemon threads at normal priority.
     */
    public static class NamedThreadFactory implements ThreadFactory {

        private static final AtomicInteger poolNumber = new AtomicInteger();

        private final AtomicInteger threadNumber = new AtomicInteger();

        private final ThreadGroup group;

        private final String namePrefix;

        public NamedThreadFactory(String prefix) {
            SecurityManager sm = System.getSecurityManager();
            group = sm == null ? Thread.currentThread().getThreadGroup()
                    : sm.getThreadGroup();
            namePrefix = prefix + ' ' + poolNumber.incrementAndGet() + '-';
        }

        @Override
        public Thread newThread(Runnable r) {
            String name = namePrefix + threadNumber.incrementAndGet();
            Thread t = new Thread(group, r, name);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    protected RemoteEntitySource getRemoteEntityService() {
        if (reService == null) {
            try {
                reService = Framework.getService(RemoteEntityService.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return reService;
    }

}
