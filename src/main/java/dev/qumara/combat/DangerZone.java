package dev.qumara.combat;

import net.minecraft.util.math.Vec3d;

/**
 * Zona de peligro. 'start' es el tiempo de mundo (ticks) en el que empieza la animacion de aviso, 'duration' los ticks
 * hasta el impacto y 'linger' los ticks que permanece despues del impacto antes de desaparecer.
 */
public record DangerZone(int id, DangerShape shape, double y, long start, int duration, int linger, int style) {
    public static final int SLAM = 0, SWEEP = 1, TONGUE = 2, ROOTS = 3, GARDEN = 4, VINE = 5, RING = 6, THORN = 7, OBSTACLE = 8, GRAB = 9;

    public boolean contains(Vec3d p) {
        return Math.abs(p.y - y) <= 3.5 && shape.contains(p.x, p.z);
    }

    public long impactTime() { return start + duration; }
    public long endTime() { return start + duration + linger; }
}
