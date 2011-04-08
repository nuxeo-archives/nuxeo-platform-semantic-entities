package org.nuxeo.ecm.platform.semanticentities;

import java.io.IOException;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.platform.semanticentities.SemanticAnalysisService;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceGroup;

/**
 * Thread pooled call to the semantic analysis engine
 */
public class AnalysisTask implements Runnable {

    protected final String repositoryName;
    protected final DocumentRef docRef;
    protected final String textContent;
    protected final SemanticAnalysisService service;

    public AnalysisTask(String repositoryName,
                        DocumentRef docRef,
                        String textContent,
                        SemanticAnalysisService service) {
        this.repositoryName = repositoryName;
        this.docRef = docRef;
        this.textContent = textContent;
        this.service = service;
    }

    @Override
    public void run() {
        try {
            List<OccurrenceGroup> occurrenceGroups = service.analyze(textContent);
            SerializationTask task = new SerializationTask(repositoryName, docRef, occurrenceGroups);
            service.scheduleSerializationTask(task);
        } catch (IOException e) {
            service.clearProgressStatus(docRef);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AnalysisTask) {
            AnalysisTask otherTask = (AnalysisTask) o;
            return repositoryName.equals(otherTask.repositoryName)
                   && docRef.equals(((AnalysisTask) o).docRef);
        }
        return false;
    }

}
