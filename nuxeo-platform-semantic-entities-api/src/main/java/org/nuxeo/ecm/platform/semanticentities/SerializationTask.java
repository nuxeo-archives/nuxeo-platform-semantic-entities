package org.nuxeo.ecm.platform.semanticentities;

import java.util.List;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;
import org.nuxeo.ecm.platform.semanticentities.AnalysisResults;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Data transfer object use to serialize the core write access link creation
 * using a BlockingQueue
 */
public class SerializationTask implements Runnable {

    private static final Log log = LogFactory.getLog(SerializationTask.class);

    protected final String repositoryName;

    protected final DocumentRef docRef;

    protected final AnalysisResults results;

    protected final DocumentLocation location;

    protected final SemanticAnalysisService service;

    protected final RepositoryManager manager;

    public SerializationTask(String repositoryName, DocumentRef docRef,
            AnalysisResults results,
            SemanticAnalysisService service) {
        this.repositoryName = repositoryName;
        this.docRef = docRef;
        this.results = results;
        location = new DocumentLocationImpl(repositoryName, docRef);
        this.service = service;
        try {
            manager = Framework.getService(RepositoryManager.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public DocumentRef getDocumentRef() {
        return docRef;
    }

    public List<OccurrenceGroup> getOccurrenceGroups() {
        return results.groups;
    }

    public DocumentLocation getDocumentLocation() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SerializationTask) {
            SerializationTask otherTask = (SerializationTask) o;
            return repositoryName.equals(otherTask.repositoryName)
                    && docRef.equals(((SerializationTask) o).docRef);
        } else if (o instanceof DocumentLocation) {
            DocumentLocation otherLocation = (DocumentLocation) o;
            return repositoryName.equals(otherLocation.getServerName())
                    && docRef.equals(otherLocation.getDocRef());
        }
        return false;
    }

    public boolean isServiceActiveOrWarn() {
        if (!service.isActive()) {
            log.warn(String.format(
                    "%s has been disabled, skipping analysis for %s:%s",
                    service.getClass(), repositoryName, docRef));
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
        LoginContext lc = null;
        try {
            lc = Framework.login();
            CoreSession session = manager.getRepository(repositoryName).open();
            try {
                if (!isServiceActiveOrWarn()) {
                    return;
                }
                DocumentModel doc = session.getDocument(docRef);
                results.savePropertiesToDocument(session, doc);
                service.createLinks(doc, session,
                        results.groups);
            } finally {
                Repository.close(session);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (isTransactionActive) {
                TransactionHelper.setTransactionRollbackOnly();
            }
        } finally {
            if (lc != null) {
                try {
                    lc.logout();
                } catch (LoginException e) {
                    log.error(e, e);
                }
            }
            if (isTransactionActive) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            service.clearProgressStatus(repositoryName, docRef);
        }
    }
}
