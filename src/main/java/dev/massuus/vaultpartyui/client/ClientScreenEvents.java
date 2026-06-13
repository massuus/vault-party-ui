package dev.massuus.vaultpartyui.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import dev.massuus.vaultpartyui.VaultPartyUiMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VaultPartyUiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientScreenEvents {
    private static final String VOICECHAT_GROUP_SCREEN = "de.maxhenkel.voicechat.gui.group.GroupScreen";

    private ClientScreenEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();
        if (screen == null || !VOICECHAT_GROUP_SCREEN.equals(screen.getClass().getName())) {
            return;
        }

        int x = readInt(screen, "guiLeft", screen.width / 2 - 118) + 74;
        int y = readInt(screen, "guiTop", 32) + readInt(screen, "ySize", 0) - 27;
        if (y <= 0) {
            y = screen.height - 52;
        }

        Button button = new Button(
                x,
                y,
                88,
                20,
                new TranslatableComponent("screen.vaultpartyui.voicechat_party_button"),
                ignored -> ClientTickEvents.triggerVoiceGroupPartyAction(),
                (pressed, poseStack, mouseX, mouseY) -> screen.renderTooltip(
                        poseStack,
                        new TranslatableComponent("screen.vaultpartyui.voicechat_party_tooltip"),
                        mouseX,
                        mouseY
                )
        );
        button.active = VoiceChatIntegration.hasLocalVoiceGroup();
        event.addListener(button);
    }

    private static int readInt(Screen screen, String fieldName, int fallback) {
        Objects.requireNonNull(screen, "screen");
        Class<?> type = screen.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(screen);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                break;
            }
        }

        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method method = screen.getClass().getMethod(getterName);
            Object value = method.invoke(screen);
            if (value instanceof Integer integer) {
                return integer;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return fallback;
    }
}
