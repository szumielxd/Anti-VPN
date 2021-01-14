package me.egg82.antivpn.events;

import java.util.ArrayList;
import java.util.List;
import ninja.egg82.events.VelocityEventSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EventHolder {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final List<VelocityEventSubscriber<?>> events = new ArrayList<>();

    public final int numEvents() { return events.size(); }

    public final void cancel() {
        for (VelocityEventSubscriber<?> event : events) {
            event.cancel();
        }
    }
}
