package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockStanbolEntityHubSource extends StanbolEntityHubSource {

    static final String DBPEDIA_PREFIX = "http://dbpedia.org/resource/"; 
    
    URI BO_THUMB_URI = URI.create("http://upload.wikimedia.org/wikipedia/en/thumb/e/e9/Official_portrait_of_Barack_Obama.jpg/200px-Official_portrait_of_Barack_Obama.jpg");

    @Override
    protected InputStream fetchResourceAsStream(URI uri, String format)
            throws MalformedURLException, IOException {

        if (uri.equals(BO_THUMB_URI)) {
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
