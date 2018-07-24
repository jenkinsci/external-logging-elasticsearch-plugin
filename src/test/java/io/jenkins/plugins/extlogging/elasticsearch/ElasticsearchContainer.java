package io.jenkins.plugins.extlogging.elasticsearch;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingGlobalConfiguration;
import io.jenkins.plugins.extlogging.logstash.LogstashDaoLoggingMethodFactory;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;

/**
 * Elasticsearch test container.
 * @author Oleg Nenashev
 */
@DockerFixture(id = "elasticsearch", ports = 9200)
public class ElasticsearchContainer extends DockerContainer {

    private static final Logger LOGGER =
            Logger.getLogger(ElasticsearchContainer.class.getName());

    @Nonnull
    public URL getURL() {
        try {
            return new URL("http://" + ipBound(9200) + ":" + port(9200));
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
    }

    public void configureJenkins(JenkinsRule j) throws AssertionError {
        try {
            ElasticsearchGlobalConfiguration.get().setElasticsearch(
                    new ElasticsearchConfiguration(getURL().toString())
            );
        } catch (Exception ex) {
            throw new AssertionError("Failed to configure Logstash Plugin using reflection", ex);
        }

        ExternalLoggingGlobalConfiguration cfg = ExternalLoggingGlobalConfiguration.getInstance();
        cfg.setLogBrowser(new ElasticsearchLogBrowserFactory());
        cfg.setLoggingMethod(new LogstashDaoLoggingMethodFactory());

    }

    public void waitForInit(int timeoutMs) throws AssertionError, Exception {

        long startTime = System.currentTimeMillis();
        ObjectMapper mapper = new ObjectMapper();

        while (System.currentTimeMillis() < startTime + timeoutMs) {
            try (CloseableHttpClient httpclient = HttpClients.createMinimal()) {
                HttpGet httpGet = new HttpGet(getURL().toString());
                try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        ElasticsearchInfo es = mapper.readValue(response.getEntity().getContent(), ElasticsearchInfo.class);
                        LOGGER.log(Level.FINE, "ES version: " + es.version.number);
                        return;
                    }
                } catch (NoHttpResponseException ex) {
                  // Fine, keep trying
                } catch (Exception ex) {
                    // keep trying
                    LOGGER.log(Level.WARNING, "Wrong response", ex);
                }
            }
            Thread.sleep(1000);
        }

        throw new TimeoutException("Elasticsearch connection timeout: " + timeoutMs + "ms");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElasticsearchInfo {

        @JsonProperty
        public int status;

        @JsonProperty
        public String name;

        @JsonProperty("cluster_name")
        public String clusterName;

        @JsonProperty
        public ElasticsearchVersion version;

        @JsonProperty
        public String tagline;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElasticsearchVersion {

        @JsonProperty
        public String number;

        @JsonProperty("build_hash")
        public String buildHash;

        @JsonProperty("build_timestamp")
        public String buildTimestamp;

        @JsonProperty("build_snapshot")
        public boolean buildSnapshot;

        @JsonProperty("lucene_version")
        public String luceneVersion;
    }
}