/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.jsf.actions;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.runtime.api.Framework;

@Name("semanticEntitiesActions")
@Scope(ScopeType.CONVERSATION)
public class SemanticEntitiesActions {

    @In(create = true)
    protected NavigationContext navigationContext;

    @In(required = false)
    protected CoreSession documentManager;

    protected LocalEntityService leService;

    protected LocalEntityService getLocalEntityService() throws Exception {
        if (leService == null) {
            leService = Framework.getService(LocalEntityService.class);
        }
        return leService;
    }

    @Factory(scope = ScopeType.SESSION, value = "canBrowseEntityContainer")
    public boolean getCanBrowseEntityContainer() throws Exception {
        // the ScopeType.SESSION scope might hide change in permissions on the
        // entity container unless affected users do logout but this is
        // necessary to avoid DB requests at each page view since the
        // canBrowseEntityContainer variable is used to filter an action that
        // shows up in the top level banner
        return getLocalEntityService().getEntityContainer(documentManager) != null;
    }

    public String goToEntityContainer() throws Exception {
        DocumentModel entityContainer = getLocalEntityService().getEntityContainer(
                documentManager);
        if (entityContainer == null) {
            // the user does not have the permission to browse the entities
            return null;
        }
        return navigationContext.navigateToDocument(entityContainer);
    }
}
