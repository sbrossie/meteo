package com.ning.metrics.meteo.subscribers;

public class ActionCoreSubscriberConfig extends SubscriberConfig {

    private static final String ACTION_CORE_PATH = "/rest/1.0/json";
    private static final String ACTION_CORE_QUERY_PREFIX = "?path=";

    public static final String ACTION_CORE_SUFIX_RECURSIVE = "&recursive=true";
    public static final String ACTION_CORE_SUFIX_RAW = "&raw=true";

    public static final String ACTION_CORE_URI_BASE = ACTION_CORE_PATH + ACTION_CORE_QUERY_PREFIX;

    public static final long ACTION_CORE_TIMEOUT_SEC = 600; // 10 min

    public String host;
    public int port = 8080;
    public String hdfsPath;
    public String eventFields;
    public String sequencerForField;

    public String getSequencerForField() {
        return sequencerForField;
    }
    public void setSequencerForField(String sequencerForField) {
        this.sequencerForField = sequencerForField;
    }
    public String getEventFields() {
        return eventFields;
    }
    public void setEventFields(String eventFields) {
        this.eventFields = eventFields;
    }
    public String getHdfsPath() {
        return hdfsPath;
    }
    public void setHdfsPath(String hdfsPath) {
        this.hdfsPath = hdfsPath;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
}
