package de.dho.correctchat.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.gui.CorrectChatConfigScreen;

public final class CorrectChatModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new CorrectChatConfigScreen(parent, CorrectChatClient.config().copy());
    }
}
