package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntitySource;

/**
 * Abstract base class to be used by all contributions to the
 * RemoteEntityServiceImpl service.
 *
 * Factorize common mapping logic and offer public methods to help the service
 * set parameters from the descriptor.
 */
public abstract class ParameterizedHTTPEntitySource implements
        RemoteEntitySource {
    
    public static final String OWL_THING = "http://www.w3.org/2002/07/owl#Thing";

    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    protected RemoteEntitySourceDescriptor descriptor;

    protected HttpClient httpClient;

    public void setDescriptor(RemoteEntitySourceDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public boolean canDereference(URI remoteEntity) {
        return remoteEntity.toString().startsWith(descriptor.getUriPrefix());
    }

    protected void initHttpClient() {
        // Create and initialize a scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http",
                PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https",
                SSLSocketFactory.getSocketFactory(), 443));
    
        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        HttpParams params = new BasicHttpParams();
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
                params, schemeRegistry);
    
        httpClient = new DefaultHttpClient(cm, params);
    }

    protected InputStream doHttpGet(URI uri, String accept) throws IOException {
        HttpGet get = new HttpGet(uri);
        try {
            if (accept != null) {
                get.setHeader("Accept", accept);
            }
            HttpResponse response = httpClient.execute(get);
            if (response.getStatusLine().getStatusCode() == 200) {
                return response.getEntity().getContent();
            } else {
                String errorMsg = String.format("Error resolving '%s' : ",
                        uri);
                errorMsg += response.getStatusLine().toString();
                throw new IOException(errorMsg);
            }
        } catch (ClientProtocolException e) {
            get.abort();
            throw e;
        } catch (IOException e) {
            get.abort();
            throw e;
        }
    }

    protected InputStream doHttpPost(URI uri, String accept,
            String contentType, String payload) throws IOException {
        HttpPost post = new HttpPost(uri);
        try {
            if (accept != null) {
                post.setHeader("Accept", accept);
            }
            if (contentType != null) {
                post.setHeader("Content-Type", contentType);
            }
            if (payload != null) {
                HttpEntity entity = new StringEntity(payload, "UTF-8");
                post.setEntity(entity);
            }
            HttpResponse response = httpClient.execute(post);
            if (response.getStatusLine().getStatusCode() == 200) {
                return response.getEntity().getContent();
            } else {
                String errorMsg = String.format(
                        "Error querying '%s' with payload '%s': ", uri, payload);
                errorMsg += response.getStatusLine().toString();
                throw new IOException(errorMsg);
            }
        } catch (ClientProtocolException e) {
            post.abort();
            throw e;
        } catch (IOException e) {
            post.abort();
            throw e;
        }
    }

    @Override
    public boolean canSuggestRemoteEntity() {
        return true;
    }

}
