package dev.qumara.client.hud;

import net.minecraft.client.gui.hud.ClientBossBar;

import java.util.Map;
import java.util.UUID;

/** Interfaz implementada por BossBarHudMixin para leer las barras de jefe sincronizadas. */
public interface BossBarHudAccess {
    Map<UUID, ClientBossBar> qumara$bars();
}
