package org.nuxeo.ecm.platform.semanticentities.service;

import org.nuxeo.runtime.test.runner.LocalDeploy;

//deploy off-line mock DBpedia source to override the default source that needs an internet connection:
//comment the following contrib to test again the real DBpedia server
@LocalDeploy("org.nuxeo.ecm.platform.semanticentities.core.tests:OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml")
public class DBpediaEntitySourceTest extends RemoteEntityServiceTest {

}
