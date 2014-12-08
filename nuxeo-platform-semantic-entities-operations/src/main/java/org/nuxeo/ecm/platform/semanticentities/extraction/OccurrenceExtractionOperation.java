package org.nuxeo.ecm.platform.semanticentities.extraction;

/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     ogrisel
 */

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentRefList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.semanticentities.SemanticAnalysisService;
import org.nuxeo.runtime.api.Framework;

/**
 * Use a semantic engine to extract the occurrences of semantic entities from the text content of a document. The
 * semantic engine is assumed to accept an HTTP POST request on a fixed URL and synchronously return the result of the
 * analysis as an RDF/XML graph in the body of the response. The label, type and context text snippet of each occurrence
 * is then extracted by performing a configurable SPARQL query on the resulting RDF model loaded in a temporary RDF
 * graph. This pattern should work for semantic engines such as:
 * <ul>
 * <li>Stanbol from the project http://incubator.apache.org/stanbol</li>
 * <li>OpenCalais (untested)</li>
 * <li>Maybe more</li>
 * </ul>
 *
 * @author <a href="mailto:ogrisel@nuxeo.com">Olivier Grisel</a>
 */
@Operation(id = OccurrenceExtractionOperation.ID, category = org.nuxeo.ecm.automation.core.Constants.CAT_DOCUMENT, label = "Extract occurrences", description = "Extract the text and launch an use a semantic engine to extract and link occurrences of semantic entities. Returns back the analyzed document.")
public class OccurrenceExtractionOperation {

    public static final String ID = "Document.ExtractSemanticEntitiesOccurrences";

    protected SemanticAnalysisService saService;

    public OccurrenceExtractionOperation() {
        try {
            saService = Framework.getService(SemanticAnalysisService.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OccurrenceExtractionOperation(CoreSession session) {
        this();
        this.session = session;
    }

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentRef run(DocumentRef docRef) throws Exception {
        DocumentModel doc = session.getDocument(docRef);
        doc = run(doc);
        return doc.getRef();
    }

    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws Exception {
        saService.launchSynchronousAnalysis(doc, session);
        return doc;
    }

    /**
     * Find the list of all textual occurrences of entities in the output of the Apache Stanbol engine.
     */

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) throws Exception {
        DocumentModelList result = new DocumentModelListImpl((int) docs.totalSize());
        for (DocumentModel doc : docs) {
            result.add(run(doc));
        }
        return result;
    }

    @OperationMethod
    public DocumentModelList run(DocumentRefList docRefs) throws Exception {
        DocumentModelList result = new DocumentModelListImpl((int) docRefs.totalSize());
        for (DocumentRef docRef : docRefs) {
            result.add(session.getDocument(run(docRef)));
        }
        return result;
    }

}
