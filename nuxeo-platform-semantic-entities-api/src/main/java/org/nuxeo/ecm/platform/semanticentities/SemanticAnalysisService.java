package org.nuxeo.ecm.platform.semanticentities;

import java.io.IOException;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * Service to asynchronously launch and monitor the semantic analysis of Nuxeo documents.
 */
public interface SemanticAnalysisService {

    public static final String STATUS_ANALYSIS_PENDING = "status.semantic.analysisPending";

    public static final String STATUS_LINKING_PENDING = "status.semantic.linkingPending";

    /**
     * Launch an asynchronous analysis of a document. The result of the analysis is stored directly in the
     * Nuxeo repository, usually as relationship to semantic entities, other documents or updated properties.
     * 
     * @param doc
     *            the document to analyze
     * @throws ClientException
     *             if a property of the document to analyze is not available due to a database connection
     *             issue for instance.
     */
    void launchAnalysis(DocumentModel doc) throws ClientException;

    /**
     * Launch a analysis of a document and wait for the result before returning. The result of the analysis is
     * stored directly in the Nuxeo repository, usually as relationship to semantic entities, other documents
     * or updated properties.
     * 
     * @param doc
     *            the document to analyze
     * @throws ClientException
     *             if a property of the document to analyze is not available due to a database connection
     *             issue for instance.
     * @throws IOException
     *             if the connection to the analysis engine fails, or if the engine it-self fails
     * @throws DereferencingException
     *             if the dereferencing process fails (e.g. due to a network failure to a remote knowledge
     *             base).
     */
    void launchSynchronousAnalysis(DocumentModel doc, CoreSession session) throws ClientException,
                                                                          DereferencingException,
                                                                          IOException;

    /**
     * Return a description of the state of the analysis of the document or null if no analysis is in
     * progress.
     * 
     * @param docRef
     * @return state description or null
     */
    String getProgressStatus(DocumentRef docRef);

}
