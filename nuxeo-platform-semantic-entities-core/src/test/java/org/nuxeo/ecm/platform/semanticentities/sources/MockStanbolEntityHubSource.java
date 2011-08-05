package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockStanbolEntityHubSource extends DBpediaEntitySource {

    URI BO_THUMB_URI = URI.create("http://upload.wikimedia.org/wikipedia/en/thumb/e/e9/Official_portrait_of_Barack_Obama.jpg/200px-Official_portrait_of_Barack_Obama.jpg");

    @Override
    protected InputStream fetchResourceAsStream(URI uri, String format)
            throws MalformedURLException, IOException {

        if (uri.equals(BO_THUMB_URI)) {
            return getClass().getResourceAsStream(
                    "/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        } else if (uri.toString().contains("?id=")) {
            int lastSlash = uri.getQuery().lastIndexOf("/");
            if (lastSlash > 0) {
                String fileName = uri.getQuery().substring(lastSlash + 1)
                        + ".json";
                return getClass().getResourceAsStream(
                        "/mock_replies/" + fileName);
            }
        }
        throw new IllegalArgumentException(
                "no mock resource registered for URI " + uri);
    }

    @Override
    protected InputStream fetchSuggestions(String keywords, int maxSuggestions)
            throws IOException {
        // TODO:
        return null;
    }

}
