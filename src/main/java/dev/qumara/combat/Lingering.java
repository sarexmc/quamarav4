package dev.qumara.combat;

import dev.qumara.entity.QumaraEntity;

/** Efecto que sigue activo aunque el jefe haya terminado el ataque (espinas, ondas expansivas...). */
public interface Lingering {
    /** @return true cuando debe eliminarse */
    boolean tick(QumaraEntity boss);
}
