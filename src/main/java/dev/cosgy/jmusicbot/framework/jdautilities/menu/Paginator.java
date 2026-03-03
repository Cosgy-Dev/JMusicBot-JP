package dev.cosgy.jmusicbot.framework.jdautilities.menu;

import dev.cosgy.jmusicbot.framework.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Paginator {
    private final int itemsPerPage;
    private final List<String> items;
    private final BiFunction<Integer, Integer, String> text;
    private final Color color;
    private final Consumer<Message> finalAction;
    private final EventWaiter waiter;
    private final Set<String> userIds;
    private final long timeout;
    private final TimeUnit timeoutUnit;

    private Paginator(Builder builder) {
        this.itemsPerPage = builder.itemsPerPage;
        this.items = List.copyOf(builder.items);
        this.text = builder.text;
        this.color = builder.color;
        this.finalAction = builder.finalAction;
        this.waiter = builder.waiter;
        this.userIds = Set.copyOf(builder.userIds);
        this.timeout = builder.timeout;
        this.timeoutUnit = builder.timeoutUnit;
    }

    public void paginate(MessageChannel channel, int pageNum) {
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) itemsPerPage));
        AtomicInteger current = new AtomicInteger(Math.max(1, Math.min(pageNum, totalPages)));
        String baseId = "paginator:" + Long.toHexString(System.nanoTime());

        channel.sendMessageEmbeds(buildPage(current.get(), totalPages)).setComponents(ActionRow.of(
                Button.secondary(baseId + ":prev", "前へ"),
                Button.secondary(baseId + ":next", "次へ"),
                Button.danger(baseId + ":close", "閉じる")
        )).queue(message -> {
            if (waiter == null) {
                if (finalAction != null) finalAction.accept(message);
                return;
            }

            waiter.waitForEvent(
                    ButtonInteractionEvent.class,
                    ev -> ev.getMessageIdLong() == message.getIdLong()
                            && ev.getComponentId().startsWith(baseId)
                            && (userIds.isEmpty() || userIds.contains(ev.getUser().getId())),
                    ev -> {
                        String id = ev.getComponentId();
                        if (id.endsWith(":close")) {
                            ev.deferEdit().queue();
                            message.editMessageComponents().queue();
                            if (finalAction != null) finalAction.accept(message);
                            return;
                        }

                        int nextPage = current.get();
                        if (id.endsWith(":prev")) {
                            nextPage = Math.max(1, current.get() - 1);
                        } else if (id.endsWith(":next")) {
                            nextPage = Math.min(totalPages, current.get() + 1);
                        }
                        current.set(nextPage);
                        ev.editMessageEmbeds(buildPage(nextPage, totalPages)).queue();
                    },
                    timeout,
                    timeoutUnit,
                    () -> {
                        message.editMessageComponents().queue();
                        if (finalAction != null) finalAction.accept(message);
                    }
            );
        });
    }

    private net.dv8tion.jda.api.entities.MessageEmbed buildPage(int current, int totalPages) {
        int from = (current - 1) * itemsPerPage;
        int to = Math.min(items.size(), from + itemsPerPage);

        StringBuilder content = new StringBuilder();
        if (text != null) {
            content.append(text.apply(current, totalPages)).append("\n");
        }
        for (int i = from; i < to; i++) {
            content.append(items.get(i)).append("\n");
        }

        EmbedBuilder eb = new EmbedBuilder();
        if (color != null) eb.setColor(color);
        eb.setDescription(content.toString());
        return eb.build();
    }

    public static class Builder {
        private int itemsPerPage = 10;
        private List<String> items = new ArrayList<>();
        private BiFunction<Integer, Integer, String> text;
        private Color color;
        private Consumer<Message> finalAction;
        private EventWaiter waiter;
        private final Set<String> userIds = new HashSet<>();
        private long timeout = 1;
        private TimeUnit timeoutUnit = TimeUnit.MINUTES;

        public Builder setColumns(int ignored) { return this; }

        public Builder setFinalAction(Consumer<Message> finalAction) {
            this.finalAction = finalAction;
            return this;
        }

        public Builder setItemsPerPage(int itemsPerPage) {
            this.itemsPerPage = itemsPerPage;
            return this;
        }

        public Builder waitOnSinglePage(boolean ignored) { return this; }

        public Builder useNumberedItems(boolean ignored) { return this; }

        public Builder showPageNumbers(boolean ignored) { return this; }

        public Builder wrapPageEnds(boolean ignored) { return this; }

        public Builder setEventWaiter(EventWaiter waiter) {
            this.waiter = waiter;
            return this;
        }

        public Builder setTimeout(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.timeoutUnit = unit;
            return this;
        }

        public Builder setText(BiFunction<Integer, Integer, String> text) {
            this.text = text;
            return this;
        }

        public Builder setItems(String[] items) {
            this.items = new ArrayList<>(List.of(items));
            return this;
        }

        public Builder setUsers(User... users) {
            this.userIds.clear();
            if (users != null) {
                for (User user : users) {
                    if (user != null) this.userIds.add(user.getId());
                }
            }
            return this;
        }

        public Builder setColor(Color color) {
            this.color = color;
            return this;
        }

        public Paginator build() {
            return new Paginator(this);
        }
    }
}
