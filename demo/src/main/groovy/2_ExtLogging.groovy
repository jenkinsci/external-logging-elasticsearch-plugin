import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingGlobalConfiguration
import io.jenkins.plugins.extlogging.logstash.LogstashDaoLoggingMethodFactory
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

//TODO: support credentials
//descriptor.@username = System.getProperty("elasticsearch.username")
//descriptor.@password = System.getProperty("elasticsearch.password")
config.key = System.getProperty("elasticsearch.key", "/logstash/logs")

println("--- Configuring External Logging")
ExternalLoggingGlobalConfiguration.instance.loggingMethod = new LogstashDaoLoggingMethodFactory()
ExternalLoggingGlobalConfiguration.instance.logBrowser = new ElasticsearchLogBrowserFactory()
