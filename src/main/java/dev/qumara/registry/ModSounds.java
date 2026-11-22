package dev.qumara.registry;

import dev.qumara.QumaraMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Eventos de sonido propios. En sounds.json apuntan a sonidos vanilla (type=event) y se pueden sustituir por .ogg propios. */
public final class ModSounds {
    public static final SoundEvent IDLE = reg("idle");
    public static final SoundEvent SPAWN_RUMBLE = reg("spawn_rumble");
    public static final SoundEvent EARTH_CRACK = reg("earth_crack");
    public static final SoundEvent ROOTS_GROW = reg("roots_grow");
    public static final SoundEvent ARM_MOVE = reg("arm_move");
    public static final SoundEvent TONGUE_LASH = reg("tongue_lash");
    public static final SoundEvent IMPACT = reg("impact");
    public static final SoundEvent WARNING = reg("warning");
    public static final SoundEvent FURY = reg("fury");
    public static final SoundEvent HURT = reg("hurt");
    public static final SoundEvent DEATH = reg("death");
    public static final SoundEvent GARDEN_BLOOM = reg("garden_bloom");
    public static final SoundEvent VINE_WHIP = reg("vine_whip");

    private static SoundEvent reg(String name) {
        Identifier id = QumaraMod.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {}

    private ModSounds() {}
}
