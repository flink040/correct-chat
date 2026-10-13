package de.dho.correctchat.mixin;

import de.dho.correctchat.CorrectChatClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** Allows Correct Chat's primary window to exceed Minecraft's fixed 320x180 chat limit. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    /**
     * Minecraft 26.2 normally discards messages rejected by visibleMessageFilter before they
     * reach allMessages. Correct Chat needs those messages later when the user selects another
     * tab, so only bypass that early rejection while tab filtering is active.
     */
    @Redirect(
            method = "addMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"
            )
    )
    private boolean correctchat$retainMessagesForOtherTabs(Predicate<GuiMessage> filter, Object message) {
        if (CorrectChatClient.config() != null
                && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled) {
            return true;
        }
        return filter.test((GuiMessage)message);
    }

    /** Reapply the selected tab before another frame can expose the newly retained message. */
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
                && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatScreen) {
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
        return CorrectChatClient.config() != null
                && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled
                && CorrectChatClient.config().primaryChatWidth > 0;
    }

    private static boolean hasCustomHeight() {
        return CorrectChatClient.config() != null
                && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled
                && CorrectChatClient.config().primaryChatHeight > 0;
    }

    private static boolean hasCustomFontScale() {
        return CorrectChatClient.config() != null
                && CorrectChatClient.config().enabled
                && CorrectChatClient.config().chatTabsEnabled
                && CorrectChatClient.config().primaryChatFontScalePercent > 0;
    }
}
