package dev.rocks.infinitecraft.discovery;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.rocks.infinitecraft.item.FusionOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class DiscoveryDialogs {
    // Splits normalized item names and IDs on punctuation, spaces, and other non-alphanumeric characters.
    private static final Pattern SEARCH_WORD_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final int PAGE_SIZE = 25;
    private static final int ROW_HEIGHT = 5 * 9 + 8; // Five font lines plus vanilla text-widget padding.
    private static final String EMPTY_ROW = " \n \n \n \n ";

    private DiscoveryDialogs() {
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage) {
        return createDialog(entries, requestedPage, false);
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage, boolean personal) {
        return createDialog(entries, requestedPage, personal, 0, 1);
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage,
            boolean personal, int itemId, int returnPage) {
        return createDialog(entries, requestedPage, personal, itemId, returnPage, null);
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage,
            boolean personal, int itemId, int returnPage, String pageCommandOverride) {
        return createDialog(entries, requestedPage, personal, itemId, returnPage, pageCommandOverride, Set.of(), false);
    }

    public static NoticeDialog createDialog(List<DiscoveryCollection.Entry> entries, int requestedPage,
            boolean personal, int itemId, int returnPage, String pageCommandOverride, Set<Integer> favorites, boolean favoritesOnly) {
        if (favoritesOnly) entries = entries.stream().filter(entry -> favorites.contains(entry.id())).toList();
        var groups = DiscoveryCollection.groupResults(entries);
        var selected = itemId > 0 ? entries.stream().filter(entry -> entry.id() == itemId).findFirst()
                : Optional.<DiscoveryCollection.Entry>empty();
        var alternatives = selected.map(entry -> groups.get(new DiscoveryCollection.ResultKey(entry.result()))).orElse(List.of());
        boolean detail = itemId > 0 && alternatives.size() > 1;
        entries = detail ? alternatives : groups.values().stream().map(List::getFirst).toList();
        entries = entries.reversed();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * PAGE_SIZE;
        List<DialogBody> body = new ArrayList<>();
        String pageCommand = detail ? "/fusion collection item " + itemId + " " + returnPage + " "
                : pageCommandOverride == null ? "/fusion collection " : pageCommandOverride;
        String entryType = detail ? "recipe" : "discovery";
        String entryCount = entries.size() + " " + entryType + (entries.size() == 1 ? "" : "s");
        body.add(new PlainMessage(Component.literal(entryCount + "  ·  Page " + page + " of " + pages)
                .withStyle(ChatFormatting.GRAY), 320));
        var navigation = new PlainMessage(Component.empty()
                .append(pageArrow("<", "Previous page", pageCommand + (page - 1), page > 1))
                .append(Component.literal("   "))
                .append(searchButton())
                .append(Component.literal("   [" + (favoritesOnly ? "★" : "☆") + "]").withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(favoritesOnly ? "Show all discoveries" : "Show favorites")))
                        .withClickEvent(new ClickEvent.RunCommand(favoritesOnly ? "/fusion collection 1" : "/fusion collection favorites 1"))))
                .append(Component.literal("   "))
                .append(pageArrow(">", "Next page", pageCommand + (page + 1), page < pages)), 320);
        body.add(navigation);
        for (int index = start; index < Math.min(entries.size(), start + PAGE_SIZE); index++) {
            var entry = entries.get(index);
            int discoveryId = entry.id() > 0 ? entry.id() : entries.size() - index;
            var resultRecipes = groups.get(new DiscoveryCollection.ResultKey(entry.result()));
            int recipeCount = resultRecipes.size();
            var name = itemName(entry.result(), false).copy();
            var description = Component.empty()
                    .append(name)
                    .append(Component.literal("\n\n"))
                    .append(itemName(entry.first(), true))
                    .append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
                    .append(itemName(entry.second(), true));
            if (!personal) description.append(Component.literal("\n\n"))
                    .append(discovererLine(DiscoveryCollection.discoverers(detail ? List.of(entry) : resultRecipes)));
            else description.append(Component.literal("\n\n"));
            if (!detail && recipeCount > 1) {
                if (!personal) description.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY));
                description.append(Component.literal("[+" + (recipeCount - 1) + "]").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/fusion collection item " + discoveryId + " " + page + " 1"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("View recipes")))));
                if (personal) description.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY));
            }
            if (personal) description.append(Component.literal("[Share]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                            .withClickEvent(new ClickEvent.RunCommand("/fusion share " + discoveryId))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Share recipe in chat")))));
            description.append(Component.literal(" [" + (favorites.contains(discoveryId) ? "★" : "☆") + "]")
                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(favorites.contains(discoveryId) ? "Remove favorite" : "Favorite")))
                            .withClickEvent(new ClickEvent.RunCommand("/fusion favorite " + discoveryId
                                    + (favoritesOnly ? " favorites " : " page ") + page))));
            body.add(new ItemBody(ItemStackTemplate.fromNonEmptyStack(displayStack(entry.result())),
                    Optional.of(new PlainMessage(description, 280)), false, true, 32, ROW_HEIGHT));
        }
        // Reserve every row so short pages keep the same viewport and navigation positions.
        while (body.size() < PAGE_SIZE + 2) {
            String text = entries.isEmpty() && body.size() == 2
                    ? "No discoveries yet." + "\n \n \n \n " : EMPTY_ROW;
            body.add(new PlainMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), 320));
        }
        body.add(navigation);
        var common = new CommonDialogData(Component.literal("Discovery Book").withStyle(style -> style.withColor(0xFFAA00).withItalic(false)),
                Optional.empty(), true, false, DialogAction.NONE, List.copyOf(body), List.of());
        var close = new ActionButton(new CommonButtonData(Component.literal(detail ? "Back" : "Close"), 150),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(detail ? "/fusion collection back " + returnPage : "/fusion collection close"))));
        return new NoticeDialog(common, close);
    }

    public static ConfirmationDialog createSearchDialog() {
        var common = new CommonDialogData(Component.literal("Search discoveries")
                .withStyle(style -> style.withColor(0xFFAA00).withItalic(false)), Optional.empty(), true, false,
                DialogAction.NONE, List.of(), List.of(new Input("query",
                        new TextInput(280, Component.literal("Search"), false, "", 80, Optional.empty()))));
        var template = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                new JsonPrimitive("/fusion collection query $(query)")).getOrThrow();
        var search = new ActionButton(new CommonButtonData(Component.literal("Search"), 150),
                Optional.of(new CommandTemplate(template)));
        var back = new ActionButton(new CommonButtonData(Component.literal("Back"), 150),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/fusion collection back 1"))));
        return new ConfirmationDialog(common, search, back);
    }

    public static List<DiscoveryCollection.Entry> search(List<DiscoveryCollection.Entry> entries, String query) {
        // Treat any run of whitespace as one separator between search terms.
        var terms = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
        if (terms.isEmpty()) return entries;
        return entries.stream().filter(entry -> {
            var fields = List.of(entry.result().getHoverName().getString(), entry.first().getHoverName().getString(),
                    entry.second().getHoverName().getString(), entry.discoverer(), entry.result().getItem().toString(),
                    entry.first().getItem().toString(), entry.second().getItem().toString()).stream()
                    .map(text -> text.toLowerCase(Locale.ROOT)).toList();
            var words = fields.stream().flatMap(field -> Arrays.stream(SEARCH_WORD_SEPARATOR.split(field))).toList();
            return terms.stream().allMatch(term -> fields.stream().anyMatch(field -> field.contains(term))
                    || term.length() >= 4 && words.stream().anyMatch(word -> fuzzyMatch(word, term)));
        }).toList();
    }

    private static boolean fuzzyMatch(String word, String query) {
        int tolerance = Math.max(1, Math.max(word.length(), query.length()) / 3);
        if (Math.abs(word.length() - query.length()) > tolerance) return false;
        int[] previous = IntStream.rangeClosed(0, query.length()).toArray();
        for (int row = 1; row <= word.length(); row++) {
            int[] current = new int[query.length() + 1];
            current[0] = row;
            for (int column = 1; column <= query.length(); column++) current[column] = Math.min(
                    Math.min(current[column - 1] + 1, previous[column] + 1),
                    previous[column - 1] + (word.charAt(row - 1) == query.charAt(column - 1) ? 0 : 1));
            previous = current;
        }
        return previous[query.length()] <= tolerance;
    }

    private static Component searchButton() {
        return Component.literal("[ ")
                .append(Component.literal("🔎").withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                .append(Component.literal(" ]"))
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Search discoveries")))
                        .withClickEvent(new ClickEvent.RunCommand("/fusion collection search")));
    }

    private static Component pageArrow(String glyph, String tooltip, String command, boolean available) {
        return Component.literal("[ ")
                .append(Component.literal(glyph).withStyle(style -> style.withBold(true)
                        .withColor(available ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)))
                .append(Component.literal(" ]"))
                .withStyle(style -> style
                .withColor(available ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip)))
                .withClickEvent(available ? new ClickEvent.RunCommand(command) : null));
    }

    private static Component itemName(ItemStack stack, boolean ingredient) {
        var name = stack.getHoverName();
        var label = ingredient
                ? Component.literal(shortText(name.getString(), 20)).withStyle(ChatFormatting.GRAY)
                : name.getString().length() > 32
                        ? Component.literal(shortText(name.getString(), 32)).setStyle(name.getStyle()) : name.copy();
        return label.withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(displayStack(stack)))));
    }

    public static ItemStack displayStack(ItemStack stack) {
        return FusionOrigin.strip(stack);
    }

    public static Component discovererLine(List<String> names) {
        return discovererLine(names, 2);
    }

    public static Component discovererLine(List<String> names, int visibleNames) {
        if (names.isEmpty()) return Component.empty();
        var ink = ChatFormatting.GRAY; // Dialogs sit on a dark background.
        var line = Component.empty()
                .append(Component.literal("By ").withStyle(ink));
        int shown = Math.min(Math.max(1, visibleNames), names.size());
        for (int index = 0; index < shown; index++) {
            if (index > 0) line.append(Component.literal(names.size() == 2 ? " and " : ", ")
                    .withStyle(ink));
            String name = names.get(index);
            line.append(Component.object(new PlayerSprite(
                    ResolvableProfile.createUnresolved(name), true), Component.empty()))
                    .append(Component.literal(" " + name).withStyle(ink));
        }
        if (names.size() > shown) {
            var more = Component.empty();
            for (int index = shown; index < names.size(); index++) {
                if (index > shown) more.append("\n");
                String name = names.get(index);
                more.append(Component.object(new PlayerSprite(
                        ResolvableProfile.createUnresolved(name), true), Component.empty()))
                        .append(Component.literal(" " + name).withStyle(ChatFormatting.GRAY));
            }
            int remaining = names.size() - shown;
            line.append(Component.literal(" and " + remaining + " more").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withHoverEvent(new HoverEvent.ShowText(more))));
        }
        return line;
    }

    private static String shortText(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }
}
