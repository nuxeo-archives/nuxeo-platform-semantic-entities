package org.nuxeo.ecm.platform.semanticentities;

import java.util.List;

import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;

/**
 * Data transfer object use to serialize the core write access link creation using a BlockingQueue
 */
public class SerializationTask {

    protected final String repositoryName;
    protected final DocumentRef docRef;
    protected final List<OccurrenceGroup> occurrenceGroups;

    public SerializationTask(String repositoryName, DocumentRef docRef, List<OccurrenceGroup> occurrenceGroups) {
        this.repositoryName = repositoryName;
        this.docRef = docRef;
        this.occurrenceGroups = occurrenceGroups;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public DocumentRef getDocumentRef() {
        return docRef;
    }

    public List<OccurrenceGroup> getOccurrenceGroups() {
        return occurrenceGroups;
    }

    public boolean isLastTask() {
        // TODO implement me: special last task marker for closing the serialization thread cleanly
        return false;
    }

}
