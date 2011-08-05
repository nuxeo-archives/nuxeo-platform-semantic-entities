package org.nuxeo.ecm.platform.semanticentities.service;

public class DBpediaEntitySourceTest extends RemoteEntityServiceTest {

    @Override
    protected void deployRemoteEntityServiceOverride() throws Exception {
        // deploy off-line mock DBpedia source to override the default source
        // that needs an internet connection: comment the following contrib to
        // test again the real DBpedia server
        deployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                "OSGI-INF/test-semantic-entities-remote-entity-contrib.xml");
    }

}
