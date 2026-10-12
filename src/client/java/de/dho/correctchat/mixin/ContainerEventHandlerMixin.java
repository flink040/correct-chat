package de.dho.correctchat.mixin;

import de.dho.correctchat.CorrectChatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void correctchat$dragWindow(MouseButtonEvent event, double dragX, double dragY,
                                        CallbackInfoReturnable<Boolean> callback) {
        if (!((Object)this instanceof ChatScreen) || CorrectChatClient.workspace() == null) return;
        var window = Minecraft.getInstance().getWindow();
        if (CorrectChatClient.workspace().dragWindow(
                event.x(), event.y(), window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void correctchat$endWindowDrag(MouseButtonEvent event, CallbackInfoReturnable<Boolean> callback) {
        if ((Object)this instanceof ChatScreen && CorrectChatClient.workspace() != null
                && CorrectChatClient.workspace().endWindowDrag()) {
            callback.setReturnValue(true);
        }
    }
}
