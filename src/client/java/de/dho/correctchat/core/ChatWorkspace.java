package de.dho.correctchat.core;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.ConfigManager;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.gui.ChatAreaSettingsScreen;
import de.dho.correctchat.gui.ChatHistoryScreen;
import de.dho.correctchat.mixin.ChatComponentAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Owns tab selection, message routing and the additional movable chat panels. */
public final class ChatWorkspace {
    public enum Kind { PLAYER, SYSTEM, TWITCH }

    public record SearchResult(Component component, String kind, long receivedAt, String server) { }
    private record TabButton(Button button, int windowIndex, String tabId) { }

    private final ChatHistoryStore history = new ChatHistoryStore();
    private final Map<Component, String> componentMods = new IdentityHashMap<>();
    private final Map<Component, String> sourceTexts = new IdentityHashMap<>();
    private final Map<String, String> sourceTextsByRenderedText = new HashMap<>();
    private final ChatRouting routing = new ChatRouting(componentMods);
    private final Map<String, Integer> unreadByTab = new HashMap<>();
    private final Set<String> mentionedTabs = new java.util.HashSet<>();
    private final List<TabButton> activeButtons = new ArrayList<>();
    private final Map<Component, Map<Integer, List<FormattedCharSequence>>> wrappedLines = new IdentityHashMap<>();
    private final Map<Component, Boolean> blockedAdvertisementResults = new IdentityHashMap<>();
    private AdvertisementFilter.RuleSet advertisementRules = AdvertisementFilter.compile(List.of());
    private int draggedWindow = -1;
    private int resizedWindow = -1;
    private boolean resizingPrimary;
    private boolean resizeWidth;
    private boolean resizeHeight;
    private double lastDragX;
    private double lastDragY;
    private Runnable rebuildChatWidgets;
    private String pendingCommandMod;
    private long pendingCommandModUntil;

