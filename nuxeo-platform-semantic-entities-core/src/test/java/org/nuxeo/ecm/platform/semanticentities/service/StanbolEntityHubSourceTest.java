package org.nuxeo.ecm.platform.semanticentities.service;

public class StanbolEntityHubSourceTest extends RemoteEntityServiceTest {

    @Override
    protected void deployRemoteEntityServiceOverride() throws Exception {
        // deploy off-line mock DBpedia source to override the default source
        // that needs an internet connection: comment the following contrib to
        // test again the real Stanbol server
        deployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                "OSGI-INF/test-semantic-entities-stanbol-entity-contrib.xml");
    }

}
