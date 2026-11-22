package dev.qumara.net;

import dev.qumara.QumaraMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** S2C: temblor de camara y (opcional) giro suave de la mirada hacia la entidad indicada (cinematica). */
public record ShakePayload(int durationTicks, float intensity, int lookAtEntityId, int lookTicks) implements CustomPayload {
    public static final Id<ShakePayload> ID = new Id<>(QumaraMod.id("shake"));
    public static final PacketCodec<RegistryByteBuf, ShakePayload> CODEC = new PacketCodec<>() {
        @Override public ShakePayload decode(RegistryByteBuf b) { return new ShakePayload(b.readVarInt(), b.readFloat(), b.readInt(), b.readVarInt()); }
        @Override public void encode(RegistryByteBuf b, ShakePayload p) { b.writeVarInt(p.durationTicks).writeFloat(p.intensity).writeInt(p.lookAtEntityId).writeVarInt(p.lookTicks); }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
