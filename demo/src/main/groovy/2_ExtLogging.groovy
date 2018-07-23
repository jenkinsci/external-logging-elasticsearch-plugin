import jenkins.plugins.logstash.LogstashInstallation
import jenkins.plugins.logstash.LogstashConfiguration
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingGlobalConfiguration
import io.jenkins.plugins.extlogging.logstash.LogstashDaoLoggingMethodFactory
import io.jenkins.plugins.extlogging.elasticsearch.ElasticsearchLogBrowserFactory

println("--- Configuring Logstash")
String logstashPort = System.getProperty("elasticsearch.port");

def descriptor = LogstashInstallation.logstashDescriptor
descriptor.@type = LogstashIndexerDao.IndexerType.ELASTICSEARCH
descriptor.@host = System.getProperty("elasticsearch.host", "http://elk")
descriptor.@port = logstashPort != null ? Integer.parseInt(logstashPort) : 9200
descriptor.@username = System.getProperty("elasticsearch.username")
descriptor.@password = System.getProperty("elasticsearch.password")
descriptor.@key = System.getProperty("logstash.key", "/logstash/logs")

// TODO: Replace by proper initialization once plugin API is fixed
// Currently setIndexer() method does not change active indexer.
LogstashConfiguration.instance.@dataMigrated = false
LogstashConfiguration.instance.migrateData()

println("--- Configuring External Logging")
ExternalLoggingGlobalConfiguration.instance.loggingMethod = new LogstashDaoLoggingMethodFactory()
ExternalLoggingGlobalConfiguration.instance.logBrowser = new ElasticsearchLogBrowserFactory()
