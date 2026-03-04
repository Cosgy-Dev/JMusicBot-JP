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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class OrderedMenu {
    private final EventWaiter waiter;
    private final List<String> choices;
    private final String text;
    private final Color color;
    private final BiConsumer<Message, Integer> selection;
    private final Consumer<Message> cancel;
    private final Set<String> userIds;
    private final long timeout;
    private final TimeUnit timeoutUnit;

    private OrderedMenu(Builder b) {
        this.waiter = b.waiter;
        this.choices = List.copyOf(b.choices);
        this.text = b.text;
        this.color = b.color;
        this.selection = b.selection;
        this.cancel = b.cancel;
        this.userIds = Set.copyOf(b.userIds);
        this.timeout = b.timeout;
        this.timeoutUnit = b.timeoutUnit;
    }

    public void display(Message message) {
        display(message.getChannel());
    }

    public void display(MessageChannel channel) {
        EmbedBuilder eb = new EmbedBuilder();
        if (color != null) eb.setColor(color);
        StringBuilder body = new StringBuilder(text == null ? "選択してください" : text);
        for (int i = 0; i < choices.size(); i++) {
            body.append("\n").append(i + 1).append(". ").append(choices.get(i));
        }
        eb.setDescription(body.toString());

        final String baseId = "ordered:" + Long.toHexString(System.nanoTime());
        List<ActionRow> rows = buildChoiceRows(baseId);

        channel.sendMessageEmbeds(eb.build()).setComponents(rows).queue(prompt -> {
            if (waiter == null) return;
            waiter.waitForEvent(
                    ButtonInteractionEvent.class,
                    ev -> ev.getMessageIdLong() == prompt.getIdLong()
                            && ev.getComponentId().startsWith(baseId)
                            && (userIds.isEmpty() || userIds.contains(ev.getUser().getId())),
                    ev -> {
                        ev.deferEdit().queue();
                        String id = ev.getComponentId();
                        if (id.endsWith(":cancel")) {
                            if (cancel != null) cancel.accept(prompt);
                            prompt.editMessageComponents().queue();
                            return;
                        }

                        String[] parts = id.split(":");
                        int index = Integer.parseInt(parts[parts.length - 1]);
                        if (selection != null) selection.accept(prompt, index);
                        prompt.editMessageComponents().queue();
                    },
                    timeout,
                    timeoutUnit,
                    () -> prompt.editMessageComponents().queue()
            );
        });
    }

    private List<ActionRow> buildChoiceRows(String baseId) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            int idx = i + 1;
            buttons.add(Button.primary(baseId + ":choice:" + idx, String.valueOf(idx)));
        }
        buttons.add(Button.danger(baseId + ":cancel", "キャンセル"));

        List<ActionRow> rows = new ArrayList<>();
        List<Button> current = new ArrayList<>();
        for (Button b : buttons) {
            current.add(b);
            if (current.size() == 5) {
                rows.add(ActionRow.of(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) rows.add(ActionRow.of(current));
        return rows;
    }

    public static class Builder {
        private final List<String> choices = new ArrayList<>();
        private EventWaiter waiter;
        private String text;
        private Color color;
        private BiConsumer<Message, Integer> selection;
        private Consumer<Message> cancel;
        private final Set<String> userIds = new HashSet<>();
        private long timeout = 1;
        private TimeUnit timeoutUnit = TimeUnit.MINUTES;

        public Builder addChoice(String choice) {
            choices.add(choice);
            return this;
        }

        public Builder addChoices(String choice) {
            choices.add(choice);
            return this;
        }

        public Builder setChoices() {
            choices.clear();
            return this;
        }

        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        public Builder setColor(Color color) {
            this.color = color;
            return this;
        }

        public Builder setSelection(BiConsumer<Message, Integer> selection) {
            this.selection = selection;
            return this;
        }

        public Builder setCancel(Consumer<Message> cancel) {
            this.cancel = cancel;
            return this;
        }

        public Builder setUsers(User... users) {
            userIds.clear();
            if (users != null) {
                for (User user : users) {
                    if (user != null) userIds.add(user.getId());
                }
            }
            return this;
        }

        public Builder useNumbers() {
            return this;
        }

        public Builder allowTextInput(boolean ignored) {
            return this;
        }

        public Builder useCancelButton(boolean ignored) {
            return this;
        }

        public Builder setEventWaiter(EventWaiter waiter) {
            this.waiter = waiter;
            return this;
        }

        public Builder setTimeout(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.timeoutUnit = unit;
            return this;
        }

        public OrderedMenu build() {
            return new OrderedMenu(this);
        }
    }
}
