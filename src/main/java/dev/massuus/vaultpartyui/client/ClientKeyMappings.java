package dev.massuus.vaultpartyui.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientKeyMappings {
    public static final KeyMapping OPEN_PARTY_UI = new KeyMapping(
            "key.vaultpartyui.open_party_ui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.vaultpartyui"
    );

    public static final KeyMapping CREATE_AND_INVITE_ALL = new KeyMapping(
        "key.vaultpartyui.create_invite_all",
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(),
        "key.categories.vaultpartyui"
    );

    public static final KeyMapping CREATE_AND_INVITE_FAVORITES = new KeyMapping(
        "key.vaultpartyui.create_invite_favorites",
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(),
        "key.categories.vaultpartyui"
    );

    public static final KeyMapping INVITE_NEARBY = new KeyMapping(
        "key.vaultpartyui.invite_nearby_party",
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(),
        "key.categories.vaultpartyui"
    );

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(OPEN_PARTY_UI);
        ClientRegistry.registerKeyBinding(CREATE_AND_INVITE_ALL);
        ClientRegistry.registerKeyBinding(CREATE_AND_INVITE_FAVORITES);
        ClientRegistry.registerKeyBinding(INVITE_NEARBY);
    }
}
