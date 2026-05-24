package dev.massuus.vaultpartyui.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.massuus.vaultpartyui.VaultPartyUiMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final KeyMapping OPEN_PARTY_UI = new KeyMapping(
            "key.vaultpartyui.open_party_ui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.vaultpartyui"
    );

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(OPEN_PARTY_UI);
    }
}
