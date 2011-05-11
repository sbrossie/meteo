package com.ning.metrics.meteo.subscribers.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import com.google.inject.internal.ImmutableList;

public class ActionCoreParser {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = Logger.getLogger(ActionCoreParser.class);

    private final ActionCoreParserFormat format;
    private final String [] allEventFields;
    private final String eventName;

    public enum ActionCoreParserFormat {
        ACTION_CORE_FORMAT_MR("MR"),
        ACTION_CORE_FORMAT_DEFAULT("DEFAULT");

        private String parserTypeValue;

        ActionCoreParserFormat(String type) {
            parserTypeValue = type;
        }

        public static ActionCoreParserFormat getFromString(String in) {
            for (ActionCoreParserFormat cur : ActionCoreParserFormat.values()) {
                if (cur.parserTypeValue.equals(in)) {
                    return cur;
                }
            }
            return null;
        }
    }


    public ActionCoreParser(ActionCoreParserFormat format, String eventName, String [] allEventFields) {
        this.format = format;
        this.allEventFields = allEventFields;
        this.eventName = eventName;
    }

    public ImmutableList<Map<String, Object>> parse(String json) throws Exception {

        switch(format) {
        case ACTION_CORE_FORMAT_DEFAULT:
            return parseDefault(json);

        case ACTION_CORE_FORMAT_MR:
            return parseMR(json);

        default:
            throw new RuntimeException("Format " + format + " not supported");
        }
    }

    private ImmutableList<Map<String, Object>> parseMR(String json) throws Exception {

        ImmutableList.Builder<Map<String, Object>> builder = new ImmutableList.Builder<Map<String, Object>>();

        Map eventTop = mapper.readValue(json, Map.class);
        List<Map> entriesDirectory = (List<Map>) eventTop.get("entries");
        for (Map entryDirectory : entriesDirectory) {

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
    }

    private ImmutableList<Map<String, Object>> parseDefault(String json) throws Exception {

        ImmutableList.Builder<Map<String, Object>> builder = new ImmutableList.Builder<Map<String, Object>>();

        Map eventTop = mapper.readValue(json, Map.class);
        List<Map> entriesDirectory = (List<Map>) eventTop.get("entries");
        for (Map entryDirectory : entriesDirectory) {

            Map entryContent = (Map) entryDirectory.get("content");
            List<Map> entries = (List<Map>) entryContent.get("entries");

            for (Map<String, Object> event : entries) {

                Map<String, Object> simplifiedEvent = extractEvent(event);
                builder.add(simplifiedEvent);
            }
        }
        return builder.build();
    }

    private Map<String, Object> extractEvent(Map<String, Object> eventFull) {

        Map<String, Object> result = new HashMap<String, Object>();

        for (String key : allEventFields) {
            Object value = eventFull.get(key);
            if (value == null) {
                log.warn("Event " + eventName + " is missing key " + key);
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

}
