package dev.qumara.net;

import dev.qumara.QumaraMod;
import dev.qumara.combat.DangerShape;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** S2C: crea/actualiza una zona de peligro. 'ageTicks' = ticks transcurridos desde el inicio del aviso en el servidor. */
public record DangerZonePayload(int id, DangerShape shape, double y, int ageTicks, int duration, int linger, int style) implements CustomPayload {
    public static final Id<DangerZonePayload> ID = new Id<>(QumaraMod.id("danger_zone"));
    public static final PacketCodec<RegistryByteBuf, DangerZonePayload> CODEC = new PacketCodec<>() {
        @Override
        public DangerZonePayload decode(RegistryByteBuf buf) {
            int id = buf.readVarInt();
            double y = buf.readDouble();
            int age = buf.readVarInt(), dur = buf.readVarInt(), linger = buf.readVarInt(), style = buf.readVarInt();
            return new DangerZonePayload(id, DangerShape.read(buf), y, age, dur, linger, style);
        }
        @Override
        public void encode(RegistryByteBuf buf, DangerZonePayload p) {
            buf.writeVarInt(p.id).writeDouble(p.y).writeVarInt(p.ageTicks).writeVarInt(p.duration).writeVarInt(p.linger).writeVarInt(p.style);
            p.shape.write(buf);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
