package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockDBpediaEntitySource extends DBpediaEntitySource {

    URI BO_THUMB_URI = URI.create("http://upload.wikimedia.org/wikipedia/en/thumb/e/e9/Official_portrait_of_Barack_Obama.jpg/200px-Official_portrait_of_Barack_Obama.jpg");

    @Override
    protected InputStream doHttpGet(URI uri, String format) throws MalformedURLException, IOException {

        if (uri.equals(BO_THUMB_URI)) {
            return getClass().getResourceAsStream("/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        } else if (uri.toString().contains("CONSTRUCT")) {
            return getClass().getResourceAsStream("/mock_replies/barack_obama_sparql_reply.rdf.xml");
        } else {
            throw new IllegalArgumentException("no mock resource registered for URI " + uri);
        }
    }

    @Override
    protected InputStream fetchSuggestions(String keywords, int maxSuggestions) throws IOException {
        if (keywords.contains("Obama")) {
            return getClass().getResourceAsStream("/mock_replies/lookup-obama.xml");
        } else {
            return getClass().getResourceAsStream("/mock_replies/lookup-empty-resultset.xml");
        }
    }

}
