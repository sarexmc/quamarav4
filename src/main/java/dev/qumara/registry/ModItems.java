package dev.qumara.registry;

import dev.qumara.QumaraMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item QUMARA_SPAWN_EGG = Registry.register(Registries.ITEM, QumaraMod.id("qumara_spawn_egg"),
            new SpawnEggItem(ModEntities.QUMARA, 0x4d7a24, 0xd9459f, new Item.Settings()));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(QUMARA_SPAWN_EGG));
    }

    private ModItems() {}
}
