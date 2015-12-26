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

import org.nuxeo.runtime.test.runner.LocalDeploy;

//deploy off-line mock DBpedia source to override the default source that needs an internet connection:
//comment the following contrib to test again the real DBpedia server
@LocalDeploy("org.nuxeo.ecm.platform.semanticentities.core.tests:OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml")
public class DBpediaEntitySourceTest extends RemoteEntityServiceTest {

}
