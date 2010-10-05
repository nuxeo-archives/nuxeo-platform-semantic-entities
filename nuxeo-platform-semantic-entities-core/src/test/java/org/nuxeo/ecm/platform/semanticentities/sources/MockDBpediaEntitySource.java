package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;

public class MockDBpediaEntitySource extends DBpediaEntitySource {

    @Override
    protected InputStream fetchSuggestions(String keywords, String type,
            int maxSuggestions) throws IOException {
        return MockDBpediaEntitySource.class.getResourceAsStream("/mock_replies/44th_US_president.xml");
    }

}
