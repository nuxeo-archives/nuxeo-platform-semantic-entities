package org.nuxeo.ecm.platform.semanticentities;

import java.util.List;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Thread pooled call to the semantic analysis engine
 */
public class AnalysisTask implements Runnable {

    private static final Log log = LogFactory.getLog(AnalysisTask.class);

    protected final String repositoryName;

    protected final DocumentRef docRef;

    protected final SemanticAnalysisService service;

    protected final RepositoryManager manager;

    public AnalysisTask(String repositoryName, DocumentRef docRef,
            SemanticAnalysisService service) {
        this.repositoryName = repositoryName;
        this.docRef = docRef;
        this.service = service;
        try {
            manager = Framework.getService(RepositoryManager.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        if (!service.isActive()) {
            return;
        }
        TransactionHelper.startTransaction();
        LoginContext lc = null;
        try {
            lc = Framework.login();
            CoreSession session = manager.getRepository(repositoryName).open();
            if (!session.exists(docRef)) {
                // document was deleted
                service.clearProgressStatus(repositoryName, docRef);
                return;
            }
            try {
                List<OccurrenceGroup> occurrenceGroups = service.analyze(session.getDocument(docRef));
                if (occurrenceGroups.isEmpty()) {
                    service.clearProgressStatus(repositoryName, docRef);
                    return;
                }
                SerializationTask task = new SerializationTask(repositoryName,
                        docRef, occurrenceGroups, service);
                service.scheduleSerializationTask(task);
            } finally {
                Repository.close(session);
            }
        } catch (Exception e) {
            service.clearProgressStatus(repositoryName, docRef);
            TransactionHelper.setTransactionRollbackOnly();
            log.error(e.getMessage(), e);
        } finally {
            if (lc != null) {
                try {
                    lc.logout();
                } catch (LoginException e) {
                    log.error(e, e);
                }
            }
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     * 
     * Override to make it possible to lookup the position of a document in the
     * queue and remove duplicates
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof AnalysisTask) {
            AnalysisTask otherTask = (AnalysisTask) o;
            return repositoryName.equals(otherTask.repositoryName)
                    && docRef.equals(((AnalysisTask) o).docRef);
        } else if (o instanceof DocumentLocation) {
            DocumentLocation otherLocation = (DocumentLocation) o;
            return repositoryName.equals(otherLocation.getServerName())
                    && docRef.equals(otherLocation.getDocRef());
        }
        return false;
    }

}
