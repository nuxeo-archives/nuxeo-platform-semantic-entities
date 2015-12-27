/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.semanticentities;

import java.io.IOException;

/**
 * Exception to be thrown when a URI cannot be successfully dereferenced due to a network or remote server problem.
 *
 * @author Olivier Grisel <ogrisel@nuxeo.com>
 */
public class DereferencingException extends IOException {

    private static final long serialVersionUID = 1L;

    public DereferencingException(String message) {
        super(message);
    }

    public DereferencingException(Throwable t) {
        super(t);
    }

    public DereferencingException(String message, Throwable t) {
        super(message, t);
    }

}
