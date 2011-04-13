package org.nuxeo.ecm.platform.semanticentities;

import java.io.IOException;
import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;

/**
 * Service to asynchronously launch and monitor the semantic analysis of Nuxeo
 * documents.
 */
public interface SemanticAnalysisService {

    /**
     * Synchronous analysis of pre-extracted text content (without linking).
     *
     * @param textContent the text to send to the engines
     * @return Occurrence suggestions
     * @throws IOException if the engine is not reachable or fails
     */
    List<OccurrenceGroup> analyze(String textContent) throws IOException;

    /**
     * Synchronous analysis of a document (without linking).
     *
     * @param doc the document to analyze (must be attached to an active core
     *            session to extract the text content)
     * @return Occurrence suggestions
     * @throws ClientException if the text extraction fails
     * @throws IOException if the engine is not reachable or fails
     */
    List<OccurrenceGroup> analyze(DocumentModel doc) throws IOException,
            ClientException;

    /**
     * Asynchronously save the result of the analyze using a dedicated
     * sequential thread.
     *
     * @param task a DTO that contains the reference of a document and the
     *            occurrence groups to link to it
     */
    void scheduleSerializationTask(SerializationTask task);

    /**
     * Launch an asynchronous analysis of a document. The result of the analysis
     * is stored directly in the Nuxeo repository, usually as relationship to
     * semantic entities, other documents or updated properties.
     *
     * @param repositoryName the repository where the document is stored
     * @param docRef the reference of the document to analyze
     * @throws ClientException if a property of the document to analyze is not
     *             available due to a database connection issue for instance.
     */
    void launchAnalysis(String repositoryName, DocumentRef docRef)
            throws ClientException;

    /**
     * Launch a analysis of a document and wait for the result before returning.
     * The result of the analysis is stored directly in the Nuxeo repository,
     * usually as relationship to semantic entities, other documents or updated
     * properties.
     *
     * @param doc the document to analyze
     * @throws ClientException if a property of the document to analyze is not
     *             available due to a database connection issue for instance.
     * @throws IOException if the connection to the analysis engine fails, or if
     *             the engine it-self fails
     * @throws DereferencingException if the dereferencing process fails (e.g.
     *             due to a network failure to a remote knowledge base).
     */
    void launchSynchronousAnalysis(DocumentModel doc, CoreSession session)
            throws ClientException, DereferencingException, IOException;

    /**
     * Return a description of the state of the analysis of the document or null
     * if no analysis is in progress.
     *
     * @param docRef
     * @return state description or null
     */
    ProgressStatus getProgressStatus(String repositoryName, DocumentRef docRef);

    /**
     * Mark the status as complete (even though the document might still be
     * under processing).
     */
    void clearProgressStatus(String repositoryName, DocumentRef docRef);

}
