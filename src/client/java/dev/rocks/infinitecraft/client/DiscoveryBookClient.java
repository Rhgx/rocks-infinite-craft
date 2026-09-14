package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.DiscoveryCollection;
import dev.rocks.infinitecraft.DiscoveryBook;
import dev.rocks.infinitecraft.DiscoveryScreenPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

final class DiscoveryBookClient {
    private DiscoveryBookClient() {}

    static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(DiscoveryScreenPayload.TYPE, (payload, context) ->
                context.client().setScreenAndShow(new CollectionScreen(payload.entries(), payload.personal())));
    }

    private static final class CollectionScreen extends Screen {
        private static final int ROWS = 8;
        private final List<DiscoveryCollection.Entry> recipes;
        private final Map<DiscoveryCollection.ResultKey, List<DiscoveryCollection.Entry>> recipesByResult;
        private final Map<DiscoveryCollection.ResultKey, DiscoveryCollection.Entry> discoveredOutputs;
        private final boolean personal;
        private List<DiscoveryCollection.Entry> results = List.of();
        private DiscoveryCollection.Entry selected;
        private String query = "";
        private int page;
        private int detailScroll;
        private int left;
        private int top;
        private int panelWidth;
        private boolean draggingDetailScrollbar;
        private final List<DiscoveryRowButton> rowButtons = new ArrayList<>();
        private Button previousButton;
        private Button nextButton;
        private EditBox searchBox;

        CollectionScreen(List<DiscoveryCollection.Entry> recipes, boolean personal) {
            super(Component.literal("Discovery Book"));
            this.recipes = List.copyOf(recipes);
            this.recipesByResult = DiscoveryCollection.groupResults(this.recipes);
            this.discoveredOutputs = recipesByResult.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> entry.getValue().getFirst()));
            this.personal = personal;
        }

        @Override protected void init() {
            draggingDetailScrollbar = false;
            rowButtons.clear();
            left = Math.max(10, (width - 410) / 2);
            top = Math.max(10, (height - 238) / 2);
            panelWidth = Math.min(410, width - 20);

            searchBox = new EditBox(font, left + 126, top + 8, Math.max(80, panelWidth - 190), 18,
                    Component.literal("Search discoveries"));
            searchBox.setHint(Component.literal("Search…").withStyle(ChatFormatting.DARK_GRAY));
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
            var close = Button.builder(Component.literal("✕"), button -> onClose())
                    .bounds(left + panelWidth - 28, top + 8, 20, 18).build();
            close.setTooltip(Tooltip.create(Component.literal("Close")));
            addRenderableWidget(close);

            int listWidth = Math.min(190, panelWidth / 2 - 6);
            for (int row = 0; row < ROWS; row++) {
                int selectedRow = row;
                var button = new DiscoveryRowButton(left + 8, top + 35 + row * 22, listWidth,
                        () -> selectRow(selectedRow));
                rowButtons.add(button);
                addRenderableWidget(button);
            }
            previousButton = Button.builder(Component.literal("‹"), button -> {
                page--;
                selected = null;
                detailScroll = 0;
                refreshRows();
            })
                    .bounds(left + 8, top + 214, 28, 18).build();
            addRenderableWidget(previousButton);
            nextButton = Button.builder(Component.literal("›"), button -> {
                page++;
                selected = null;
                detailScroll = 0;
                refreshRows();
            })
                    .bounds(left + listWidth - 20, top + 214, 28, 18).build();
            addRenderableWidget(nextButton);
            refreshRows();
        }

        private void selectRow(int row) {
            int index = page * ROWS + row;
            if (index >= results.size()) return;
            selected = results.get(index);
            detailScroll = 0;
        }

        private void refreshRows() {
            results = uniqueResults(filteredRecipes()).reversed();
            int pages = Math.max(1, (results.size() + ROWS - 1) / ROWS);
            page = Math.clamp(page, 0, pages - 1);
            int listWidth = Math.min(190, panelWidth / 2 - 6);
            for (int row = 0; row < rowButtons.size(); row++) {
                int index = page * ROWS + row;
                var button = rowButtons.get(row);
                button.visible = index < results.size();
                if (button.visible) button.setStack(results.get(index).result());
            }
            if (previousButton != null) previousButton.active = page > 0;
            if (nextButton != null) nextButton.active = page + 1 < pages;
        }

        private List<DiscoveryCollection.Entry> filteredRecipes() {
            return DiscoveryBook.search(recipes, query);
        }

        private static List<DiscoveryCollection.Entry> uniqueResults(List<DiscoveryCollection.Entry> entries) {
            return DiscoveryCollection.uniqueResults(entries);
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
            if (searchBox != null) searchBox.setValue("");
            results = uniqueResults(recipes).reversed();
            selected = target;
            detailScroll = 0;
            int index = results.indexOf(target);
            page = index < 0 ? 0 : index / ROWS;
            refreshRows();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
        }

        @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (selected != null && event.button() == 0) {
                var alternatives = alternatives(selected);
                if (alternatives.size() > 4 && overDetailScrollbar(event.x(), event.y())) {
                    draggingDetailScrollbar = true;
                    scrollDetailsTo(event.y(), alternatives.size());
                    return true;
                }
                int x = left + Math.min(204, panelWidth / 2) + 10;
                int y = top + 88;
                for (var recipe : alternatives.stream().skip(detailScroll).limit(4).toList()) {
                    if (event.x() >= x && event.x() < x + 18 && event.y() >= y && event.y() < y + 18
                            && discoveredOutput(recipe.first()) != null) {
                        openIngredient(recipe.first());
                        return true;
                    }
                    if (event.x() >= x + 54 && event.x() < x + 72 && event.y() >= y && event.y() < y + 18
                            && discoveredOutput(recipe.second()) != null) {
                        openIngredient(recipe.second());
                        return true;
                    }
                    if (personal && event.x() >= x + 82 && event.x() < x + 120
                            && event.y() >= y + 2 && event.y() < y + 17) {
                        if (minecraft.player != null)
                            minecraft.player.connection.sendCommand("fusion share " + recipe.id());
                        onClose();
                        return true;
                    }
                    y += 23;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (draggingDetailScrollbar && selected != null) {
                scrollDetailsTo(event.y(), alternatives(selected).size());
                return true;
            }
            return super.mouseDragged(event, dragX, dragY);
        }

        @Override public boolean mouseReleased(MouseButtonEvent event) {
            if (draggingDetailScrollbar) {
                draggingDetailScrollbar = false;
                return true;
            }
            return super.mouseReleased(event);
        }

        private boolean overDetailScrollbar(double mouseX, double mouseY) {
            return mouseX >= detailTrackX() - 3 && mouseX < detailTrackX() + 5
                    && mouseY >= detailTrackY() && mouseY < detailTrackY() + detailTrackHeight();
        }

        private void scrollDetailsTo(double mouseY, int recipeCount) {
            int maximum = recipeCount - 4;
            if (maximum <= 0) return;
            int thumbHeight = detailThumbHeight(recipeCount);
            int travel = detailTrackHeight() - thumbHeight;
            int thumbTop = Math.clamp((int) Math.round(mouseY - detailTrackY() - thumbHeight / 2.0), 0, travel);
            detailScroll = Math.clamp((int) Math.round((double) thumbTop * maximum / travel), 0, maximum);
        }

        private int detailTrackX() { return left + panelWidth - 8; }
        private int detailTrackY() { return top + 86; }
        private int detailTrackHeight() { return 94; }
        private int detailThumbHeight(int recipeCount) {
            return Math.max(12, detailTrackHeight() * 4 / recipeCount);
        }

        @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            if (selected != null) {
                int paneX = left + Math.min(204, panelWidth / 2);
                if (mouseX >= paneX && mouseX < left + panelWidth && mouseY >= top + 84 && mouseY < top + 184) {
                    int maximum = Math.max(0, alternatives(selected).size() - 4);
                    int next = Math.clamp(detailScroll - (int) Math.signum(vertical), 0, maximum);
                    if (next != detailScroll) {
                        detailScroll = next;
                        return true;
                    }
                }
            }
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }

        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
            graphics.fill(left, top, left + panelWidth, top + 238, 0xEE202020);
            graphics.outline(left, top, panelWidth, 238, 0xFF8B8B8B);
            int divider = left + Math.min(204, panelWidth / 2);
            graphics.verticalLine(divider, top + 31, top + 207, 0xFF555555);
        }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.text(font, title.copy().withStyle(ChatFormatting.GOLD), left + 8, top + 13, 0xFFFFFFFF);
            int pages = Math.max(1, (results.size() + ROWS - 1) / ROWS);
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pages).withStyle(ChatFormatting.GRAY),
                    left + Math.min(190, panelWidth / 2 - 6) / 2 + 8, top + 219, 0xFFFFFFFF);
            drawDetails(graphics, mouseX, mouseY);
        }

        private final class DiscoveryRowButton extends Button {
            private ItemStack stack = ItemStack.EMPTY;

            DiscoveryRowButton(int x, int y, int width, Runnable action) {
                super(x, y, width, 20, Component.empty(), ignored -> action.run(), DEFAULT_NARRATION);
            }

            void setStack(ItemStack value) {
                stack = DiscoveryBook.displayStack(value);
                setMessage(stack.getHoverName());
            }

            @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                extractDefaultSprite(graphics);
                graphics.item(stack, getX() + 4, getY() + 2);
                graphics.enableScissor(getX() + 24, getY(), getX() + getWidth() - 4, getY() + getHeight());
                graphics.text(font, stack.getHoverName(), getX() + 24, getY() + 6, 0xFFFFFFFF);
                graphics.disableScissor();
                if (mouseX >= getX() + 3 && mouseX < getX() + 21
                        && mouseY >= getY() + 1 && mouseY < getY() + 19)
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }

        private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            int x = left + Math.min(204, panelWidth / 2) + 10;
            if (selected == null) {
                graphics.centeredText(font, results.isEmpty() ? "No discoveries yet" : "Select an item",
                        x + (left + panelWidth - x) / 2, top + 112, 0xFF888888);
                return;
            }
            var displayedResult = DiscoveryBook.displayStack(selected.result());
            graphics.item(displayedResult, x, top + 42);
            graphics.enableScissor(x + 22, top + 42, left + panelWidth - 8, top + 60);
            graphics.text(font, displayedResult.getHoverName(), x + 22, top + 46, 0xFFFFFFFF);
            graphics.disableScissor();
            if (mouseX >= x && mouseX < x + 18 && mouseY >= top + 42 && mouseY < top + 60)
                graphics.setTooltipForNextFrame(font, displayedResult, mouseX, mouseY);
            var alternatives = alternatives(selected);
            graphics.text(font, alternatives.size() + (alternatives.size() == 1 ? " recipe" : " recipes"),
                    x, top + 69, 0xFFAAAAAA);
            int y = top + 88;
            graphics.enableScissor(x, top + 84, left + panelWidth - 8, top + 184);
            for (var recipe : alternatives.stream().skip(detailScroll).limit(4).toList()) {
                var first = DiscoveryBook.displayStack(recipe.first());
                var second = DiscoveryBook.displayStack(recipe.second());
                graphics.item(first, x, y);
                graphics.item(second, x + 54, y);
                graphics.centeredText(font, "+", x + 35, y + 5, 0xFFAAAAAA);
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18)
                    graphics.setTooltipForNextFrame(font, first, mouseX, mouseY);
                if (mouseX >= x + 54 && mouseX < x + 72 && mouseY >= y && mouseY < y + 18)
                    graphics.setTooltipForNextFrame(font, second, mouseX, mouseY);
                if ((mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18
                        && discoveredOutput(recipe.first()) != null)
                        || (mouseX >= x + 54 && mouseX < x + 72 && mouseY >= y && mouseY < y + 18
                        && discoveredOutput(recipe.second()) != null))
                    graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                if (personal) {
                    boolean hoveringShare = mouseX >= x + 82 && mouseX < x + 120
                            && mouseY >= y + 2 && mouseY < y + 17;
                    graphics.text(font, Component.literal("Share").withStyle(hoveringShare
                                    ? ChatFormatting.YELLOW : ChatFormatting.GOLD), x + 82, y + 5, 0xFFFFFFFF);
                    if (hoveringShare) {
                        graphics.setTooltipForNextFrame(font, Component.literal("Share this recipe"), mouseX, mouseY);
                        graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                    }
                }
                y += 23;
            }
            graphics.disableScissor();
            if (alternatives.size() > 4) {
                int trackX = detailTrackX();
                int trackY = detailTrackY();
                int trackHeight = detailTrackHeight();
                int thumbHeight = detailThumbHeight(alternatives.size());
                int maximum = alternatives.size() - 4;
                int thumbY = trackY + (trackHeight - thumbHeight) * detailScroll / maximum;
                graphics.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0xFF444444);
                graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight,
                        overDetailScrollbar(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFAAAAAA);
                if (overDetailScrollbar(mouseX, mouseY))
                    graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            }
            if (!personal) {
                graphics.enableScissor(x, top + 188, left + panelWidth - 8, top + 205);
                var names = DiscoveryCollection.discoverers(alternatives);
                var byline = DiscoveryBook.discovererLine(names);
                if (font.width(byline) > left + panelWidth - 8 - x)
                    byline = DiscoveryBook.discovererLine(names, 1);
                graphics.text(font, byline, x, top + 194, 0xFFFFFFFF);
                graphics.disableScissor();
                if (names.size() > 2) {
                    var more = byline.getSiblings().getLast();
                    int moreX = x + font.width(byline) - font.width(more);
                    if (mouseX >= moreX && mouseX < moreX + font.width(more)
                            && mouseY >= top + 192 && mouseY < top + 205
                            && more.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText hover)
                        graphics.setTooltipForNextFrame(font, hover.value(), mouseX, mouseY);
                }
            }
        }

        @Override public boolean isPauseScreen() { return false; }
    }
}
