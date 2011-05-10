package com.ning.metrics.meteo.subscribers;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.espertech.esper.client.EPServiceProvider;
import com.google.inject.Inject;
import com.google.inject.internal.ImmutableList;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import com.ning.metrics.meteo.sequencer.Sequencer;
import com.ning.metrics.meteo.sequencer.SequencerElement;

public class ActionCoreSubscriber implements Subscriber {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = Logger.getLogger(ActionCoreSubscriber.class);


    public static final String HEADER_CONTENT_TYPE = "Content-type";
    public static final String CONTENT_TYPE = "application/json";

    private final EPServiceProvider esperSink;
    private final ActionCoreSubscriberConfig subscriberConfig;
    private final AsyncHttpClient asyncSender;
    private final String sequencerField;
    private final String [] allEventFields;

    private final Sequencer sequencer;


    private final static LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();


    private final DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(DateTimeZone.forID("UTC"));

    @Inject
    public ActionCoreSubscriber(ActionCoreSubscriberConfig subscriberConfig, EPServiceProvider esperSink)
    {
        this.subscriberConfig = subscriberConfig;
        this.esperSink = esperSink;
        AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();
        builder.setMaximumConnectionsPerHost(-1);
        this.asyncSender = new AsyncHttpClient(builder.build());
        this.sequencerField = subscriberConfig.getSequencerForField();
        this.sequencer = (this.sequencerField != null) ? new Sequencer() : null;
        String eventFields = subscriberConfig.getEventFields();
        this.allEventFields = eventFields.split("\\s*,\\s*");
    }


    @Override
    public void subscribe() {

        ImmutableList<Map<String, Object>> points = getDataPoints();
        for (Map<String, Object> curPoint : points) {
            try {
                // "2011-05-10 16:45:16"
                String pointTimeStr = (String) curPoint.get(sequencerField);
                DateTime pointTime = fmt.parseDateTime(pointTimeStr);
                sequence(pointTime, curPoint);
                esperSink.getEPRuntime().sendEvent(curPoint, subscriberConfig.getEventOutputName());
            }
            catch (ClassCastException ex) {
                log.info("Received message that I couldn't parse: " + curPoint, ex);
            }
        }
    }

    @Override
    public void unsubscribe() {

    }

    private void sequence(DateTime pointDate, Map<String, Object> point) {
        if (sequencer == null) {
            return;
        }
        sequencer.wait(new SequencerElement(pointDate, point));
    }

    private Map<String, Object> extractEvent(Map<String, Object> eventFull) {

        Map<String, Object> result = new HashMap<String, Object>();

        for (String key : allEventFields) {
            Object value = eventFull.get(key);
            if (value == null) {
                log.warn("Event " + subscriberConfig.getEventOutputName() + " is missing key " + key);
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    private Map<String, Object> extractEventTabSep(String event) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (event == null) {
            return result;
        }

        String eventFields = subscriberConfig.getEventFields();
        String [] allEventFields = eventFields.split("\\s*,\\s*");


        String [] parts = event.split("\\t");
        if (parts == null || parts.length != allEventFields.length) {
            log.warn("Unexpected event content size = " + ((parts == null) ? 0 : parts.length));
            return result;
        }

        int i = 0;
        for (String key : allEventFields) {
            result.put(key, parts[i]);
            i++;
        }
        return result;
    }


    private ImmutableList<Map<String, Object>> getDataPoints() {


        String url = String.format("http://%s:%d%s%s%s",
                subscriberConfig.getHost(),
                subscriberConfig.getPort(),
                ActionCoreSubscriberConfig.ACTION_CORE_URI_BASE,
                subscriberConfig.getHdfsPath(),
                ActionCoreSubscriberConfig.ACTION_CORE_SUFIX_RECURSIVE);
        try {

            log.debug("ActionCoreSubscriber retrieving : " +  url);

            Future<String> future = asyncSender.prepareGet(url)
            .addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE)
            .execute(new AsyncCompletionHandler<String>() {

                @Override
                public String onCompleted(Response response) throws Exception {
                    int statusCode = response.getStatusCode();
                    return response.getResponseBody();
                }

            });

            // Wait for results
            String json = future.get(ActionCoreSubscriberConfig.ACTION_CORE_TIMEOUT_SEC, TimeUnit.SECONDS);

            ImmutableList.Builder<Map<String, Object>> builder = new ImmutableList.Builder<Map<String, Object>>();

            Map eventTop = mapper.readValue(json, Map.class);
            List<Map> entriesDirectory = (List<Map>) eventTop.get("entries");
            for (Map entryDirectory : entriesDirectory) {

                //Map entryContent = (Map) entryDirectory.get("content");
                //List<Map> entries = (List<Map>) entryContent.get("entries");
                //List<Map> entries = (List<Map>) entryContent.get("entries");

                List<Map> entries = null;
                Object entriesRow =  null;
                try {
                    entriesRow =  entryDirectory.get("content");
                    if (entriesRow == null) {
                        continue;
                    }
                    if (entriesRow instanceof java.lang.String && ((String)entriesRow).equals("")) {
                        continue;
                    }

                    entries = (List<Map>) entriesRow;
                } catch (Exception e) {
                    log.error("Failed to deserialize the event " + entriesRow.toString());
                }
                for (Map<String, Object> event : entries) {

                    Map<String, Object> simplifiedEvent = extractEventTabSep((String) event.get("record"));
                    builder.add(simplifiedEvent);
                }
            }
            return builder.build();

        } catch (IOException ioe) {
            log.warn("IOException : Failed to connect to action code : url = " + url + ", error = " + ioe.getMessage());
            return null;
        } catch (InterruptedException ie) {
            log.warn("Thread got interrupted : Failed to connect to action code : url = " + url + ", error = " + ie.getMessage());
            return null;

        } catch (TimeoutException toe) {
            log.warn("Timeout: Failed to connect to action code within " + ActionCoreSubscriberConfig.ACTION_CORE_TIMEOUT_SEC + " sec, : url = " + url);
            return null;

        } catch (Throwable othere) {
            log.error("Unexpected exception while connecting to action core, url =  " + url , othere);
            return null;
        }
    }
}
