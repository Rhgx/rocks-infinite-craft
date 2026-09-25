package dev.rocks.infinitecraft.client.discovery;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.rocks.infinitecraft.discovery.DiscoveryBook;
import dev.rocks.infinitecraft.discovery.DiscoveryCollection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** An open two-page book: discoveries listed on the left page, the selected one's recipes and uses on the right. */
final class DiscoveryBookScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("rocks_infinite_craft",
            "textures/gui/discovery_book.png");
    // An ingredient slot pressed into the paper.
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath("rocks_infinite_craft", "discovery/slot");
    private static final Identifier SEARCH = Identifier.withDefaultNamespace("icon/search");
    private static final int BOOK_WIDTH = 280;
    private static final int BOOK_HEIGHT = 180;
    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 20;
    private static final int RECIPES = 3;
    private static final int RECIPE_HEIGHT = 22;
    private static final int INK = 0xFF3B2A1A;
    private static final int FAINT = 0xFF8C7A5B;
    private static final int HEADING = 0xFF8A3F25;
    private static final int STAR = 0xFFFFAA00;
    private static final int STAR_HOVER = 0xFFFFC94A;
    private static final int AMETHYST = 0xFF5D3A9A;
    // Page geometry, relative to the book's top-left corner.
    private static final int LEFT_PAGE = 14;
    private static final int RIGHT_PAGE = 152;
    private static final int PAGE_WIDTH = 114;
    private static final int LIST_Y = 30;
    private static final int RECIPES_Y = 82;
    // The divider under the name: "---- 3 recipes ----", or "Made from · Used in" tabs once the item is used anywhere.
    private static final int TABS_Y = 69;
    private static final String MADE = "Made from";
    private static final String USED = "Used in";
    private static final String SEPARATOR = " · ";

    private enum Sort {
        NEWEST("New", "newest first"), OLDEST("Old", "oldest first"), NAME("A-Z", "by name");
        final String label;
        final String description;
        Sort(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }
    // Kept across openings for the rest of the session.
    private static Sort sort = Sort.NEWEST;

    private final List<DiscoveryCollection.Entry> recipes;
    private final Map<DiscoveryCollection.ResultKey, List<DiscoveryCollection.Entry>> recipesByResult;
    private final Map<DiscoveryCollection.ResultKey, DiscoveryCollection.Entry> discoveredOutputs;
    private final Map<DiscoveryCollection.ResultKey, List<DiscoveryCollection.Entry>> usesByIngredient = new LinkedHashMap<>();
    private final boolean personal;
    private final Set<Integer> favorites;
    private boolean favoritesOnly;
    // The right page lists recipes that use the selected item instead of ones that make it.
    private boolean showingUses;
    private InkButton sortButton;
    private InkButton favoriteButton;
    private InkButton favoritesFilter;
    private List<DiscoveryCollection.Entry> results = List.of();
    private DiscoveryCollection.Entry selected;
    private String query = "";
    private int page;
    private int detailScroll;
    private int left;
    private int top;
    private boolean draggingDetailScrollbar;
    private final List<DiscoveryRowButton> rowButtons = new ArrayList<>();
    private PageButton previousButton;
    private PageButton nextButton;
    private EditBox searchBox;

    DiscoveryBookScreen(List<DiscoveryCollection.Entry> recipes, boolean personal, Set<Integer> favorites) {
        super(Component.literal("Discovery Book"));
        this.recipes = List.copyOf(recipes);
        this.recipesByResult = DiscoveryCollection.groupResults(this.recipes);
        this.discoveredOutputs = recipesByResult.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().getFirst()));
        this.personal = personal;
        this.favorites = new HashSet<>(favorites);
        for (var recipe : this.recipes) {
            var first = new DiscoveryCollection.ResultKey(recipe.first());
            var second = new DiscoveryCollection.ResultKey(recipe.second());
            usesByIngredient.computeIfAbsent(first, ignored -> new ArrayList<>()).add(recipe);
            if (!second.equals(first)) usesByIngredient.computeIfAbsent(second, ignored -> new ArrayList<>()).add(recipe);
        }
    }

    @Override
    protected void init() {
        draggingDetailScrollbar = false;
        rowButtons.clear();
        left = (width - BOOK_WIDTH) / 2;
        top = Math.max(2, (height - BOOK_HEIGHT - 26) / 2);

        searchBox = new EditBox(font, left + LEFT_PAGE + 17, top + 15, 62, 10, Component.literal("Search discoveries"));
        searchBox.setBordered(false);
        searchBox.setTextColor(INK);
        searchBox.setTextShadow(false);
        searchBox.setMaxLength(80);
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            if (value.equals(query)) return;
            query = value;
            page = 0;
            selected = null;
            detailScroll = 0;
            refreshRows();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        sortButton = addRenderableWidget(new InkButton(left + LEFT_PAGE + PAGE_WIDTH - 33, top + 12, 20, 14,
                FAINT, HEADING, button -> {
                    sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                    page = 0;
                    selected = null;
                    refreshRows();
                }));
        favoritesFilter = addRenderableWidget(new InkButton(left + LEFT_PAGE + PAGE_WIDTH - 12, top + 12, 12, 14,
                STAR, STAR_HOVER, button -> {
                    favoritesOnly = !favoritesOnly;
                    page = 0;
                    selected = null;
                    refreshRows();
                }));
        favoriteButton = addRenderableWidget(new InkButton(left + RIGHT_PAGE + PAGE_WIDTH - 12, top + 12, 12, 14,
                STAR, STAR_HOVER, button -> {
                    if (selected == null || minecraft.player == null) return;
                    minecraft.player.connection.sendCommand("fusion favorite " + selected.id());
                    boolean remove = favorites.contains(selected.id());
                    for (var recipe : alternatives(selected)) {
                        if (remove) favorites.remove(recipe.id()); else favorites.add(recipe.id());
                    }
                    refreshRows();
                }));

        for (int row = 0; row < ROWS; row++) {
            int index = row;
            var button = new DiscoveryRowButton(left + LEFT_PAGE - 2, top + LIST_Y + row * ROW_HEIGHT,
                    PAGE_WIDTH + 4, () -> selectRow(index));
            rowButtons.add(button);
            addRenderableWidget(button);
        }
        previousButton = addRenderableWidget(new PageButton(left + LEFT_PAGE, top + 154, false,
                button -> turnPage(-1), true));
        nextButton = addRenderableWidget(new PageButton(left + LEFT_PAGE + PAGE_WIDTH - 23, top + 154, true,
                button -> turnPage(1), true));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 49, top + BOOK_HEIGHT + 4, 98, 20).build());
        refreshRows();
    }

    private void turnPage(int direction) {
        page += direction;
        selected = null;
        detailScroll = 0;
        refreshRows();
    }

    private void selectRow(int row) {
        int index = page * ROWS + row;
        if (index >= results.size()) return;
        selected = results.get(index);
        showingUses = false;
        detailScroll = 0;
        refreshRows();
    }

    private int pages() {
        return Math.max(1, (results.size() + ROWS - 1) / ROWS);
    }

    private void refreshRows() {
        results = sorted(DiscoveryCollection.uniqueResults(filteredRecipes()));
        page = Math.clamp(page, 0, pages() - 1);
        for (int row = 0; row < rowButtons.size(); row++) {
            int index = page * ROWS + row;
            var button = rowButtons.get(row);
            button.visible = index < results.size();
            if (button.visible) button.setEntry(results.get(index));
        }
        if (previousButton != null) previousButton.visible = page > 0;
        if (nextButton != null) nextButton.visible = page + 1 < pages();
        if (sortButton != null) {
            sortButton.setMessage(Component.literal(sort.label));
            sortButton.setTooltip(Tooltip.create(Component.literal("Sorted " + sort.description + ". Click to change.")));
        }
        if (favoritesFilter != null) {
            favoritesFilter.setMessage(Component.literal(favoritesOnly ? "★" : "☆"));
            favoritesFilter.setTooltip(Tooltip.create(Component.literal(favoritesOnly
                    ? "Showing favorites only. Click to show everything." : "Show favorites only")));
        }
        if (favoriteButton != null) {
            boolean favorite = selected != null && favorites.contains(selected.id());
            favoriteButton.visible = selected != null;
            favoriteButton.setMessage(Component.literal(favorite ? "★" : "☆"));
            favoriteButton.setTooltip(Tooltip.create(Component.literal(favorite ? "Remove from favorites" : "Add to favorites")));
        }
    }

    private List<DiscoveryCollection.Entry> filteredRecipes() {
        return DiscoveryBook.search(recipes, query).stream()
                .filter(entry -> !favoritesOnly || favorites.contains(entry.id())).toList();
    }

    private static List<DiscoveryCollection.Entry> sorted(List<DiscoveryCollection.Entry> unique) {
        return switch (sort) {
            case NEWEST -> unique.reversed();
            case OLDEST -> unique;
            case NAME -> unique.stream().sorted(Comparator.comparing(
                    (DiscoveryCollection.Entry entry) -> entry.result().getHoverName().getString(),
                    String.CASE_INSENSITIVE_ORDER)).toList();
        };
    }

    private List<DiscoveryCollection.Entry> uses(DiscoveryCollection.Entry entry) {
        return usesByIngredient.getOrDefault(new DiscoveryCollection.ResultKey(entry.result()), List.of());
    }

    /** What the right page lists for the selection: recipes that make it, or recipes that use it. */
    private List<DiscoveryCollection.Entry> shownRecipes() {
        return showingUses ? uses(selected) : alternatives(selected);
    }

    /** The slots of one recipe row. Uses read "selected + other = result", with the selected item first. */
    private List<ItemStack> slots(DiscoveryCollection.Entry recipe) {
        if (!showingUses) return List.of(recipe.first(), recipe.second());
        var key = new DiscoveryCollection.ResultKey(selected.result());
        return new DiscoveryCollection.ResultKey(recipe.first()).equals(key)
                ? List.of(recipe.first(), recipe.second(), recipe.result())
                : List.of(recipe.second(), recipe.first(), recipe.result());
    }

    private List<DiscoveryCollection.Entry> alternatives(DiscoveryCollection.Entry entry) {
        return recipesByResult.getOrDefault(new DiscoveryCollection.ResultKey(entry.result()), List.of());
    }

    private DiscoveryCollection.Entry discoveredOutput(ItemStack ingredient) {
        return discoveredOutputs.get(new DiscoveryCollection.ResultKey(ingredient));
    }

    private void openIngredient(ItemStack ingredient) {
        var target = discoveredOutput(ingredient);
        if (target == null) return;
        query = "";
        favoritesOnly = false;
        if (searchBox != null) searchBox.setValue("");
        results = sorted(DiscoveryCollection.uniqueResults(recipes));
        selected = target;
        showingUses = false;
        detailScroll = 0;
        int index = results.indexOf(target);
        page = index < 0 ? 0 : index / ROWS;
        refreshRows();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1));
    }

    // Recipe rows on the right page: slots 34 pixels apart joined by + and =, then an optional Share link.
    private int recipeX() { return left + RIGHT_PAGE + 4; }
    private int recipeY(int visibleIndex) { return top + RECIPES_Y + visibleIndex * RECIPE_HEIGHT; }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private List<DiscoveryCollection.Entry> visibleRecipes() {
        return shownRecipes().stream().skip(detailScroll).limit(RECIPES).toList();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (selected != null && event.button() == 0) {
            var tab = hoveredTab(event.x(), event.y());
            if (tab != null) {
                showingUses = tab;
                detailScroll = 0;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1));
                return true;
            }
            int count = shownRecipes().size();
            if (count > RECIPES && overDetailScrollbar(event.x(), event.y())) {
                draggingDetailScrollbar = true;
                scrollDetailsTo(event.y(), count);
                return true;
            }
            int x = recipeX();
            var visible = visibleRecipes();
            for (int index = 0; index < visible.size(); index++) {
                var recipe = visible.get(index);
                int y = recipeY(index);
                var slots = slots(recipe);
                for (int slot = 0; slot < slots.size(); slot++) {
                    if (inside(event.x(), event.y(), x + slot * 34, y, 18, 18) && discoveredOutput(slots.get(slot)) != null) {
                        openIngredient(slots.get(slot));
                        return true;
                    }
                }
                if (!showingUses && personal && inside(event.x(), event.y(), x + 74, y + 3, font.width("Share"), 12)) {
                    if (minecraft.player != null) minecraft.player.connection.sendCommand("fusion share " + recipe.id());
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingDetailScrollbar && selected != null) {
            scrollDetailsTo(event.y(), shownRecipes().size());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingDetailScrollbar) {
            draggingDetailScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean overDetailScrollbar(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, detailTrackX() - 2, detailTrackY(), 6, detailTrackHeight());
    }

    private void scrollDetailsTo(double mouseY, int recipeCount) {
        int maximum = recipeCount - RECIPES;
        if (maximum <= 0) return;
        int thumbHeight = detailThumbHeight(recipeCount);
        int travel = detailTrackHeight() - thumbHeight;
        int thumbTop = Math.clamp((int) Math.round(mouseY - detailTrackY() - thumbHeight / 2.0), 0, travel);
        detailScroll = Math.clamp((int) Math.round((double) thumbTop * maximum / travel), 0, maximum);
    }

    private int detailTrackX() { return left + RIGHT_PAGE + PAGE_WIDTH - 2; }
    private int detailTrackY() { return top + RECIPES_Y; }
    private int detailTrackHeight() { return RECIPES * RECIPE_HEIGHT - 4; }
    private int detailThumbHeight(int recipeCount) {
        return Math.max(10, detailTrackHeight() * RECIPES / recipeCount);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int step = (int) -Math.signum(vertical);
        // Scrolling over the left page flips pages, over the recipes it scrolls them.
        if (inside(mouseX, mouseY, left, top, BOOK_WIDTH / 2, BOOK_HEIGHT)) {
            int next = Math.clamp(page + step, 0, pages() - 1);
            if (next != page) {
                turnPage(step);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1));
            }
            return true;
        }
        if (selected != null && inside(mouseX, mouseY, left + RIGHT_PAGE, top + RECIPES_Y - 4, PAGE_WIDTH, RECIPES * RECIPE_HEIGHT + 4)) {
            detailScroll = Math.clamp(detailScroll + step, 0, Math.max(0, shownRecipes().size() - RECIPES));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);
        // Search field: a faint inked underline with the vanilla magnifier.
        int x = left + LEFT_PAGE;
        graphics.fill(x, top + 12, x + PAGE_WIDTH - 35, top + 26, 0x14000000);
        graphics.fill(x, top + 25, x + PAGE_WIDTH - 35, top + 26, 0x40000000);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEARCH, x + 2, top + 13, 12, 12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = left + LEFT_PAGE;
        if (results.isEmpty()) {
            centered(graphics, Component.literal(recipes.isEmpty() ? "Nothing discovered yet"
                    : favoritesOnly ? "No favorites yet" : "No matches"), x + PAGE_WIDTH / 2, top + 90, FAINT);
        }
        if (pages() > 1) centered(graphics, Component.literal((page + 1) + " / " + pages()),
                x + PAGE_WIDTH / 2, top + 157, FAINT);
        // Drawn here rather than with setHint, which ignores the no-shadow setting.
        if (searchBox.getValue().isEmpty())
            graphics.text(font, "Search...", searchBox.getX(), searchBox.getY(), FAINT, false);
        drawDetails(graphics, mouseX, mouseY);
    }

    /**
     * Item names keep their styling, but colors made for dark tooltips wash out on parchment,
     * so bright ones are darkened until they read like ink.
     */
    private static Component inked(ItemStack stack) {
        var name = Component.empty();
        stack.getHoverName().visit((style, text) -> {
            name.append(Component.literal(text).withStyle(style.getColor() == null ? style
                    : style.withColor(darken(style.getColor().getValue()))));
            return Optional.empty();
        }, Style.EMPTY);
        return name;
    }

    static int darken(int rgb) {
        int red = rgb >> 16 & 0xFF, green = rgb >> 8 & 0xFF, blue = rgb & 0xFF;
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
        if (luminance <= 0.4) return rgb;
        double scale = 0.4 / luminance;
        return (int) (red * scale) << 16 | (int) (green * scale) << 8 | (int) (blue * scale);
    }

    /** Cuts a styled name to fit, ending in an ellipsis. */
    private FormattedCharSequence fitted(FormattedText text, int room) {
        if (font.width(text) <= room) return Language.getInstance().getVisualOrder(text);
        var cut = font.substrByWidth(text, room - font.width("..."));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
    }

    private void centered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /** A text-only button inked straight onto the page, for the sort and star toggles. */
    private final class InkButton extends Button {
        private final int color;
        private final int hoverColor;

        InkButton(int x, int y, int width, int height, int color, int hoverColor, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.color = color;
            this.hoverColor = hoverColor;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            // Hover only: after a click the button keeps focus, which should not change its color.
            int color = isHovered() ? hoverColor : this.color;
            graphics.text(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                    getY() + (height - 8) / 2, color, false);
            if (isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private final class DiscoveryRowButton extends Button {
        private DiscoveryCollection.Entry entry;
        private ItemStack stack = ItemStack.EMPTY;

        DiscoveryRowButton(int x, int y, int width, Runnable action) {
            super(x, y, width, ROW_HEIGHT, Component.empty(), ignored -> action.run(), DEFAULT_NARRATION);
        }

        void setEntry(DiscoveryCollection.Entry value) {
            entry = value;
            stack = DiscoveryBook.displayStack(value.result());
            setMessage(stack.getHoverName());
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean chosen = entry == selected;
            if (chosen) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x305D3A9A);
                graphics.fill(getX(), getY(), getX() + 1, getY() + height, AMETHYST);
            } else if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x18000000);
            }
            graphics.item(stack, getX() + 2, getY() + 2);
            boolean favorite = favorites.contains(entry.id());
            int room = width - 23 - (favorite ? 10 : 0);
            graphics.text(font, fitted(inked(stack), room), getX() + 21, getY() + 6, chosen ? AMETHYST : INK, false);
            if (favorite) graphics.text(font, "★", getX() + width - 10, getY() + 6, STAR, false);
            if (inside(mouseX, mouseY, getX() + 2, getY() + 2, 16, 16))
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            if (isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + RIGHT_PAGE;
        int center = x + PAGE_WIDTH / 2;
        if (selected == null) {
            int total = DiscoveryCollection.uniqueResults(recipes).size();
            centered(graphics, Component.literal(total + (total == 1 ? " discovery" : " discoveries")), center, top + 66, INK);
            if (!results.isEmpty()) {
                centered(graphics, Component.literal("Pick a discovery"), center, top + 84, FAINT);
                centered(graphics, Component.literal("to see how it was made"), center, top + 95, FAINT);
            }
            return;
        }
        // The result, drawn at double size, with its name centered underneath.
        var result = DiscoveryBook.displayStack(selected.result());
        graphics.pose().pushMatrix();
        graphics.pose().translate(center - 16, top + 13);
        graphics.pose().scale(2, 2);
        graphics.item(result, 0, 0);
        graphics.pose().popMatrix();
        if (inside(mouseX, mouseY, center - 16, top + 13, 32, 32))
            graphics.setTooltipForNextFrame(font, result, mouseX, mouseY);
        // Up to two lines of name; a third would run into the recipe header, so it ends in an ellipsis.
        var lines = font.getSplitter().splitLines(inked(result), PAGE_WIDTH, Style.EMPTY);
        var name = lines;
        if (lines.size() > 2) {
            // Rejoin everything after the first line, putting back the spaces the wrap removed.
            var rest = new ArrayList<FormattedText>();
            for (var line : lines.subList(1, lines.size())) {
                if (!rest.isEmpty()) rest.add(FormattedText.of(" "));
                rest.add(line);
            }
            name = List.of(lines.getFirst(), FormattedText.composite(rest));
        }
        for (int line = 0; line < name.size(); line++) {
            var text = fitted(name.get(line), PAGE_WIDTH);
            graphics.text(font, text, center - font.width(text) / 2, top + 48 + line * 9, INK, false);
        }

        var alternatives = alternatives(selected);
        drawTabs(graphics, alternatives.size(), mouseX, mouseY);

        var visible = visibleRecipes();
        for (int index = 0; index < visible.size(); index++) {
            var recipe = visible.get(index);
            int y = recipeY(index);
            var slots = slots(recipe);
            for (int slot = 0; slot < slots.size(); slot++) {
                int slotX = recipeX() + slot * 34;
                var shown = DiscoveryBook.displayStack(slots.get(slot));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, slotX, y, 18, 18);
                graphics.item(shown, slotX + 1, y + 1);
                if (inside(mouseX, mouseY, slotX, y, 18, 18)) {
                    graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
                    if (discoveredOutput(slots.get(slot)) != null) graphics.requestCursor(CursorTypes.POINTING_HAND);
                }
            }
            graphics.text(font, "+", recipeX() + 23, y + 5, FAINT, false);
            if (showingUses) graphics.text(font, "=", recipeX() + 57, y + 5, FAINT, false);
            else if (personal) {
                int shareX = recipeX() + 74;
                boolean hovering = inside(mouseX, mouseY, shareX, y + 3, font.width("Share"), 12);
                graphics.text(font, Component.literal("Share").withStyle(style -> style.withUnderlined(hovering)),
                        shareX, y + 5, hovering ? HEADING : STAR, false);
                if (hovering) {
                    graphics.setTooltipForNextFrame(font, Component.literal("Share this recipe in chat"), mouseX, mouseY);
                    graphics.requestCursor(CursorTypes.POINTING_HAND);
                }
            }
        }
        int listed = shownRecipes().size();
        if (listed > RECIPES) {
            int trackX = detailTrackX();
            int trackY = detailTrackY();
            int thumbHeight = detailThumbHeight(listed);
            int thumbY = trackY + (detailTrackHeight() - thumbHeight) * detailScroll / (listed - RECIPES);
            boolean hovering = overDetailScrollbar(mouseX, mouseY);
            graphics.fill(trackX, trackY, trackX + 2, trackY + detailTrackHeight(), 0x20000000);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, hovering ? HEADING : 0xFFB08A60);
            if (hovering) graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        if (!personal) drawDiscoverers(graphics, alternatives, x, mouseX, mouseY);
    }

    private int tabsX() {
        return left + RIGHT_PAGE + PAGE_WIDTH / 2 - font.width(MADE + SEPARATOR + USED) / 2;
    }

    /** The closed tab under the mouse: false for Made from, true for Used in, null for neither. */
    private Boolean hoveredTab(double mouseX, double mouseY) {
        if (uses(selected).isEmpty()) return null;
        int x = tabsX();
        if (showingUses && inside(mouseX, mouseY, x, top + TABS_Y - 2, font.width(MADE), 12)) return false;
        int usedX = x + font.width(MADE + SEPARATOR);
        if (!showingUses && inside(mouseX, mouseY, usedX, top + TABS_Y - 2, font.width(USED), 12)) return true;
        return null;
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int recipeCount, int mouseX, int mouseY) {
        int x = left + RIGHT_PAGE;
        int center = x + PAGE_WIDTH / 2;
        int useCount = uses(selected).size();
        String label = useCount > 0 ? MADE + SEPARATOR + USED : recipeCount + (recipeCount == 1 ? " recipe" : " recipes");
        int half = font.width(label) / 2 + 4;
        graphics.fill(x + 4, top + TABS_Y + 4, center - half, top + TABS_Y + 5, 0x30000000);
        graphics.fill(center + half, top + TABS_Y + 4, x + PAGE_WIDTH - 4, top + TABS_Y + 5, 0x30000000);
        if (useCount == 0) {
            graphics.text(font, label, center - font.width(label) / 2, top + TABS_Y, FAINT, false);
            return;
        }
        var hovered = hoveredTab(mouseX, mouseY);
        int tabX = tabsX();
        tab(graphics, MADE, tabX, !showingUses, Boolean.FALSE.equals(hovered));
        graphics.text(font, SEPARATOR, tabX + font.width(MADE), top + TABS_Y, FAINT, false);
        tab(graphics, USED, tabX + font.width(MADE + SEPARATOR), showingUses, Boolean.TRUE.equals(hovered));
        if (hovered != null) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            graphics.setTooltipForNextFrame(font, Component.literal(hovered
                    ? useCount + (useCount == 1 ? " recipe uses this" : " recipes use this")
                    : recipeCount + (recipeCount == 1 ? " recipe makes this" : " recipes make this")), mouseX, mouseY);
        }
    }

    private void tab(GuiGraphicsExtractor graphics, String label, int x, boolean open, boolean hovered) {
        var text = Component.literal(label).withStyle(style -> style.withUnderlined(open || hovered));
        graphics.text(font, text, x, top + TABS_Y, hovered ? HEADING : open ? INK : FAINT, false);
    }

    private void drawDiscoverers(GuiGraphicsExtractor graphics, List<DiscoveryCollection.Entry> alternatives,
                                 int x, int mouseX, int mouseY) {
        var names = DiscoveryCollection.discoverers(alternatives);
        if (names.isEmpty()) return;
        int y = top + 152;
        int cursor = x + 4;
        int right = x + PAGE_WIDTH - 4;
        graphics.text(font, "By", cursor, y, FAINT, false);
        cursor += font.width("By ");
        if (names.size() == 1) {
            var head = head(names.getFirst());
            graphics.text(font, head, cursor, y, 0xFFFFFFFF, false);
            cursor += font.width(head) + 2;
            graphics.text(font, fitted(FormattedText.of(names.getFirst()), right - cursor), cursor, y, INK, false);
            return;
        }
        // Several discoverers: a row of heads that name their player on hover, then +N for any that do not fit.
        int step = font.width(head(names.getFirst())) + 2;
        int shown = names.size();
        if (cursor + shown * step > right)
            shown = Math.max(1, (right - cursor - font.width("+" + names.size())) / step);
        for (String name : names.subList(0, shown)) {
            graphics.text(font, head(name), cursor, y, 0xFFFFFFFF, false);
            if (inside(mouseX, mouseY, cursor, y - 1, step, 10))
                graphics.setTooltipForNextFrame(font, Component.literal(name), mouseX, mouseY);
            cursor += step;
        }
        if (shown < names.size()) {
            String more = "+" + (names.size() - shown);
            boolean hovering = inside(mouseX, mouseY, cursor, y - 1, font.width(more), 10);
            graphics.text(font, more, cursor, y, hovering ? HEADING : FAINT, false);
            if (hovering) {
                var lines = names.subList(shown, names.size()).stream()
                        .map(name -> Component.empty().append(head(name)).append(" " + name).getVisualOrderText())
                        .toList();
                graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    private static Component head(String name) {
        return Component.object(new PlayerSprite(ResolvableProfile.createUnresolved(name), true), Component.empty());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
