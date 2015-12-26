/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public class MockSemanticAnalysisServiceImpl extends SemanticAnalysisServiceImpl {

    @Override
    public String callSemanticEngine(String textContent, String outputFormat, int retry) throws IOException {
        InputStream is = getClass().getResourceAsStream("/mock_replies/engine-output.rdf.xml");
        return IOUtils.toString(is, "UTF-8");
    }

}
