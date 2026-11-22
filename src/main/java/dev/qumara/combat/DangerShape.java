package dev.qumara.combat;

import net.minecraft.network.PacketByteBuf;

/**
 * Forma geometrica de una zona de peligro. LA MISMA clase se usa en el servidor para aplicar el dano
 * y en el cliente para dibujar el indicador: asi el indicador coincide exactamente con la zona real.
 */
public sealed interface DangerShape {
    byte CIRCLE = 0, ARC = 1, STRIP = 2;

    boolean contains(double x, double z);

    byte type();

    void write(PacketByteBuf buf);

    static DangerShape read(PacketByteBuf buf) {
        byte t = buf.readByte();
        return switch (t) {
            case CIRCLE -> new Circle(buf.readDouble(), buf.readDouble(), buf.readFloat());
            case ARC -> new Arc(buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
            default -> new Strip(buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        };
    }

    /** Disco centrado en (x,z). */
    record Circle(double x, double z, float radius) implements DangerShape {
        public boolean contains(double px, double pz) {
            double dx = px - x, dz = pz - z;
            return dx * dx + dz * dz <= (double) radius * radius;
        }
        public byte type() { return CIRCLE; }
        public void write(PacketByteBuf b) { b.writeByte(CIRCLE).writeDouble(x).writeDouble(z).writeFloat(radius); }
    }

    /** Sector anular alrededor de (x,z): radios [rIn,rOut], desde 'start' (rad, atan2(dz,dx)) recorriendo 'sweep' (rad, con signo). */
    record Arc(double x, double z, float rIn, float rOut, float start, float sweep) implements DangerShape {
        public boolean contains(double px, double pz) {
            double dx = px - x, dz = pz - z;
            double d2 = dx * dx + dz * dz;
            if (d2 < (double) rIn * rIn || d2 > (double) rOut * rOut) return false;
            double delta = wrap(Math.atan2(dz, dx) - start);          // [-pi, pi)
            if (sweep >= 0) { if (delta < 0) delta += Math.PI * 2; return delta <= sweep; }
            if (delta > 0) delta -= Math.PI * 2;
            return delta >= sweep;
        }
        public byte type() { return ARC; }
        public void write(PacketByteBuf b) { b.writeByte(ARC).writeDouble(x).writeDouble(z).writeFloat(rIn).writeFloat(rOut).writeFloat(start).writeFloat(sweep); }
    }

    /** Rectangulo desde (x,z) en direccion unitaria (dx,dz), largo 'length' y semiancho 'halfWidth'. */
    record Strip(double x, double z, float dx, float dz, float length, float halfWidth) implements DangerShape {
        public boolean contains(double px, double pz) {
            double rx = px - x, rz = pz - z;
            double along = rx * dx + rz * dz;
            double side = -rx * dz + rz * dx;
            return along >= 0 && along <= length && Math.abs(side) <= halfWidth;
        }
        public byte type() { return STRIP; }
        public void write(PacketByteBuf b) { b.writeByte(STRIP).writeDouble(x).writeDouble(z).writeFloat(dx).writeFloat(dz).writeFloat(length).writeFloat(halfWidth); }
    }

    static double wrap(double a) {
        while (a >= Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }
}
