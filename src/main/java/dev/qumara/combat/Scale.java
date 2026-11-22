package dev.qumara.combat;

/**
 * UNICO punto donde se define el tamaño de Qumara. RENDER = escala del modelo (1 unidad de modelo = RENDER/16 bloques).
 * Con 1.3 mide ~24 bloques de alto (era ~16 con el modelo anterior). Cambia solo RENDER para agrandar o
 * reducir: alcances, zonas rojas, hitbox y alturas de particulas se recalculan a partir de el.
 */
public final class Scale {
    /** TAMANO. Con 1.3 la planta mide ~24 bloques de alto (modelo de 300 px; 16 px = 1 bloque a RENDER 1). El brazo alcanza ~17 bloques. */
    public static final float RENDER = 1.3f;
    public static final double K = RENDER / 0.5;           // factor respecto al tamaño base (0.5) con el que se calibraron los ataques
    public static final float KF = (float) K;
    public static final double MOUTH_Y = 163.0 * RENDER / 16.0;   // altura de la boca (px de modelo -> bloques)
    public static final double HEAD_Y = 175.0 * RENDER / 16.0;    // altura del centro de la cabeza (auras, camara)
    public static final double STALK_Y = 295.0 * RENDER / 16.0;   // punta de las flores (origen de la enredadera)
    public static final float HITBOX_W = (float) (130.0 * RENDER / 16.0);
    public static final float HITBOX_H = (float) (300.0 * RENDER / 16.0);
    private Scale() {}
}
