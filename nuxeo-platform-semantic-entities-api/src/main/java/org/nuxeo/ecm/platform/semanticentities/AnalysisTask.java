package org.nuxeo.ecm.platform.semanticentities;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Thread pooled call to the semantic analysis engine
 */
public class AnalysisTask implements Runnable {

    private static final Log log = LogFactory.getLog(AnalysisTask.class);

    protected final String repositoryName;

    protected final DocumentRef docRef;

    protected final SemanticAnalysisService service;

    public AnalysisTask(String repositoryName, DocumentRef docRef, SemanticAnalysisService service) {
        this.repositoryName = repositoryName;
        this.docRef = docRef;
        this.service = service;
    }

    public boolean isServiceActiveOrWarn() {
        if (!service.isActive()) {
            log.warn(String.format("%s has been disabled, skipping analysis for %s:%s", service.getClass(),
                    repositoryName, docRef));
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        if (!isServiceActiveOrWarn()) {
            return;
        }
        boolean isTransactionActive = TransactionHelper.startTransaction();
        try {
            try (CoreSession session = CoreInstance.openCoreSession(repositoryName)) {
                if (!session.exists(docRef)) {
                    log.info(String.format("Document %s:%s has been deleted, skipping semantic analysis.",
                            repositoryName, docRef));
                    service.clearProgressStatus(repositoryName, docRef);
                    return;
                }
                if (!isServiceActiveOrWarn()) {
                    return;
                }
                AnalysisResults results = service.analyze(session, session.getDocument(docRef));
                if (results.isEmpty()) {
                    service.clearProgressStatus(repositoryName, docRef);
                    return;
                }
                SerializationTask task = new SerializationTask(repositoryName, docRef, results, service);
                if (!isServiceActiveOrWarn()) {
                    return;
                }
                service.scheduleSerializationTask(task);
            } catch (Exception e) {
                service.clearProgressStatus(repositoryName, docRef);
                if (isTransactionActive) {
                    TransactionHelper.setTransactionRollbackOnly();
                }
                log.error(e.getMessage(), e);
            }
        } finally {
            if (isTransactionActive) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object) Override to make it possible to lookup the position of a document
     * in the queue and remove duplicates
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof AnalysisTask) {
            AnalysisTask otherTask = (AnalysisTask) o;
            return repositoryName.equals(otherTask.repositoryName) && docRef.equals(((AnalysisTask) o).docRef);
        } else if (o instanceof DocumentLocation) {
            DocumentLocation otherLocation = (DocumentLocation) o;
            return repositoryName.equals(otherLocation.getServerName()) && docRef.equals(otherLocation.getDocRef());
        }
        return false;
    }

}
