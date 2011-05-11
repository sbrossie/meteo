package com.ning.metrics.meteo.sequencer;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

public class Sequencer {

    private static final Logger log = Logger.getLogger(Sequencer.class);

    private static final int WAIT_LOG_THRESHOLD = 5000; // 5 sec

    private static final int DEFAULT_SPEED = 1;

    // Speed at which to replay the element
    private final int speed;
    // Time at which we get the first element
    private long T0;
    // Time of the first init event (vClock axis)
    private long vT0;


    private final AtomicBoolean init;
    private SequencerElement firstElement;
    private SequencerElement prevElement;

    private long totalWait;
    private int totalElements;

    public Sequencer() {
        this.speed = DEFAULT_SPEED;
        this.init = new AtomicBoolean(false);
        this.prevElement = null;
    }

    private long getVClock() {
        return speed * (currentTimeUTC() - T0) + vT0;
    }


    private void initOnce(SequencerElement el) {

        if (init.compareAndSet(false, true)) {
            T0 = currentTimeUTC();
            vT0 = el.getDate().getMillis();
            if (T0 < vT0) {
                log.error("TO = " + T0 + " (" + (new DateTime(T0, DateTimeZone.forID("UTC"))) + "), vT0 = " +vT0 + " (" + (new DateTime(vT0, DateTimeZone.forID("UTC"))) + ")");
                throw new RuntimeException("Can't play (yet) element from the future");
            }
            firstElement = el;
            log.info("Sequencer initialized with T0 = " + T0 + ", vTO = " + vT0);
        }
    }

    public void wait(SequencerElement el) {

        if (prevElement != null) {
            if (el.getDate().isBefore(prevElement.getDate())) {

                log.warn("!!! Elements not ordered, skip prev = " + new DateTime(prevElement.getDate()).toString() +
                        ", cur = " + new DateTime(el.getDate()).toString() + ", duration = " +
                        new Duration(el.getDate(), prevElement.getDate()).getMillis() / 1000);

                prevElement = el;
                return;
            }
        }

        initOnce(el);

        long waitTime = (long) ((double) (el.getDate().getMillis() - getVClock()) / speed);
        if (waitTime > 0) {
            try {
                Thread.sleep(waitTime);
                if (prevElement != null && waitTime > WAIT_LOG_THRESHOLD) {
                    log.warn("Waiting for more than " + waitTime / 1000 +
                            " sec. prevElement = " + new DateTime(prevElement.getDate()).toString() +
                            " curElement = " + new DateTime(el.getDate()).toString());
                }
                totalWait += waitTime;
            } catch (InterruptedException ie) {
                log.warn("Sequencer got interrupted...");
                return;
            }
        }
        prevElement = el;
        totalElements++;
    }

    public long getTotalWaitTimeMs() {
        return totalWait;
    }

    public int getNbElements() {
        return totalElements;
    }


    private long currentTimeUTC() {
        return (new DateTime(DateTimeZone.forID("UTC")).getMillis());
    }

}
