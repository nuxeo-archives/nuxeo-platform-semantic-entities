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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoCmisService;

import org.nuxeo.ecm.platform.query.api.AbstractPageProvider;
import org.nuxeo.ecm.platform.query.api.PageProvider;

/**
 * Simple PageProvider implementation that uses the CMISQL api to be able to perform paginated joins. The CMISQL SELECT
 * statement is expected to select a unique attribute that is the document IdRef (a.k.a. cmis:objectId) of the result.
 *
 * @author ogrisel
 */
public class CMISQLDocumentPageProvider extends AbstractPageProvider<DocumentModel> implements
        PageProvider<DocumentModel> {

    public static final Log log = LogFactory.getLog(CMISQLDocumentPageProvider.class);

    private static final long serialVersionUID = 1L;

    protected final CoreSession session;

    protected final String query;

    protected final String docIdColumnName;

    protected List<DocumentModel> currentPageDocumentModels;

    public CMISQLDocumentPageProvider(CoreSession session, String query, String docIdColumnName, String providerName)
            {
        this.session = session;
        this.query = query;
        this.docIdColumnName = docIdColumnName;
        pageSize = 10;
        name = providerName;
    }

    @Override
    public List<DocumentModel> getCurrentPage() {
        if (currentPageDocumentModels == null) {
            currentPageDocumentModels = new ArrayList<DocumentModel>();
            IterableQueryResult result = null;

            NuxeoCmisService cmisService = new NuxeoCmisService(session);
            try {
                result = cmisService.queryAndFetch(query, true);
                resultsCount = result.size();
                if (offset < resultsCount) {
                    result.skipTo(offset);
                }
                Iterator<Map<String, Serializable>> it = result.iterator();
                int pos = 0;
                while (it.hasNext() && pos < pageSize) {
                    pos += 1;
                    Map<String, Serializable> selectedAttributes = it.next();
                    DocumentRef docRef = new IdRef(selectedAttributes.get(docIdColumnName).toString());
                    DocumentModel doc = session.getDocument(docRef);
                    currentPageDocumentModels.add(doc);
                }
            } finally {
                cmisService.close();
                if (result != null) {
                    result.close();
                }
            }
        }
        return currentPageDocumentModels;
    }

    @Override
    public void refresh() {
        currentPageDocumentModels = null;
        super.refresh();
    }
}
