/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.extraction;

import java.io.IOException;

/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        saService = Framework.getService(SemanticAnalysisService.class);
    }

    public OccurrenceExtractionOperation(CoreSession session) {
        this();
        this.session = session;
    }

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentRef run(DocumentRef docRef) {
        DocumentModel doc = session.getDocument(docRef);
        doc = run(doc);
        return doc.getRef();
    }

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        try {
            saService.launchSynchronousAnalysis(doc, session);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return doc;
    }

    /**
     * Find the list of all textual occurrences of entities in the output of the Apache Stanbol engine.
     */

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) {
        DocumentModelList result = new DocumentModelListImpl((int) docs.totalSize());
        for (DocumentModel doc : docs) {
            result.add(run(doc));
        }
        return result;
    }

    @OperationMethod
    public DocumentModelList run(DocumentRefList docRefs) {
        DocumentModelList result = new DocumentModelListImpl((int) docRefs.totalSize());
        for (DocumentRef docRef : docRefs) {
            result.add(session.getDocument(run(docRef)));
        }
        return result;
    }

}
