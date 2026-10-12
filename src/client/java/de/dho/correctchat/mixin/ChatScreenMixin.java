package de.dho.correctchat.mixin;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.core.LegacyText;
import de.dho.correctchat.gui.MessageActionsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected EditBox input;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void correctchat$addTabButtons(CallbackInfo callback) {
        if (CorrectChatClient.workspace() != null) {
            input.setWidth(CorrectChatClient.workspace().primaryChatWidth(width));
            CorrectChatClient.workspace().addTabButtons(this::addRenderableWidget, this::rebuildWidgets, width, height);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void correctchat$renderMessagePreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                                  CallbackInfo callback) {
        if (CorrectChatClient.workspace() != null) CorrectChatClient.workspace().renderTabBadges(graphics);
        if (CorrectChatClient.config() == null
                || !CorrectChatClient.config().enabled
                || !CorrectChatClient.config().chatMessagePreview) return;

        String message = input.getValue();
        if (message.isEmpty() || message.startsWith("/") || !LegacyText.hasFormattingCode(message)) return;

        int x = input.getX();
        int y = input.getY() - 13;
        int right = x + input.getWidth();
        graphics.fill(x, y, right, input.getY() - 1, 0xB0000000);
        graphics.enableScissor(x + 2, y, right - 2, input.getY() - 1);
        graphics.text(font, LegacyText.parse(message), x + 3, y + 2, 0xFFFFFFFF, false);
        graphics.disableScissor();
    }

    @Inject(
            method = "keyPressed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"
            ),
            cancellable = true
    )
    private void correctchat$keepArrowKeysInChatHistory(KeyEvent event,
                                                         CallbackInfoReturnable<Boolean> callback) {
        if (event.key() == GLFW_KEY_UP) {
            ((ChatScreen)(Object)this).moveInHistory(-1);
            callback.setReturnValue(true);
        } else if (event.key() == GLFW_KEY_DOWN) {
            ((ChatScreen)(Object)this).moveInHistory(1);
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void correctchat$beginWindowDrag(MouseButtonEvent event, boolean doubleClick,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (CorrectChatClient.workspace() != null
                && CorrectChatClient.workspace().handleTabRowClick(event.x(), event.y(), event.button(), this)) {
            callback.setReturnValue(true);
            return;
        }
        if (CorrectChatClient.workspace() != null && event.button() == 1) {
            Component message = CorrectChatClient.workspace().messageAt(event.x(), event.y(), width, height);
            if (message != null) {
                minecraft.gui.setScreen(new MessageActionsScreen(this, message));
                callback.setReturnValue(true);
                return;
            }
        }
        if (CorrectChatClient.workspace() != null
                && CorrectChatClient.workspace().beginWindowDrag(event.x(), event.y(), event.button(), width, height)) {
            callback.setReturnValue(true);
        }
    }

}
