package de.dho.correctchat.mixin;

import de.dho.correctchat.CorrectChatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentLegacyMixin {
    @Inject(method = "addMessage", at = @At("TAIL"))
    private void correctchat$refreshSelectedTab(CallbackInfo callback) {
        if (CorrectChatClient.config() != null
                && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled
                && CorrectChatClient.workspace() != null) {
            CorrectChatClient.workspace().refreshPrimaryFilterNow();
        }
    }

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void correctchat$useCustomWidth(CallbackInfoReturnable<Integer> callback) {
        if (hasCustomWidth() && CorrectChatClient.workspace() != null) {
            callback.setReturnValue(CorrectChatClient.workspace().primaryChatRawWidth());
        }
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void correctchat$useCustomFocusedHeight(CallbackInfoReturnable<Integer> callback) {
        if (hasCustomHeight() && CorrectChatClient.workspace() != null
                && Minecraft.getInstance().screen instanceof ChatScreen) {
            callback.setReturnValue(CorrectChatClient.workspace().primaryChatRawHeight());
        }
    }

    @Inject(method = "getScale()D", at = @At("HEAD"), cancellable = true)
    private void correctchat$useCustomFontScale(CallbackInfoReturnable<Double> callback) {
        if (hasCustomFontScale() && CorrectChatClient.workspace() != null) {
            callback.setReturnValue(CorrectChatClient.workspace().primaryChatScale());
        }
    }

    private static boolean hasCustomWidth() {
        return CorrectChatClient.config() != null && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled && CorrectChatClient.config().primaryChatWidth > 0;
    }

    private static boolean hasCustomHeight() {
        return CorrectChatClient.config() != null && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled && CorrectChatClient.config().primaryChatHeight > 0;
    }

    private static boolean hasCustomFontScale() {
        return CorrectChatClient.config() != null && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled && CorrectChatClient.config().primaryChatFontScalePercent > 0;
    }
}
