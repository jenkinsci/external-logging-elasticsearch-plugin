/*
 * The MIT License
 *
 * Copyright 2014-2018 Barnes and Noble College, Liam Newman,
 * CloudBees Inc., and other Jenkins contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.extlogging.elasticsearch.util;

import com.google.common.collect.Range;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.google.common.collect.Ranges.closedOpen;


/**
 * Elastic Search Data Access Object.
 * Originally the implementation was in the Elasticsearch plugin,
 * but is has been copied here.
 * The main difference is that this implementation is serializable.
 * @author Liam Newman
 * @author Oleg Nenashev
 */
//TODO: fix before the release
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "To be set before the release")
public class ElasticSearchDao implements Serializable {

    private static final Range<Integer> SUCCESS_CODES = closedOpen(200,300);

    private final URI uri;
    @CheckForNull
    private String username;
    @CheckForNull
    private String password;
    @CheckForNull
    private String mimeType;

    @CheckForNull
    private transient HttpClientBuilder clientBuilder;

    @CheckForNull
    private transient String auth;

    //primary constructor used by indexer factory
    public ElasticSearchDao(URI uri, String username, String password) {
        this(null, uri, username, password);
    }

    // Factored for unit testing
    public ElasticSearchDao(@CheckForNull HttpClientBuilder factory,
                            @Nonnull URI uri,
                            @CheckForNull String username,
                            @CheckForNull String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;

        try {
            uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }

        clientBuilder = factory;
    }

    @Nonnull
    public HttpClientBuilder getClientBuilder() {
        if (clientBuilder == null) {
            clientBuilder = HttpClientBuilder.create();
        }
        return clientBuilder;
    }

    public URI getUri() {
        return uri;
    }

    public String getHost() {
        return uri.getHost();
    }

    public String getScheme() {
        return uri.getScheme();
    }

    public int getPort() {
        return uri.getPort();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getKey()
    {
        return uri.getPath();
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @CheckForNull
    public String getAuth() {
        if (auth == null && StringUtils.isNotBlank(username)) {
            auth = Base64.encodeBase64String((username + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8));
        }
        return auth;
    }

    HttpPost getHttpPost(String data) {
        HttpPost postRequest = new HttpPost(uri);
        String mimeType = this.getMimeType();
        // char encoding is set to UTF_8 since this request posts a JSON string
        StringEntity input = new StringEntity(data, StandardCharsets.UTF_8);
        mimeType = (mimeType != null) ? mimeType : ContentType.APPLICATION_JSON.toString();
        input.setContentType(mimeType);
        postRequest.setEntity(input);
        if (auth != null) {
            postRequest.addHeader("Authorization", "Basic " + auth);
        }
        return postRequest;
    }

    public void push(String data) throws IOException {

        HttpPost post = getHttpPost(data);
        try(CloseableHttpClient httpClient = getClientBuilder().build();
            CloseableHttpResponse response = httpClient.execute(post)) {

            if (!SUCCESS_CODES.contains(response.getStatusLine().getStatusCode())) {
                throw new IOException(HttpGetWithData.getErrorMessage(uri, response));
            }
        }
    }


    public String getDescription()
    {
        return uri.toString();
    }
}
