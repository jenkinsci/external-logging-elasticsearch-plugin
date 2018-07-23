package io.jenkins.plugins.extlogging.elasticsearch;

import com.jcraft.jzlib.GZIPInputStream;
import com.trilead.ssh2.crypto.Base64;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import hudson.model.Run;
import hudson.remoting.ObjectInputStreamEx;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Writer;
import static java.lang.Math.abs;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import io.jenkins.plugins.extlogging.api.util.UniqueIdHelper;
import io.jenkins.plugins.extlogging.elasticsearch.util.HttpGetWithData;
import jenkins.model.Jenkins;
import jenkins.plugins.logstash.LogstashConfiguration;
import jenkins.plugins.logstash.persistence.ElasticSearchDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import jenkins.security.CryptoConfidentialKey;
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
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
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

    public ElasticsearchLogLargeTextProvider(Run<?, ?> run, String stepId) {
        this.run = run;
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
        Collection<String> pulledLogs = pullLogs(esDao,0, Long.MAX_VALUE);
        long ctr = 0;
        for (String logEntry : pulledLogs) {
            byte[] bytes = logEntry.getBytes();
            
            buffer.write(bytes, 0, bytes.length);
            buffer.write('\n');
            
        }
        return buffer;
    }

    private Collection<String> pullLogs(ElasticSearchDao dao, long sinceMs, long toMs) throws IOException {
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
            ArrayList<String> res = new ArrayList<>(jsonArray.size());
            for (int i=0; i<jsonArray.size(); ++i) {
                JSONObject hit = jsonArray.getJSONObject(i);
                String timestamp = hit.getJSONObject("fields").getJSONArray("@timestamp").getString(0);
                String message = hit.getJSONObject("fields").getJSONArray("message").getString(0);
                res.add(timestamp + " > " +message);
            }
            Collections.sort(res);
            return res;

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
    
    public static class UncompressedAnnotatedLargeText<T> extends AnnotatedLargeText<T> {

        private T context;
        private ByteBuffer memory;
        
        public UncompressedAnnotatedLargeText(ByteBuffer memory, Charset charset, boolean completed, T context) {
            super(memory, charset, completed, context);
            this.context = context;
            this.memory = memory;
        }
         
        @Override
        public long writeHtmlTo(long start, Writer w) throws IOException {
            ConsoleAnnotationOutputStream caw = new ConsoleAnnotationOutputStream(
                    w, createAnnotator(Stapler.getCurrentRequest()), context, charset);
            long r = super.writeLogTo(start, caw);
            caw.flush();
            long initial = memory.length();
            /*
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Cipher sym = PASSING_ANNOTATOR.encrypt();
            ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new CipherOutputStream(baos, sym)));
            oos.writeLong(System.currentTimeMillis()); // send timestamp to prevent a replay attack
            oos.writeObject(caw.getConsoleAnnotator());
            oos.close();
            StaplerResponse rsp = Stapler.getCurrentResponse();
            if (rsp != null) {
                rsp.setHeader("X-ConsoleAnnotator", new String(Base64.encode(baos.toByteArray())));
            }
            return r;
            */
            
            /*
            try {
                memory.writeTo(caw);
            } finally {
                caw.flush();
                caw.close();
            }*/
            return initial - memory.length(); 
        }
        
        /**
        * Used for sending the state of ConsoleAnnotator to the client, because we are deserializing this object later.
        */
        private static final CryptoConfidentialKey PASSING_ANNOTATOR = new CryptoConfidentialKey(AnnotatedLargeText.class,"consoleAnnotator");

        
        private ConsoleAnnotator createAnnotator(StaplerRequest req) throws IOException {
        try {
            String base64 = req!=null ? req.getHeader("X-ConsoleAnnotator") : null;
            if (base64!=null) {
                Cipher sym = PASSING_ANNOTATOR.decrypt();

                ObjectInputStream ois = new ObjectInputStreamEx(new GZIPInputStream(
                        new CipherInputStream(new ByteArrayInputStream(Base64.decode(base64.toCharArray())),sym)),
                        Jenkins.getInstance().pluginManager.uberClassLoader);
                try {
                    long timestamp = ois.readLong();
                    if (TimeUnit.HOURS.toMillis(1) > abs(System.currentTimeMillis()-timestamp))
                        // don't deserialize something too old to prevent a replay attack
                        return (ConsoleAnnotator)ois.readObject();
                } finally {
                    ois.close();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        // start from scratch
        return ConsoleAnnotator.initial(context==null ? null : context.getClass());
    }
    }
}