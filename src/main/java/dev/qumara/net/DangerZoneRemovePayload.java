package dev.qumara.net;

import dev.qumara.QumaraMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** S2C: elimina una zona de peligro (id &lt; 0 = eliminar todas). */
public record DangerZoneRemovePayload(int id) implements CustomPayload {
    public static final Id<DangerZoneRemovePayload> ID = new Id<>(QumaraMod.id("danger_zone_remove"));
    public static final PacketCodec<RegistryByteBuf, DangerZoneRemovePayload> CODEC = new PacketCodec<>() {
        @Override public DangerZoneRemovePayload decode(RegistryByteBuf buf) { return new DangerZoneRemovePayload(buf.readInt()); }
        @Override public void encode(RegistryByteBuf buf, DangerZoneRemovePayload p) { buf.writeInt(p.id); }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
