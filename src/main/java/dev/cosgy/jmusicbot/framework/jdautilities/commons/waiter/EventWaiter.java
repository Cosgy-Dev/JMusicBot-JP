package dev.cosgy.jmusicbot.framework.jdautilities.commons.waiter;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class EventWaiter extends ListenerAdapter {
    private final List<WaitingEvent<?>> waitingEvents = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "event-waiter");
        t.setDaemon(true);
        return t;
    });

    public <T extends GenericEvent> void waitForEvent(Class<T> clazz,
                                                      Predicate<T> condition,
                                                      Consumer<T> action,
                                                      long timeout,
                                                      TimeUnit unit,
                                                      Runnable timeoutAction) {
        WaitingEvent<T> waiting = new WaitingEvent<>(clazz, condition, action);
        waitingEvents.add(waiting);
        scheduler.schedule(() -> {
            if (waitingEvents.remove(waiting) && timeoutAction != null) {
                timeoutAction.run();
            }
        }, timeout, unit);
    }

    @Override
    public void onGenericEvent(GenericEvent event) {
        Iterator<WaitingEvent<?>> iterator = waitingEvents.iterator();
        while (iterator.hasNext()) {
            WaitingEvent<?> waiting = iterator.next();
            if (waiting.tryAccept(event)) {
                waitingEvents.remove(waiting);
            }
        }
    }

    private static final class WaitingEvent<T extends GenericEvent> {
        private final Class<T> type;
        private final Predicate<T> condition;
        private final Consumer<T> action;

        private WaitingEvent(Class<T> type, Predicate<T> condition, Consumer<T> action) {
            this.type = type;
            this.condition = condition;
            this.action = action;
        }

        @SuppressWarnings("unchecked")
        private boolean tryAccept(GenericEvent event) {
            if (!type.isInstance(event)) return false;
            T casted = (T) event;
            if (condition != null && !condition.test(casted)) return false;
            action.accept(casted);
            return true;
        }
    }
}
