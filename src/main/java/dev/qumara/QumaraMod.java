package dev.qumara;

import dev.qumara.combat.RootedManager;
import dev.qumara.net.QumaraNetworking;
import dev.qumara.registry.ModEntities;
import dev.qumara.registry.ModItems;
import dev.qumara.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class QumaraMod implements ModInitializer {
    public static final String MOD_ID = "qumara";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModEntities.init();
        ModItems.init();
        QumaraNetworking.registerPayloads();
        RootedManager.init();
        dev.qumara.combat.VenomManager.init();
    }
}
