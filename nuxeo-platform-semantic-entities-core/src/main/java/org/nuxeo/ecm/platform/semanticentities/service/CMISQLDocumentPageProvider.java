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
import org.nuxeo.ecm.core.api.AbstractPageProvider;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.PageProvider;
import org.nuxeo.ecm.core.chemistry.impl.CMISQLQueryMaker;
import org.nuxeo.ecm.core.chemistry.impl.NuxeoConnection;
import org.nuxeo.ecm.core.chemistry.impl.NuxeoRepository;

/**
 * Simple PageProvider implementation that uses the CMISQL api to be able to
 * perform paginated joins. The CMISQL SELECT statement is expected to select a
 * unique attribute that is the document IdRef (a.k.a. cmis:objectId) of the
 * result.
 *
 * @author ogrisel
 */
public class CMISQLDocumentPageProvider extends
        AbstractPageProvider<DocumentModel> implements
        PageProvider<DocumentModel> {

    public static final Log log = LogFactory.getLog(CMISQLDocumentPageProvider.class);

    private static final long serialVersionUID = 1L;

    protected final CoreSession session;

    protected final String query;

    protected final String docIdColumnName;

    protected List<DocumentModel> currentPageDocumentModels;

    public CMISQLDocumentPageProvider(CoreSession session, String query,
            String docIdColumnName) {
        this.session = session;
        this.query = query;
        this.docIdColumnName = docIdColumnName;
        pageSize = 10;
    }

    @Override
    public List<DocumentModel> getCurrentPage() {
        if (currentPageDocumentModels == null) {
            currentPageDocumentModels = new ArrayList<DocumentModel>();
            IterableQueryResult result = null;

            NuxeoRepository rep = new NuxeoRepository(
                    session.getRepositoryName());
            NuxeoConnection conn = (NuxeoConnection) rep.getConnection(null);
            try {
                result = session.queryAndFetch(query, CMISQLQueryMaker.TYPE,
                        conn);
                resultsCount = result.size();
                if (offset < resultsCount) {
                    result.skipTo(offset);
                }
                Iterator<Map<String, Serializable>> it = result.iterator();
                int pos = 0;
                while (it.hasNext() && pos < pageSize) {
                    pos += 1;
                    Map<String, Serializable> selectedAttributes = it.next();
                    DocumentRef docRef = new IdRef(selectedAttributes.get(
                            docIdColumnName).toString());
                    DocumentModel doc = session.getDocument(docRef);
                    currentPageDocumentModels.add(doc);
                }
            } catch (Exception e) {
                throw new ClientRuntimeException(e);
            } finally {
                conn.close();
                if (result != null) {
                    result.close();
                }
            }
        }
        return currentPageDocumentModels;
    }

    @Override
    public void refresh() {
        super.refresh();
        currentPageDocumentModels = null;
    }
}
