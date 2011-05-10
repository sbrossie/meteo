package com.ning.metrics.meteo.sequencer;

import org.joda.time.DateTime;

public class SequencerElement implements Comparable<SequencerElement> {

    private final DateTime date;
    private final Object node;

    public SequencerElement(DateTime date, Object node) {
        this.date = date;
        this.node = node;
    }



    @Override
    public int compareTo(SequencerElement o) {
        if (o == null) {
            return -1;
        }
        return getDate().compareTo(o.getDate());
    }

    public DateTime getDate() {
        return date;
    }

    public Object getNode() {
        return node;
    }
}
