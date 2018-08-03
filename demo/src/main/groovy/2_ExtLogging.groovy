import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingGlobalConfiguration
import io.jenkins.plugins.extlogging.elasticsearch.ElasicsearchLoggingMethodFactory
import io.jenkins.plugins.extlogging.elasticsearch.ElasticsearchLogBrowserFactory
import io.jenkins.plugins.extlogging.elasticsearch.ElasticsearchGlobalConfiguration
import io.jenkins.plugins.extlogging.elasticsearch.ElasticsearchConfiguration


println("--- Configuring Logstash")
String logstashPort = System.getProperty("elasticsearch.port");
int port = logstashPort != null ? Integer.parseInt(logstashPort) : 9200;

def config = ElasticsearchGlobalConfiguration.get();

ElasticsearchConfiguration cfg = new ElasticsearchConfiguration(
    System.getProperty("elasticsearch.uri", "http://elk:${port}")
)

config.elasticsearch = cfg;
config.key = System.getProperty("elasticsearch.key", "/logstash/logs")

//TODO: support credentials
//descriptor.@username = System.getProperty("elasticsearch.username")
//descriptor.@password = System.getProperty("elasticsearch.password")

println("--- Configuring External Logging")
ExternalLoggingGlobalConfiguration.instance.loggingMethod = new ElasicsearchLoggingMethodFactory()
ExternalLoggingGlobalConfiguration.instance.logBrowser = new ElasticsearchLogBrowserFactory()
