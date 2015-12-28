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
