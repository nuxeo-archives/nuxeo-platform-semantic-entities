package org.nuxeo.ecm.platform.semanticentities;

import java.net.URI;

/**
 * Data transfer object for the name lookup API of the RemoteEntityService
 */
public class RemoteEntitySuggestion {

    public final String label;

    public final URI uri;

    public RemoteEntitySuggestion(String label, URI uri) {
        this.label = label;
        this.uri = uri;
    }

}
