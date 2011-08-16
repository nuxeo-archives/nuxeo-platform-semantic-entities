package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockStanbolEntityHubSource extends StanbolEntityHubSource {

    static final String DBPEDIA_PREFIX = "http://dbpedia.org/resource/"; 
    
    @Override
    protected InputStream fetchResourceAsStream(URI uri, String format)
            throws MalformedURLException, IOException {

        if (uri.toString().endsWith("Obama.jpg")) {
            return getClass().getResourceAsStream(
                    "/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        } else if (uri.toString().startsWith(DBPEDIA_PREFIX)) {
            String fileName = uri.toString().substring(DBPEDIA_PREFIX.length())
                    + ".json";
            return getClass().getResourceAsStream("/mock_replies/" + fileName);
        }
        throw new IllegalArgumentException(
                "no mock resource registered for URI " + uri);
    }

    protected InputStream fetchSuggestions(String keywords, int maxSuggestions)
            throws IOException {
        // TODO:
        return null;
    }

}
