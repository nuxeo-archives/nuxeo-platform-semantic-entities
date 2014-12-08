package org.nuxeo.ecm.platform.semanticentities.jsf.actions;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;

/**
 * Data transfer object that provides all the required data to build JSF views to introspect the details of an
 * occurrence and review it.
 */
public class EntityOccurrence {

    protected final DocumentModel documentModel;

    protected final DocumentModel entityModel;

    protected final DocumentModel occurrenceModel;

    protected int maxDisplayedOccurences;

    protected final List<OccurrenceInfo> occurrences = new ArrayList<OccurrenceInfo>();

    public EntityOccurrence(DocumentModel doc, DocumentModel entity, DocumentModel occurrence,
            List<OccurrenceInfo> occurrences, int maxDisplayedOccurrences) {
        documentModel = doc;
        entityModel = entity;
        occurrenceModel = occurrence;
        this.occurrences.addAll(occurrences);
        maxDisplayedOccurences = maxDisplayedOccurrences;
    }

    // Getters for Seam / JSF

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public DocumentModel getEntityModel() {
        return entityModel;
    }

    public DocumentModel getOccurrenceModel() {
        return occurrenceModel;
    }

    public List<OccurrenceInfo> getOccurrences() {
        if (occurrences.size() > maxDisplayedOccurences) {
            return occurrences.subList(0, maxDisplayedOccurences);
        }
        return occurrences;
    }

    public int getExtractOccurrences() {
        int extra = occurrences.size() - maxDisplayedOccurences;
        return extra > 0 ? extra : 0;
    }
}
