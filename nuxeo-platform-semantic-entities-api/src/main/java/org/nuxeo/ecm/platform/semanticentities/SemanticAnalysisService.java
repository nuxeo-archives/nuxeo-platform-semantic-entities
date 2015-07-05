package org.nuxeo.ecm.platform.semanticentities;

import java.io.IOException;
import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;

/**
 * Service to asynchronously launch and monitor the semantic analysis of Nuxeo documents.
 */
public interface SemanticAnalysisService {

    /**
     * DocumentModel ContextData marker to avoid recursive analysis of documents by event listeners.
     */
    public static final String SKIP_SEMANTIC_ANALYSIS = "SKIP_SEMANTIC_ANALYSIS";

    /**
     * @return true if the service could be correctly initialized
     */
    boolean isActive();

    /**
     * Synchronous analysis of pre-extracted text content (without linking).
     *
     * @param session used to create in-memory prefetched entity suggestions if available in the analysis engine
     *            response.
     * @param textContent the text to send to the engines
     * @return Occurrence and properties suggestions
     * @throws IOException if the engine is not reachable or fails
     * @throws ClientException if the in-memory creation of prefetched entity suggestions fails (very unlikely)
     */
    AnalysisResults analyze(CoreSession session, String textContent) throws IOException;

    /**
     * Synchronous analysis of a document (without linking).
     *
     * @param session used to create in-memory prefetched entity suggestions if available in the analysis engine
     *            response.
     * @param doc the document to analyze (must be attached to an active core session to extract the text content)
     * @return Occurrence and properties suggestions
     * @throws ClientException if the text extraction fails
     * @throws IOException if the engine is not reachable or fails
     */
    AnalysisResults analyze(CoreSession session, DocumentModel doc) throws IOException;

    /**
     * Asynchronously save the result of the analyze using a dedicated sequential thread.
     *
     * @param task a DTO that contains the reference of a document and the occurrence groups to link to it
     */
    void scheduleSerializationTask(SerializationTask task);

    /**
     * Launch an asynchronous analysis of a document. The result of the analysis is stored directly in the Nuxeo
     * repository, usually as relationship to semantic entities, other documents or updated properties.
     *
     * @param repositoryName the repository where the document is stored
     * @param docRef the reference of the document to analyze
     * @throws ClientException if a property of the document to analyze is not available due to a database connection
     *             issue for instance.
     */
    void launchAnalysis(String repositoryName, DocumentRef docRef);

    /**
     * Launch a analysis of a document and wait for the result before returning. The result of the analysis is stored
     * directly in the Nuxeo repository, usually as relationship to semantic entities, other documents or updated
     * properties.
     *
     * @param doc the document to analyze
     * @throws ClientException if a property of the document to analyze is not available due to a database connection
     *             issue for instance.
     * @throws IOException if the connection to the analysis engine fails, or if the engine it-self fails
     * @throws DereferencingException if the dereferencing process fails (e.g. due to a network failure to a remote
     *             knowledge base).
     */
    void launchSynchronousAnalysis(DocumentModel doc, CoreSession session) throws
            DereferencingException, IOException;

    /**
     * Return a description of the state of the analysis of the document or null if no analysis is in progress.
     *
     * @param docRef
     * @return state description or null
     */
    ProgressStatus getProgressStatus(String repositoryName, DocumentRef docRef);

    /**
     * Mark the status as complete (even though the document might still be under processing).
     */
    void clearProgressStatus(String repositoryName, DocumentRef docRef);

    /**
     * Save semantic links in the repository to materialize the occurrence relationships between documents and entities
     *
     * @throws IOException if the remote entity sources fail during lookups
     * @throws ClientException if the document repository fails during local lookups or saving entities and occurrences
     */
    void createLinks(DocumentModel document, CoreSession session, List<OccurrenceGroup> occurrenceGroups)
            throws IOException;

}
