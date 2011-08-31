package org.nuxeo.ecm.platform.semanticentities.sources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class MockStanbolEntityHubSource extends StanbolEntityHubSource {

    static final String DBPEDIA_PREFIX = "http://dbpedia.org/resource/";

    @Override
    protected InputStream doHttpGet(URI uri, String format)
            throws MalformedURLException, IOException {

        if (uri.toString().endsWith("Obama.jpg")) {
            return getClass().getResourceAsStream(
                    "/mock_replies/200px-Official_portrait_of_Barack_Obama.jpg");
        }
        String query = uri.getQuery();
        if (query.startsWith("id=" + DBPEDIA_PREFIX)) {
            String fileName = query.substring(("id=" + DBPEDIA_PREFIX).length())
                    + ".json";
            return getClass().getResourceAsStream("/mock_replies/" + fileName);
        }
        throw new IllegalArgumentException(
                "no mock resource registered for URI " + uri);
    }

    @Override
    protected InputStream doHttpPost(URI uri, String accepted,
            String contentType, String payload) throws MalformedURLException,
            IOException {
        if (uri.toString().endsWith("entityhub/site/dbpedia/query")) {
            if (payload.toLowerCase().contains("obama")) {
                return getClass().getResourceAsStream(
                        "/mock_replies/obama_query_response.json");
            }
            // TODO: add mock replies for John Lennon and Liverpool instead
            return new ByteArrayInputStream("{'results': []}".getBytes());
        }
        throw new IllegalArgumentException(
                "no mock resource registered for URI " + uri + " and payload: "
                        + payload);
    }

}
