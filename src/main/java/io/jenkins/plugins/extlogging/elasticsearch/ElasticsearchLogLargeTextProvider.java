package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.console.AnnotatedLargeText;
import hudson.model.Run;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import io.jenkins.plugins.extlogging.api.util.UniqueIdHelper;
import io.jenkins.plugins.extlogging.elasticsearch.util.JSONConsoleNotes;
import io.jenkins.plugins.extlogging.elasticsearch.util.HttpGetWithData;
import jenkins.plugins.logstash.LogstashConfiguration;
import jenkins.plugins.logstash.persistence.ElasticSearchDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
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
    private Run run;

    @CheckForNull
    private String stepId;

    public ElasticsearchLogLargeTextProvider(Run<?, ?> run) {
        this(run, null);
    }

    public ElasticsearchLogLargeTextProvider(Run<?, ?> run, @CheckForNull String stepId) {
        this.run = run;
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
        return new UncompressedAnnotatedLargeText(buf, StandardCharsets.UTF_8, !run.isLogUpdated(), this);
    }
    
    /**
     * Returns an input stream that reads from the log file.
     * @throws IOException Operation error
     */
    @Nonnull 
    public ByteBuffer readLogToBuffer(long initialOffset) throws IOException {
        LogstashIndexerDao dao = LogstashConfiguration.getInstance().getIndexerInstance();
        if (!(dao instanceof ElasticSearchDao)) {
            throw new IOException("Cannot brows logs, Elasticsearch Dao must be configured in the Logstash plugin");
        }
        ElasticSearchDao esDao = (ElasticSearchDao)dao;
        
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

    private void pullLogs(Writer writer, ElasticSearchDao dao, long sinceMs, long toMs) throws IOException {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;

        // Determine job id
        String jobId = UniqueIdHelper.getOrCreateId(run);

        // Prepare query
        String query = "{\n" +
                "  \"fields\": [\"message\",\"@timestamp\"], \n" +
                "  \"size\": 9999, \n" + // TODO use paging https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
                "  \"query\": { \n" +
                "    \"bool\": { \n" +
                "      \"must\": [\n" +
                "        { \"match\": { \"data.jobId\":   \"" + jobId + "\"}}, \n" +
                (stepId != null ?
                "        { \"match\": { \"data.stepId\": \"" + stepId + "\" }},  \n"
                : "") +
                "        { \"match\": { \"data.buildNum\": \"" + run.getNumber() + "\" }}  \n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";


        // Prepare request
        final HttpGetWithData getRequest = new HttpGetWithData(dao.getUri() + "/_search");
        final StringEntity input = new StringEntity(query, ContentType.APPLICATION_JSON);
        getRequest.setEntity(input);

        if (dao.getUsername() != null) {
            //TODO: Make the logic public in the Logstash plugin
            String auth = org.apache.commons.codec.binary.Base64.encodeBase64String(
                    (dao.getUsername() + ":" + StringUtils.defaultString(dao.getPassword())).getBytes(StandardCharsets.UTF_8));
            getRequest.addHeader("Authorization", "Basic " + auth);
        }

        try {
            httpClient = clientBuilder().build();
            response = httpClient.execute(getRequest);

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

        } finally {
            if (response != null) {
                response.close();
            }
            if (httpClient != null) {
                httpClient.close();
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