package dev.qumara.client.render;

import dev.qumara.QumaraMod;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.model.QumaraMesh;
import dev.qumara.model.QumaraPose;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renderer propio de Qumara: malla organica (qumara.mesh) deformada por huesos (skinning lineal en CPU) con las animaciones de
 * qumara.qani. Sustituye a GeckoLib (que solo admite cubos). El tamano sale de Scale.RENDER.
 */
public class QumaraRenderer extends EntityRenderer<QumaraEntity> {
    private static final Identifier NORMAL = QumaraMod.id("textures/entity/qumara.png");
    private static final Identifier FURY = QumaraMod.id("textures/entity/qumara_fury.png");

    private final QumaraMesh mesh = QumaraMesh.get();
    private final float[] skinPos = new float[mesh.nv * 3], skinNrm = new float[mesh.nv * 3];
    private final Map<QumaraEntity, QumaraPose> poses = new WeakHashMap<>();

    public QumaraRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 4.0f;
    }

    @Override
    public Identifier getTexture(QumaraEntity e) { return e.fury() ? FURY : NORMAL; }

    @Override
    public void render(QumaraEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
        QumaraPose pose = poses.computeIfAbsent(e, k -> new QumaraPose());
        String main = e.mainAnim(), tongue = e.tongueAnim();
        boolean attack = !(main.startsWith("idle") || main.equals("spawn") || main.equals("death") || main.equals("stun") || main.equals("fury") || main.equals("rage"));
        float speed = attack ? (float) e.speed() : 1f;
        float tSpeed = (tongue.equals("lengua_atacar") || tongue.equals("lengua_abanico") || tongue.equals("lengua_devorar") || tongue.equals("lengua_veneno")) ? (float) e.speed() : 1f;
        float now = (e.age + tickDelta) / 20f;
        pose.update(main, e.animSeq(), speed, tongue, e.animSeq(), tSpeed, e.hurtSeq(), now);
        mesh.skin(pose.mats, skinPos, skinNrm);

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevBodyYaw, e.bodyYaw);
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));        // el frente del modelo es +Z, igual que forward() del jefe
        float s = Scale.RENDER / 16f;
        m.scale(s, s, s);
        MatrixStack.Entry entry = m.peek();
        Matrix4f pm = entry.getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(e)));
        int overlay = LivingEntityRenderer.getOverlay(e, 0.0f);
        int[] q = mesh.quads; float[] uv = mesh.uv;
        for (int i = 0; i < mesh.nq; i++) {
            for (int k = 0; k < 4; k++) {
                int v = q[i * 4 + k];
                vc.vertex(pm, skinPos[v * 3], skinPos[v * 3 + 1], skinPos[v * 3 + 2])
                        .color(255, 255, 255, 255).texture(uv[v * 2], uv[v * 2 + 1]).overlay(overlay).light(light)
                        .normal(entry, skinNrm[v * 3], skinNrm[v * 3 + 1], skinNrm[v * 3 + 2]);
            }
        }
        m.pop();
    }
}
