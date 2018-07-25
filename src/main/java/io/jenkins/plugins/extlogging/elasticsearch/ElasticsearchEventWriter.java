
package io.jenkins.plugins.extlogging.elasticsearch;

import io.jenkins.plugins.extlogging.api.Event;
import io.jenkins.plugins.extlogging.api.ExternalLoggingEventWriter;

import io.jenkins.plugins.extlogging.elasticsearch.util.ElasticSearchDao;
import io.jenkins.plugins.extlogging.elasticsearch.util.JSONConsoleNotes;
import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.time.FastDateFormat;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ElasticsearchEventWriter extends ExternalLoggingEventWriter {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ElasticsearchEventWriter.class.getName());
    private static final FastDateFormat MILLIS_FORMATTER = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @CheckForNull
    private final String prefix;
    @Nonnull
    private final ElasticSearchDao dao;
    private boolean connectionBroken;

    public ElasticsearchEventWriter(@CheckForNull String prefix,
                                    @Nonnull ElasticSearchDao dao) {
        this.prefix = prefix;
        this.dao = dao;
    }

    @Override
    public void writeMessage(String message) throws IOException {
        super.writeMessage(prefix != null ? prefix + message : message);
    }

    @Override
    public void writeEvent(Event event) {
        JSONObject payload = new JSONObject();
        JSONConsoleNotes.parseToJSON(event.getMessage(), payload);
        // TODO: replace Dao implementation by an independent one
        JSONObject data = new JSONObject();
        for (Map.Entry<String, Serializable> entry : event.getData().entrySet()) {
            Serializable value = entry.getValue();
            data.accumulate(entry.getKey(), value != null ? value.toString() : null);
        }
        payload.put("data", data);
        //TODO: Use Event timestamp everywhere?
        payload.put("@buildTimestamp", MILLIS_FORMATTER.format(event.getTimestamp()));
        payload.put("@timestamp", MILLIS_FORMATTER.format(Calendar.getInstance().getTime()));
        payload.put("@version", 1);

        try {
            dao.push(payload.toString());
        } catch (IOException e) {
            String msg = "[logstash-plugin]: Failed to send log data to " + dao.getDescription() + ".\n"
                    + "[logstash-plugin]: No Further logs will be sent to " + dao.getDescription() + ".\n"
                    + ExceptionUtils.getStackTrace(e);
            logErrorMessage(msg);
        }
    }

    /**
     * @return True if errors have occurred during initialization or write.
     */
    public boolean isConnectionBroken() {
        return connectionBroken || dao == null;
    }

    /**
     * Write error message to errorStream and set connectionBroken to true.
     */
    private void logErrorMessage(String msg) {
        connectionBroken = true;
        LOGGER.log(Level.WARNING, msg);
    }

    @Override
    public void flush() throws IOException {
        // no caching, nothing to do here
    }

    @Override
    public void close() throws IOException {
        // dao handles it
    }
}
