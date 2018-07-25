package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.console.AnnotatedLargeText;
import hudson.model.Run;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import io.jenkins.plugins.extlogging.api.util.UniqueIdHelper;
import io.jenkins.plugins.extlogging.elasticsearch.util.ElasticSearchDao;
import io.jenkins.plugins.extlogging.elasticsearch.util.JSONConsoleNotes;
import io.jenkins.plugins.extlogging.elasticsearch.util.HttpGetWithData;
import jenkins.model.logging.Loggable;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * Displays Embedded log.
 * @author Oleg Nenashev
 */
@Restricted(NoExternalUse.class)
public class ElasticsearchLogLargeTextProvider {

    @Nonnull
    private ElasticSearchDao esDao;

    @Nonnull
    private Loggable loggable;

    @CheckForNull
    private String stepId;

    public ElasticsearchLogLargeTextProvider(@Nonnull ElasticSearchDao dao, @Nonnull Loggable loggable) {
        this(dao, loggable, null);
    }

    public ElasticsearchLogLargeTextProvider(@Nonnull ElasticSearchDao dao, @Nonnull Loggable loggable, @CheckForNull String stepId) {
        this.esDao = dao;
        this.loggable = loggable;
        this.stepId = stepId;
    }

    private transient HttpClientBuilder clientBuilder;

    /**
     * Used from <tt>index.jelly</tt> to write annotated log to the given
     * output.
     * @param offset offset of the log
     * @param out destination output
     */
    public void writeLogTo(long offset, @Nonnull XMLOutput out) throws IOException {
        try {
            getLogText().writeHtmlTo(offset, out.asWriter());
        } catch (IOException e) {
            // try to fall back to the old getLogInputStream()
            // mainly to support .gz compressed files
            // In this case, console annotation handling will be turned off.
            InputStream input = readLogToBuffer(offset).newInputStream();
            try {
                IOUtils.copy(input, out.asWriter());
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }
    
    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     * @return A {@link Run} log with annotations
     */   
    public @Nonnull AnnotatedLargeText getLogText() {
        ByteBuffer buf;
        try {
            buf = readLogToBuffer(0);
        } catch (IOException ex) {
            buf = new ByteBuffer();
            //TODO: return new BrokenAnnotatedLargeText(ex);
        }
        return new UncompressedAnnotatedLargeText(buf, StandardCharsets.UTF_8, loggable.isLoggingFinished(), this);
    }
    
    /**
     * Returns an input stream that reads from the log file.
     * @throws IOException Operation error
     */
    @Nonnull 
    public ByteBuffer readLogToBuffer(long initialOffset) throws IOException {
        ByteBuffer buffer = new ByteBuffer();
        Writer wr = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                byte[] bytes = new String(cbuf).getBytes("UTF-8");
                buffer.write(bytes, off, len);
            }

            @Override
            public void flush() throws IOException {
                buffer.flush();
            }

            @Override
            public void close() throws IOException {
                buffer.close();
            }
        };
        pullLogs(wr, esDao,0, Long.MAX_VALUE);
        return buffer;
    }

    //TODO: Move to External Logging API
    private Map<String, String> produceMatchers() {
        Map<String, String> eqMatchers = new HashMap<>();
        if (loggable instanceof Run<?, ?>) {
            Run<?,?> run = (Run<?, ?>)loggable;
            eqMatchers.put("jobId", UniqueIdHelper.getOrCreateId(run.getParent()));
            eqMatchers.put("buildNum", Integer.toString(run.getNumber()));
        }

        if (stepId != null) {
            eqMatchers.put("stepId", stepId);
        }
        return eqMatchers;
    }

    private String getMatchQuery() {
        //TODO: Can be optimized a lot
        //TODO: Proper escaping to avoid query injection
        Map<String, String> matchers = produceMatchers();
        ArrayList<String> conditions = new ArrayList<>(matchers.size());
        for (Map.Entry<String, String> entry : matchers.entrySet()) {
            String filter = String.format("{ \"match\": { \"data.%s\": \"%s\"}}",
                    entry.getKey(), entry.getValue());
            conditions.add(filter);
        }
        return StringUtils.join(conditions, ",");
    }

    private void pullLogs(Writer writer, ElasticSearchDao dao, long sinceMs, long toMs) throws IOException {

        // Prepare query
        //TODO: stored_fields in ES5
        String query = "{\n" +
                "  \"fields\": [\"message\",\"@timestamp\"], \n" +
                "  \"size\": 9999, \n" + // TODO use paging https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
                "  \"query\": { \n" +
                "    \"bool\": { \n" +
                "      \"must\": [ " + getMatchQuery() + " ]\n" +
                "    }\n" +
                "  }\n" +
                "}";


        // Prepare request
        final HttpGetWithData getRequest = new HttpGetWithData(dao.getUri() + "/_search");
        final StringEntity input = new StringEntity(query, ContentType.APPLICATION_JSON);
        getRequest.setEntity(input);

        final String auth = dao.getAuth();
        if (auth != null) {
            getRequest.addHeader("Authorization", "Basic " + auth);
        }

        try(CloseableHttpClient httpClient = clientBuilder().build();
            CloseableHttpResponse response = httpClient.execute(getRequest)) {

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException(HttpGetWithData.getErrorMessage(dao.getUri(), response));
            }

            // TODO: retrieve log entries
            final String content;
            try(InputStream i = response.getEntity().getContent()) {
                content = IOUtils.toString(i);
            }

            final JSONObject json = JSONObject.fromObject(content);
            JSONArray jsonArray = json.getJSONObject("hits").getJSONArray("hits");
            for (int i=0; i<jsonArray.size(); ++i) {
                JSONObject hit = jsonArray.getJSONObject(i);
                JSONObject data = hit.getJSONObject("fields");
                String timestamp = data.getJSONArray("@timestamp").getString(0);
                JSONConsoleNotes.jsonToMessage(writer, data);
            }
        }
    }

    HttpClientBuilder clientBuilder() {
        if (clientBuilder == null) {
            clientBuilder = HttpClientBuilder.create();
        }
        return clientBuilder;
    }
}