package io.jenkins.plugins.extlogging.elasticsearch.util;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.HTTP;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Restricted(NoExternalUse.class)
public class HttpGetWithData extends HttpGet implements HttpEntityEnclosingRequest {
    private HttpEntity entity;

    public HttpGetWithData(String uri) {
        super(uri);
    }

    @Override
    public HttpEntity getEntity() {
        return this.entity;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean expectContinue() {
        final Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
    }

    public static String getErrorMessage(URI uri, CloseableHttpResponse response) {
        ByteArrayOutputStream byteStream = null;
        PrintStream stream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8.name());

            try {
                stream.print("HTTP error code: ");
                stream.println(response.getStatusLine().getStatusCode());
                stream.print("URI: ");
                stream.println(uri.toString());
                stream.println("RESPONSE: " + response.toString());
                response.getEntity().writeTo(stream);
            } catch (IOException e) {
                stream.println(ExceptionUtils.getStackTrace(e));
            }
            stream.flush();
            return byteStream.toString(StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e)
        {
            return ExceptionUtils.getStackTrace(e);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}