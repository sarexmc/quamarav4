package dev.qumara.registry;

import dev.qumara.QumaraMod;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<QumaraEntity> QUMARA = Registry.register(
            Registries.ENTITY_TYPE, QumaraMod.id("qumara"),
            EntityType.Builder.create(QumaraEntity::new, SpawnGroup.MONSTER)
                    .dimensions(Scale.HITBOX_W, Scale.HITBOX_H)
                    .maxTrackingRange(24)
                    .trackingTickInterval(1)
                    .makeFireImmune()
                    .build("qumara"));

    public static void init() {
        FabricDefaultAttributeRegistry.register(QUMARA, QumaraEntity.createAttributes());
    }

    private ModEntities() {}
}