    public void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(CorrectChatClient.MOD_ID, "chat_windows"),
                this::renderWindows);
        refreshAdvertisementRules();
        ensureEnabledTabSelection();
        applyPrimaryFilter();
    }

    public void record(Component component, Kind kind) {
        record(component, kind, component == null ? "" : component.getString());
    }

    public void record(Component component, Kind kind, String sourceText) {
        if (component == null) return;
        CorrectChatConfig config = CorrectChatClient.config();
        String sourceMod = config == null ? null : routing.detectedMod(component, tabs(config));
        if (sourceMod == null && kind == Kind.SYSTEM && System.currentTimeMillis() <= pendingCommandModUntil) {
            sourceMod = pendingCommandMod;
        }
        history.add(component, kind, currentServerKey());
        sourceTexts.put(component, sourceText == null ? component.getString() : sourceText);
        sourceTextsByRenderedText.put(component.getString(), sourceText == null ? component.getString() : sourceText);
        if (sourceMod != null) componentMods.put(component, sourceMod);
        updateUnread(component, kind);
        trim();
    }

    /** Returns the server/source text before Correct Chat added timestamps or copy controls. */
    public String sourceText(Component displayed) {
        if (displayed == null) return "";
        String exact = sourceTexts.get(displayed);
        if (exact != null) return exact;
        return sourceTextsByRenderedText.getOrDefault(displayed.getString(), displayed.getString());
    }

    public void noteCommand(String command) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || command == null) return;
        for (CorrectChatConfig.ChatTab tab : tabs(config)) {
            for (CorrectChatConfig.TabFilter filter : tab.filters) {
                if (filter.type == CorrectChatConfig.TabType.MOD && ChatRouting.commandMatchesMod(command, filter.value)) {
                    pendingCommandMod = filter.value.trim().toLowerCase(java.util.Locale.ROOT);
                    pendingCommandModUntil = System.currentTimeMillis() + ChatRouting.responseWindowMillis(pendingCommandMod);
                    return;
                }
            }
        }
    }

    public boolean shouldCaptureOverlay(Component component) {
        if (component == null) return false;
        if (ChatRouting.modMatches(component, "worldedit", 0)) return true;
        if (!"worldedit".equals(pendingCommandMod) || System.currentTimeMillis() > pendingCommandModUntil) return false;
        String text = component.getString().toLowerCase(java.util.Locale.ROOT);
        return text.contains("position") || text.contains("pos1") || text.contains("pos2")
                || text.contains("selection") || text.contains("selected");
    }

    public void onConfigChanged() {
        routing.clear();
        refreshAdvertisementRules();
        // The configuration can replace the complete window list while a previously built
        // ChatScreen still owns buttons that reference indices from the old list.
        activeButtons.clear();
        ensureEnabledTabSelection();
        trim();
        applyPrimaryFilter();
    }

    public boolean isPrimaryTwitchTab() {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.enabled || !config.chatTabsEnabled || !config.twitchEnabled) return false;
        CorrectChatConfig.ChatTab tab = findTab(primaryTab(config));
        return tab != null && tab.isTwitchOnly();
    }

    public void applyPrimaryFilter() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null || client.gui.hud == null) return;
        CorrectChatConfig config = CorrectChatClient.config();
        ChatComponent chat = client.gui.hud.getChat();
        chat.setVisibleMessageFilter(message -> {
            if (config == null || !config.enabled) return true;
            if (isBlockedAdvertisement(message.content())) return false;
            if (!config.chatTabsEnabled) return true;
            return matches(findTab(primaryTab(config)), message.content(), kindOf(message));
        });
        // setVisibleMessageFilter rebuilds Minecraft's trimmed line cache immediately. The
        // ChatComponent mixin separately makes sure filtered messages remain in allMessages so
        // that this rebuild can reveal them after switching tabs.
    }

    /**
     * Rebuilds the visible lines before the next frame is rendered. Messages must first pass
     * Minecraft's insertion-time predicate so they remain available to other tabs; delaying this
     * second filtering step until the end of a later tick would briefly show them in the active tab.
     */
    public void refreshPrimaryFilterNow() {
        applyPrimaryFilter();
    }

    public void addTabButtons(Consumer<Button> add, Runnable rebuild, int screenWidth, int screenHeight) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.enabled || !config.chatTabsEnabled) return;
        rebuildChatWidgets = rebuild;
        activeButtons.clear();
        addTabRow(add, activeButtons, -1, 4, screenHeight - 34, primaryChatWidth(screenWidth));
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        for (int index = 0; index < windows.size(); index++) {
            CorrectChatConfig.ChatWindow window = windows.get(index);
            if (!window.visible) continue;
            int x = ChatWindowRenderer.resolvedX(window, screenWidth);
            int y = ChatWindowRenderer.resolvedY(window, screenHeight);
            addTabRow(add, activeButtons, index, x + 2, y + 18, window.width - 4);
        }
        refreshButtonLabels(activeButtons);
    }

    public boolean beginWindowDrag(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (button != 0) return false;
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.enabled || !config.chatTabsEnabled) return false;
        int primaryRight = 4 + primaryChatWidth(screenWidth);
        int primaryTop = primaryChatTop(screenHeight);
        boolean primaryCorner = mouseX >= primaryRight - 14 && mouseX <= primaryRight + 3
                && mouseY >= primaryTop - 3 && mouseY <= primaryTop + 14;
        boolean primaryRightEdge = mouseX >= primaryRight - 5 && mouseX <= primaryRight + 3
                && mouseY >= primaryTop - 3 && mouseY < screenHeight - 36;
        boolean primaryTopEdge = mouseY >= primaryTop - 3 && mouseY <= primaryTop + 5
                && mouseX >= 0 && mouseX <= primaryRight + 3;
        if (primaryCorner || primaryRightEdge || primaryTopEdge) {
            resizingPrimary = true;
            resizeWidth = primaryCorner || primaryRightEdge;
            resizeHeight = primaryCorner || primaryTopEdge;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        for (int i = windows.size() - 1; i >= 0; i--) {
            CorrectChatConfig.ChatWindow window = windows.get(i);
            if (!window.visible) continue;
            int x = ChatWindowRenderer.resolvedX(window, screenWidth);
            int y = ChatWindowRenderer.resolvedY(window, screenHeight);
            int right = x + Math.min(window.width, screenWidth - x);
            int bottom = y + Math.min(window.height, screenHeight - y);
            boolean corner = mouseX >= right - 14 && mouseX <= right + 3
                    && mouseY >= bottom - 14 && mouseY <= bottom + 3;
            boolean rightEdge = mouseX >= right - 5 && mouseX <= right + 3
                    && mouseY >= y && mouseY <= bottom + 3;
            boolean bottomEdge = mouseY >= bottom - 5 && mouseY <= bottom + 3
                    && mouseX >= x && mouseX <= right + 3;
            if (corner || rightEdge || bottomEdge) {
                window.x = x;
                window.y = y;
                resizedWindow = i;
                resizeWidth = corner || rightEdge;
                resizeHeight = corner || bottomEdge;
                lastDragX = mouseX;
                lastDragY = mouseY;
                return true;
            }
            if (mouseX >= x && mouseX < x + window.width && mouseY >= y && mouseY < y + 16) {
                window.x = x;
                window.y = y;
                draggedWindow = i;
                lastDragX = mouseX;
                lastDragY = mouseY;
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the compact chat-row controls before ChatScreen processes the click. Some Minecraft
     * versions render injected widgets correctly but do not reliably dispatch clicks to them.
     */
    public boolean handleTabRowClick(double mouseX, double mouseY, int button, Screen parent) {
        if (button != 0) return false;
        Minecraft client = Minecraft.getInstance();
        for (int i = activeButtons.size() - 1; i >= 0; i--) {
            TabButton binding = activeButtons.get(i);
            Button widget = binding.button;
            if (!widget.visible || !widget.active || !widget.isMouseOver(mouseX, mouseY)) continue;
            if (binding.tabId != null) {
                selectTab(binding.windowIndex, binding.tabId);
                refreshButtonLabels(activeButtons);
                return true;
            }
            String action = widget.getMessage().getString();
            if ("+".equals(action)) {
                createTab(binding.windowIndex);
                CorrectChatClient.LOGGER.info("Created a new chat tab from the plus button");
                client.gui.setScreen(new ChatAreaSettingsScreen(parent, binding.windowIndex));
                return true;
            }
            if ("S".equalsIgnoreCase(action)) {
                List<CorrectChatConfig.ChatWindow> windows = windows(CorrectChatClient.config());
                String active = binding.windowIndex < 0 ? primaryTab(CorrectChatClient.config())
                        : binding.windowIndex < windows.size() ? windows.get(binding.windowIndex).activeTabId : null;
                client.gui.setScreen(new ChatHistoryScreen(parent, active));
                return true;
            }
        }
        return false;
    }

    /** Returns the complete message underneath the pointer in the primary or an additional chat window. */
    public Component messageAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        CorrectChatConfig config = CorrectChatClient.config();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.enabled || client.gui == null || client.gui.hud == null) return null;

        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        for (int index = windows.size() - 1; index >= 0; index--) {
            CorrectChatConfig.ChatWindow window = windows.get(index);
            if (!window.visible) continue;
            int x = ChatWindowRenderer.resolvedX(window, screenWidth);
            int y = ChatWindowRenderer.resolvedY(window, screenHeight);
            int renderedWidth = Math.min(window.width, screenWidth - x);
            int renderedHeight = Math.min(window.height, screenHeight - y);
            int contentTop = y + 36;
            int contentBottom = y + renderedHeight - 2;
            if (mouseX < x + 2 || mouseX >= x + renderedWidth - 2
                    || mouseY < contentTop || mouseY >= contentBottom) continue;
            CorrectChatConfig.ChatTab active = findTab(window.activeTabId);
            int lineY = contentBottom - client.font.lineHeight;
            List<ChatHistoryStore.Entry> entries = history.snapshot();
            outer:
            for (int entryIndex = entries.size() - 1; entryIndex >= 0; entryIndex--) {
                ChatHistoryStore.Entry entry = entries.get(entryIndex);
                if (!matches(active, entry.component, entry.kind)) continue;
                List<FormattedCharSequence> lines = wrappedLines(entry.component, Math.max(20, renderedWidth - 8));
                for (int line = lines.size() - 1; line >= 0; line--) {
                    if (lineY < contentTop) break outer;
                    if (mouseY >= lineY && mouseY < lineY + client.font.lineHeight) return entry.component;
                    lineY -= client.font.lineHeight;
                }
            }
        }

        double scale = primaryChatScale();
        int chatBottom = (int)Math.floor((screenHeight - 40) / scale);
        int localX = (int)Math.floor(mouseX / scale);
        int localY = (int)Math.floor(mouseY / scale);
        int lineHeight = (int)(client.font.lineHeight * (client.options.chatLineSpacing().get() + 1.0D));
        int rawWidth = primaryChatRawWidth();
        if (localX < -4 || localX > rawWidth + 4 || localY > chatBottom || lineHeight <= 0) return null;
        int visibleLine = (chatBottom - localY) / lineHeight;
        ChatComponent chat = client.gui.hud.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor)chat;
        int lineIndex = accessor.correctchat$getChatScrollbarPos() + visibleLine;
        List<GuiMessage.Line> lines = accessor.correctchat$getTrimmedMessages();
        if (visibleLine < 0 || visibleLine >= chat.getLinesPerPage() || lineIndex < 0 || lineIndex >= lines.size()) return null;
        return lines.get(lineIndex).parent().content();
    }

    public boolean dragWindow(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null) return false;
        if (resizingPrimary) {
            Minecraft client = Minecraft.getInstance();
            int dx = (int)Math.round(mouseX - lastDragX);
            int dy = (int)Math.round(mouseY - lastDragY);
            if (resizeWidth) {
                config.primaryChatWidth = Math.max(40,
                        Math.min(primaryChatWidth(screenWidth) + dx, Math.max(40, screenWidth - 8)));
            }
            if (resizeHeight) {
                config.primaryChatHeight = Math.max(20,
                        Math.min(primaryChatHeight() - dy, Math.max(20, screenHeight - 40)));
            }
            client.gui.hud.getChat().rescaleChat();
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        if (resizedWindow >= 0 && resizedWindow < windows.size()) {
            CorrectChatConfig.ChatWindow window = windows.get(resizedWindow);
            int dx = (int)Math.round(mouseX - lastDragX);
            int dy = (int)Math.round(mouseY - lastDragY);
            int maximumWidth = Math.min(600, Math.max(100, screenWidth - window.x));
            int maximumHeight = Math.min(400, Math.max(40, screenHeight - window.y));
            if (resizeWidth) window.width = Math.max(100, Math.min(window.width + dx, maximumWidth));
            if (resizeHeight) window.height = Math.max(40, Math.min(window.height + dy, maximumHeight));
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        if (draggedWindow < 0 || draggedWindow >= windows.size()) return false;
        CorrectChatConfig.ChatWindow window = windows.get(draggedWindow);
        int oldX = window.x;
        int oldY = window.y;
        int dx = (int)Math.round(mouseX - lastDragX);
        int dy = (int)Math.round(mouseY - lastDragY);
        window.x = Math.max(0, Math.min(oldX + dx, Math.max(0, screenWidth - window.width)));
        window.y = Math.max(0, Math.min(oldY + dy, Math.max(0, screenHeight - window.height)));
        int movedX = window.x - oldX;
        int movedY = window.y - oldY;
        for (TabButton binding : activeButtons) {
            if (binding.windowIndex == draggedWindow) {
                binding.button.setX(binding.button.getX() + movedX);
                binding.button.setY(binding.button.getY() + movedY);
            }
        }
        lastDragX = mouseX;
        lastDragY = mouseY;
        return true;
    }

    public boolean endWindowDrag() {
        if (!resizingPrimary && draggedWindow < 0 && resizedWindow < 0) return false;
        boolean primaryResized = resizingPrimary;
        boolean resized = resizedWindow >= 0;
        resizingPrimary = false;
        draggedWindow = -1;
        resizedWindow = -1;
        resizeWidth = false;
        resizeHeight = false;
        ConfigManager.saveLater(CorrectChatClient.config(), CorrectChatClient.LOGGER);
        if ((primaryResized || resized) && rebuildChatWidgets != null) rebuildChatWidgets.run();
        return true;
    }

    /** Width of the vanilla chat after applying Minecraft's chat scale. */
    public int primaryChatWidth(int screenWidth) {
        Minecraft client = Minecraft.getInstance();
        CorrectChatConfig config = CorrectChatClient.config();
        int width = config != null && config.primaryChatWidth > 0
                ? config.primaryChatWidth
                : (int)Math.ceil(ChatComponent.getWidth(client.options.chatWidth().get())
                        * primaryChatScale());
        return Math.max(40, Math.min(width, Math.max(40, screenWidth - 8)));
    }

    public int primaryChatHeight() {
        Minecraft client = Minecraft.getInstance();
        CorrectChatConfig config = CorrectChatClient.config();
        int height = config != null && config.primaryChatHeight > 0
                ? config.primaryChatHeight
                : (int)Math.ceil(ChatComponent.getHeight(client.options.chatHeightFocused().get())
                        * primaryChatScale());
        int screenHeight = client.getWindow().getGuiScaledHeight();
        return Math.max(20, Math.min(height, Math.max(20, screenHeight - 40)));
    }

    /** Raw dimensions consumed by ChatComponent before Minecraft applies the chat scale. */
    public int primaryChatRawWidth() {
        Minecraft client = Minecraft.getInstance();
        double scale = primaryChatScale();
        return Math.max(1, (int)Math.round(primaryChatWidth(client.getWindow().getGuiScaledWidth()) / scale));
    }

    public int primaryChatRawHeight() {
        Minecraft client = Minecraft.getInstance();
        double scale = primaryChatScale();
        return Math.max(1, (int)Math.round(primaryChatHeight() / scale));
    }

    public double primaryChatScale() {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config != null && config.primaryChatFontScalePercent > 0) {
            return config.primaryChatFontScalePercent / 100.0D;
        }
        return Math.max(0.01D, Minecraft.getInstance().options.chatScale().get());
    }

    /** Applies a size entered in Correct Chat's own editor to the vanilla-backed primary chat. */
    public void setPrimaryChatSize(int width, int height, int screenWidth, int screenHeight) {
        Minecraft client = Minecraft.getInstance();
        CorrectChatConfig config = CorrectChatClient.config();
        config.primaryChatWidth = Math.max(40, Math.min(width, Math.max(40, screenWidth - 8)));
        config.primaryChatHeight = Math.max(20, Math.min(height, Math.max(20, screenHeight - 40)));
        client.gui.hud.getChat().rescaleChat();
        ConfigManager.save(config, CorrectChatClient.LOGGER);
    }

    private int primaryChatTop(int screenHeight) {
        return screenHeight - 40 - primaryChatHeight();
    }

    private void addTabRow(Consumer<Button> add, List<TabButton> bindings, int windowIndex,
                           int startX, int y, int availableWidth) {
        Minecraft client = Minecraft.getInstance();
        List<CorrectChatConfig.ChatTab> tabs = visibleTabs(CorrectChatClient.config(), windowIndex);
        int x = startX;
        for (CorrectChatConfig.ChatTab tab : tabs) {
            CorrectChatConfig config = CorrectChatClient.config();
            int badgeAreaWidth = config.unreadBadges ? client.font.width(" 99+") + 4 : 0;
            int buttonWidth = Math.max(34, client.font.width(tab.name) + badgeAreaWidth + 12);
            final TabButton[] binding = new TabButton[1];
            Button button = Button.builder(Component.literal(tab.name), pressed -> {
                selectTab(windowIndex, tab.id);
                refreshButtonLabels(bindings);
            }).bounds(x, y, buttonWidth, 14).build();
            binding[0] = new TabButton(button, windowIndex, tab.id);
            bindings.add(binding[0]);
            add.accept(button);
            x += buttonWidth + 2;
        }
        Button settings = Button.builder(Localizer.literal("+").withStyle(ChatFormatting.GREEN), pressed -> {
            if (client.gui.screen() instanceof ChatScreen parent) {
                createTab(windowIndex);
                client.gui.setScreen(new ChatAreaSettingsScreen(parent, windowIndex));
            }
        }).bounds(x, y, 16, 14).build();
        bindings.add(new TabButton(settings, windowIndex, null));
        add.accept(settings);
        Button search = Button.builder(Localizer.literal("S").withStyle(ChatFormatting.AQUA), pressed -> {
            if (client.gui.screen() instanceof ChatScreen parent) {
                String active = windowIndex < 0 ? primaryTab(CorrectChatClient.config())
                        : windows(CorrectChatClient.config()).get(windowIndex).activeTabId;
                client.gui.setScreen(new ChatHistoryScreen(parent, active));
            }
        }).bounds(x + 18, y, 16, 14).build();
        bindings.add(new TabButton(search, windowIndex, null));
        add.accept(search);
    }

    private void selectTab(int windowIndex, String tabId) {
        CorrectChatConfig config = CorrectChatClient.config();
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        if (windowIndex < 0) config.setPrimaryTabFor(currentServerKey(), tabId);
        else if (windowIndex < windows.size()) windows.get(windowIndex).activeTabId = tabId;
        unreadByTab.remove(tabId);
        mentionedTabs.remove(tabId);
        ConfigManager.saveLater(config, CorrectChatClient.LOGGER);
        applyPrimaryFilter();
    }

    private void refreshButtonLabels(List<TabButton> bindings) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || bindings.isEmpty()) return;
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        for (TabButton binding : bindings) {
            if (binding.tabId == null) continue;
            String active;
            if (binding.windowIndex < 0) {
                active = primaryTab(config);
            } else if (binding.windowIndex < windows.size()) {
                active = windows.get(binding.windowIndex).activeTabId;
            } else {
                // Ignore a stale binding until ChatScreen rebuilds its widgets.
                continue;
            }
            CorrectChatConfig.ChatTab tab = findTab(binding.tabId);
            String name = tab == null ? "?" : tab.name;
            boolean mentioned = config.mentionHighlight && mentionedTabs.contains(binding.tabId);
            boolean selected = binding.tabId.equals(active);
            String badgeSpace = config.unreadBadges ? "    " : "";
            ChatFormatting color = selected ? ChatFormatting.GREEN
                    : mentioned ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
            binding.button.setMessage(Localizer.literal(name + badgeSpace).withStyle(color));
        }
    }

    public void renderTabBadges(GuiGraphicsExtractor graphics) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.enabled || !config.chatTabsEnabled || !config.unreadBadges) return;
        Minecraft client = Minecraft.getInstance();
        for (TabButton binding : activeButtons) {
            if (binding.tabId == null) continue;
            int unread = unreadByTab.getOrDefault(binding.tabId, 0);
            if (unread <= 0) continue;
            String count = unread > 99 ? "99+" : Integer.toString(unread);
            Button button = binding.button;
            int badgeWidth = Math.max(10, client.font.width(count) + 4);
            int left = button.getX() + button.getWidth() - badgeWidth - 2;
            int top = button.getY() + 2;
            int right = left + badgeWidth;
            graphics.fill(left + 1, top, right - 1, top + 1, 0xFFE44757);
            graphics.fill(left, top + 1, right, top + 9, 0xFFE44757);
            graphics.fill(left + 1, top + 9, right - 1, top + 10, 0xFFE44757);
            graphics.centeredText(client.font, Localizer.literal(count), left + badgeWidth / 2, top + 1, 0xFFFFFFFF);
        }
    }

    private void renderWindows(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        CorrectChatConfig config = CorrectChatClient.config();
        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.enabled || !config.chatTabsEnabled) return;
        boolean focused = client.gui.screen() instanceof ChatScreen;
        ChatWindowRenderer.render(graphics, client.font, windows(config), history.snapshot(), focused,
                this::primaryChatWidth, this::primaryChatTop, this::findTab, this::matches, this::wrappedLines);
    }

    private Kind kindOf(GuiMessage message) {
        Kind fallback = message.source() == GuiMessageSource.PLAYER ? Kind.PLAYER : Kind.SYSTEM;
        return history.kindOf(message.content(), fallback);
    }

    private boolean matches(CorrectChatConfig.ChatTab tab, Component component, Kind kind) {
        CorrectChatConfig config = CorrectChatClient.config();
        return !isBlockedAdvertisement(component)
                && (config == null || routing.matches(tab, component, kind, tabs(config)));
    }

    private boolean isBlockedAdvertisement(Component component) {
        return component != null && blockedAdvertisementResults.computeIfAbsent(
                component, value -> advertisementRules.blocks(value.getString()));
    }

    private void refreshAdvertisementRules() {
        CorrectChatConfig config = CorrectChatClient.config();
        advertisementRules = AdvertisementFilter.compile(config == null ? List.of() : config.blockedAdvertisements);
        blockedAdvertisementResults.clear();
    }

    private List<FormattedCharSequence> wrappedLines(Component component, int width) {
        Map<Integer, List<FormattedCharSequence>> widths = wrappedLines.computeIfAbsent(component, ignored -> new HashMap<>());
        List<FormattedCharSequence> cached = widths.get(width);
        if (cached != null) return cached;
        // Resizing can otherwise retain an entry for every intermediate pixel width.
        if (widths.size() >= 4) widths.clear();
        List<FormattedCharSequence> split = List.copyOf(Minecraft.getInstance().font.split(component, width));
        widths.put(width, split);
        return split;
    }

    private CorrectChatConfig.ChatTab findTab(String id) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null) return null;
        List<CorrectChatConfig.ChatTab> tabs = tabs(config);
        for (CorrectChatConfig.ChatTab tab : tabs) if (tab.id.equals(id)) return tab;
        return tabs.isEmpty() ? null : tabs.getFirst();
    }

    public String currentServerKey() {
        Minecraft client = Minecraft.getInstance();
        if (client.hasSingleplayerServer()) return "singleplayer";
        return client.getCurrentServer() == null ? "" : client.getCurrentServer().ip.trim();
    }

    public List<CorrectChatConfig.ChatTab> activeTabs() {
        return visibleTabs(CorrectChatClient.config());
    }

    public List<CorrectChatConfig.ChatWindow> activeWindows() {
        return windows(CorrectChatClient.config());
    }

    public String activePrimaryTabId() {
        return primaryTab(CorrectChatClient.config());
    }

    public void setActivePrimaryTabId(String id) {
        CorrectChatClient.config().setPrimaryTabFor(currentServerKey(), id);
    }

    public List<SearchResult> search(String query, String tabId, int maximum) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        CorrectChatConfig.ChatTab tab = tabId == null || tabId.isBlank() ? null : findTab(tabId);
        List<SearchResult> result = new ArrayList<>();
        List<ChatHistoryStore.Entry> entries = history.snapshot();
        for (int i = entries.size() - 1; i >= 0 && result.size() < maximum; i--) {
            ChatHistoryStore.Entry entry = entries.get(i);
            if (isBlockedAdvertisement(entry.component)) continue;
            if (tab != null && !matches(tab, entry.component, entry.kind)) continue;
            String searchable = entry.component.getString() + " " + entry.kind + " " + entry.server;
            if (!needle.isEmpty() && !searchable.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            result.add(new SearchResult(entry.component, entry.kind.name(), entry.receivedAt, entry.server));
        }
        return result;
    }

    private void createTab(int windowIndex) {
        CorrectChatConfig config = CorrectChatClient.config();
        List<CorrectChatConfig.ChatTab> tabs = tabs(config);
        CorrectChatConfig.ChatTab created = new CorrectChatConfig.ChatTab();
        created.name = "Neuer Tab " + (tabs.size() + 1);
        tabs.add(created);
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        if (windowIndex < 0) {
            List<String> assigned = config.primaryTabIdsFor(currentServerKey());
            if (!assigned.isEmpty()) assigned.add(created.id);
            config.setPrimaryTabFor(currentServerKey(), created.id);
        } else if (windowIndex < windows.size()) {
            CorrectChatConfig.ChatWindow window = windows.get(windowIndex);
            if (!window.tabIds.isEmpty()) window.tabIds.add(created.id);
            window.activeTabId = created.id;
        }
        ConfigManager.save(config, CorrectChatClient.LOGGER);
        applyPrimaryFilter();
    }

    private void updateUnread(Component component, Kind kind) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.chatTabsEnabled || isBlockedAdvertisement(component)) return;
        String primary = primaryTab(config);
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        String playerName = "";
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) playerName = client.player.getName().getString().toLowerCase(java.util.Locale.ROOT);
        boolean mention = !playerName.isBlank()
                && component.getString().toLowerCase(java.util.Locale.ROOT).contains(playerName);
        for (CorrectChatConfig.ChatTab tab : tabs(config)) {
            if (!matches(tab, component, kind)) continue;
            boolean visible = tab.id.equals(primary);
            if (!visible) {
                for (CorrectChatConfig.ChatWindow window : windows) {
                    if (window.visible && tab.id.equals(window.activeTabId)) { visible = true; break; }
                }
            }
            if (!visible) unreadByTab.merge(tab.id, 1, Integer::sum);
            if (mention && !visible) mentionedTabs.add(tab.id);
        }
        refreshButtonLabels(activeButtons);
    }

    private List<CorrectChatConfig.ChatTab> tabs(CorrectChatConfig config) {
        return config.tabsFor(currentServerKey());
    }

    private List<CorrectChatConfig.ChatTab> visibleTabs(CorrectChatConfig config) {
        return visibleTabs(config, Integer.MIN_VALUE);
    }

    private List<CorrectChatConfig.ChatTab> visibleTabs(CorrectChatConfig config, int windowIndex) {
        List<String> assigned;
        if (windowIndex == Integer.MIN_VALUE) assigned = List.of();
        else if (windowIndex < 0) assigned = config.primaryTabIdsFor(currentServerKey());
        else {
            List<CorrectChatConfig.ChatWindow> windows = windows(config);
            assigned = windowIndex < windows.size() ? windows.get(windowIndex).tabIds : List.of();
        }
        List<CorrectChatConfig.ChatTab> visible = new ArrayList<>();
        List<CorrectChatConfig.ChatTab> all = tabs(config);
        if (assigned.isEmpty()) {
            for (CorrectChatConfig.ChatTab tab : all) {
                if (config.twitchEnabled || !tab.isTwitchOnly()) visible.add(tab);
            }
            return visible;
        }
        for (String id : assigned) {
            for (CorrectChatConfig.ChatTab tab : all) {
                if (tab.id.equals(id) && (config.twitchEnabled || !tab.isTwitchOnly())) {
                    visible.add(tab);
                    break;
                }
            }
        }
        return visible;
    }

    private List<CorrectChatConfig.ChatWindow> windows(CorrectChatConfig config) {
        return config.windowsFor(currentServerKey());
    }

    private String primaryTab(CorrectChatConfig config) {
        return config.primaryTabFor(currentServerKey());
    }

    private void ensureEnabledTabSelection() {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || config.twitchEnabled) return;
        List<CorrectChatConfig.ChatTab> visible = visibleTabs(config, -1);
        if (visible.isEmpty()) return;
        String fallback = visible.getFirst().id;
        CorrectChatConfig.ChatTab primary = findTab(config.primaryTabFor(currentServerKey()));
        if (primary == null || visible.stream().noneMatch(tab -> tab.id.equals(primary.id))) {
            config.setPrimaryTabFor(currentServerKey(), fallback);
        }
        List<CorrectChatConfig.ChatWindow> windows = windows(config);
        for (int index = 0; index < windows.size(); index++) {
            CorrectChatConfig.ChatWindow window = windows.get(index);
            List<CorrectChatConfig.ChatTab> windowTabs = visibleTabs(config, index);
            if (windowTabs.isEmpty()) continue;
            CorrectChatConfig.ChatTab active = findTab(window.activeTabId);
            if (active == null || windowTabs.stream().noneMatch(tab -> tab.id.equals(active.id))) {
                window.activeTabId = windowTabs.getFirst().id;
            }
        }
        ConfigManager.save(config, CorrectChatClient.LOGGER);
    }

    private void trim() {
        CorrectChatConfig config = CorrectChatClient.config();
        int maximum = config == null ? 1000 : config.maxStoredMessages;
        history.trim(maximum).forEach(component -> {
            componentMods.remove(component);
            sourceTexts.remove(component);
            sourceTextsByRenderedText.remove(component.getString());
            wrappedLines.remove(component);
            blockedAdvertisementResults.remove(component);
            routing.forget(component);
        });
    }
}
