package dev.qumara.entity;

import dev.qumara.ai.QumaraBrain;
import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZoneManager;
import dev.qumara.combat.Lingering;
import dev.qumara.combat.Shockwave;
import dev.qumara.combat.attack.AttackType;
import dev.qumara.combat.attack.BossAttack;
import dev.qumara.net.QumaraNetworking;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import dev.qumara.world.ArenaTransformer;
import dev.qumara.world.PlantManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import dev.qumara.model.QumaraRig;

import java.util.*;

/**
 * QUMARA. Todo el combate se decide en el servidor: maquina de estados (SPAWN, IDLE, ATTACK, STUN, FURY, DEAD), IA,
 * ataques, zonas de peligro, BossBar. El cliente solo recibe el estado/animaciones (DataTracker + GeckoLib) y las
 * zonas de peligro (paquetes).
 */
public class QumaraEntity extends HostileEntity {
    public static final float MAX_HEALTH = 3000f;
    /**
     * ESCALADO PARA MULTITUD: el dano recibido se divide por participantes^0.75 (jugadores en la arena). Con 1 jugador ~6 min;
     * con 100 jugadores (solo ~30 pueden golpear a la vez) ~6-7 min. Ajusta SCALING_EXPONENT: 1.0 = lineal (mas dificil con
     * mucha gente), 0.5 = suave (mas facil). MAX_HEALTH es la vida con 1 jugador.
     */
    public static final double SCALING_EXPONENT = 0.75;
    public static final double HIT_CAP_FRACTION = 0.02;          // un solo golpe nunca quita mas del 2 % de la vida maxima
    public static final int INTRO_TICKS = 200, FURY_TICKS = 60, STUN_TICKS = 80;

    private static final TrackedData<Integer> STATE = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> ANIM = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> TONGUE_ANIM = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> FURY = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> RAGE = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> ANIM_SEQ = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.INTEGER);   // +1 en cada cambio de animacion (reinicia la animacion)
    private static final TrackedData<Integer> HURT_SEQ = DataTracker.registerData(QumaraEntity.class, TrackedDataHandlerRegistry.INTEGER);   // +1 en cada golpe recibido (animacion aditiva)

    private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("entity.qumara.qumara"), BossBar.Color.PINK, BossBar.Style.PROGRESS);
    private final Random rng = new Random();
    private final List<Lingering> lingering = new ArrayList<>();

    private DangerZoneManager zones;
    private PlantManager plants;
    private final ArenaTransformer arena = new ArenaTransformer();
    private QumaraBrain brain;

    private boolean spawned, barVisible, arenaLoaded, raging;
    private int ticksInState, cooldown, aimTicks, nextIdleSound;
    private float clock, poise;
    private int participants = 1, lastHurtSound = -100, lastHurtAnim = -100;
    private double divisor = 1.0;
    private BossAttack current, pending, forcedNext;
    private AttackType currentType, pendingType;

    public QumaraEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0);
    }

    // ------------------------------------------------------------------------------------ datos sincronizados
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STATE, QumaraState.SPAWN.ordinal());
        builder.add(ANIM, "idle");
        builder.add(TONGUE_ANIM, "lengua_idle");
        builder.add(FURY, false);
        builder.add(RAGE, false);
        builder.add(ANIM_SEQ, 0);
        builder.add(HURT_SEQ, 0);
    }

    public QumaraState state() { return QumaraState.of(dataTracker.get(STATE)); }
    public boolean fury() { return dataTracker.get(FURY); }
    public String mainAnim() { return dataTracker.get(ANIM); }
    public String tongueAnim() { return dataTracker.get(TONGUE_ANIM); }
    public boolean rage() { return dataTracker.get(RAGE); }
    public int animSeq() { return dataTracker.get(ANIM_SEQ); }
    public int hurtSeq() { return dataTracker.get(HURT_SEQ); }
    /** 0 = normal, 1 = furia (<50 % vida), 2 = ira (<25 %). */
    public int phase() { return rage() ? 2 : (fury() ? 1 : 0); }
    public double speed() { return rage() ? 1.45 : (fury() ? 1.25 : 1.0); }
    public int ticks(float clockUnits) { return Math.max(1, Math.round(clockUnits / (float) speed())); }

    private String idleAnim() { return rage() ? "idle_rage" : (fury() ? "idle_fury" : "idle"); }
    private String tongueIdle() { return rage() ? "lengua_idle_rage" : (fury() ? "lengua_idle_fury" : "lengua_idle"); }

    private void setState(QumaraState s, String anim, String tongue) {
        dataTracker.set(STATE, s.ordinal());
        dataTracker.set(ANIM, "idle".equals(anim) ? idleAnim() : anim);
        dataTracker.set(TONGUE_ANIM, tongue == null ? tongueIdle() : tongue);
        dataTracker.set(ANIM_SEQ, dataTracker.get(ANIM_SEQ) + 1);
        ticksInState = 0;
    }

    // ------------------------------------------------------------------------------------ accesores para los ataques
    public ServerWorld sw() { return (ServerWorld) getWorld(); }
    public DangerZoneManager zones() { return zones; }
    public PlantManager plants() { return plants; }
    public ArenaTransformer arena() { return arena; }
    public Random rng() { return rng; }
    public void addLingering(Lingering l) { lingering.add(l); }

    public Vec3d forward() { float y = getYaw() * MathHelper.RADIANS_PER_DEGREE; return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y)); }
    public Vec3d right() { Vec3d f = forward(); return new Vec3d(-f.z, 0, f.x); }

    public double groundY(double x, double z) {
        int top = sw().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
        return Math.abs(top - getY()) > 6 * Scale.K ? getY() : top;
    }

    public List<ServerPlayerEntity> players() {
        return sw().getPlayers(p -> !p.isSpectator() && p.isAlive() && Math.hypot(p.getX() - getX(), p.getZ() - getZ()) <= 60 && Math.abs(p.getY() - getY()) <= 30);
    }

    /**
     * Dano de los ataques. 'amount' esta pensado para un jugador de 20 de vida (10 corazones); se escala si tiene mas corazones
     * y x1.2 / x1.45 en furia / ira. IGNORA LA ARMADURA (fuente tipo magia indirecta atribuida a Qumara): la armadura de
     * netherita ya no la vuelve inofensiva; solo reducen el dano Resistencia, encantamientos de proteccion, absorcion y totems.
     */
    public void hurtPlayer(ServerPlayerEntity p, float amount) {
        float mul = (rage() ? 1.45f : (fury() ? 1.2f : 1.0f)) * Math.max(1f, p.getMaxHealth() / 20f);
        p.damage(getDamageSources().indirectMagic(this, this), amount * mul);
    }

    public void sound(SoundEvent e, float volume, float pitch) {
        sw().playSound(null, getX(), getY() + 3, getZ(), e, SoundCategory.HOSTILE, volume, pitch);
    }

    public void shake(int ticks, float intensity) { QumaraNetworking.shake(sw(), getPos(), 56, ticks, intensity, -1, 0); }

    // ------------------------------------------------------------------------------------ bucle principal
    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (getWorld().isClient || !(getWorld() instanceof ServerWorld sw)) return;
        if (isDead()) return;
        init(sw);
        if (age % 20 == 0) {                                        // participantes -> escalado de vida y densidad de particulas
            participants = Math.max(1, players().size());
            divisor = Math.pow(participants, SCALING_EXPONENT);
            Vfx.density = MathHelper.clamp(1.0 - (participants - 8) / 90.0, 0.3, 1.0);
        }
        zones.setAnchor(getPos()); zones.tick(); plants.tick(); arena.tick(sw);
        for (Iterator<Lingering> it = lingering.iterator(); it.hasNext(); ) if (it.next().tick(this)) it.remove();
        if (!spawned) { spawned = true; Optional.ofNullable(sw.getClosestPlayer(this, 40)).ifPresent(p -> faceTowards(p, 360f)); setState(QumaraState.SPAWN, "spawn", "lengua_idle"); }
        updateBar(sw);
        switch (state()) {
            case SPAWN -> introTick(sw);
            case IDLE -> idleTick();
            case ATTACK -> attackTick();
            case STUN -> { if (++ticksInState >= STUN_TICKS) { cooldown = 14; poise = 0; setState(QumaraState.IDLE, "idle", null); } }
            case FURY -> furyTick(sw);
            default -> {}
        }
        ambient(sw);
        poise = Math.max(0, poise - 0.04f);
    }

    private void init(ServerWorld sw) {
        if (zones != null) return;
        zones = new DangerZoneManager(sw);
        plants = new PlantManager(sw);
        brain = new QumaraBrain(this);
        if (!arenaLoaded) arenaLoaded = true;
        nextIdleSound = 40;
        if (spawned && state() == QumaraState.SPAWN) setState(QumaraState.IDLE, "idle", null);   // recargado desde disco
        if (!arena.isEmpty()) arena.scheduleRestore(0);                                           // restos de un combate interrumpido
    }

    // ------------------------------------------------------------------------------------ CINEMATICA DE APARICION
    private void introTick(ServerWorld sw) {
        int t = ticksInState++;
        Vec3d c = getPos();
        if (t == 0) { sound(ModSounds.SPAWN_RUMBLE, 6f, 0.8f); QumaraNetworking.shake(sw, c, 64, 70, 0.12f, getId(), 120); }
        if (t < 70 && t % 5 == 0) {                              // grietas radiales + polvo
            for (int i = 0; i < 3; i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                for (double d = 1.5; d < 3.5 + t * 0.06; d += 0.5)
                    sw.spawnParticles(Vfx.DIRT, c.x + Math.cos(a) * d, c.y + 0.1, c.z + Math.sin(a) * d, 2, 0.08, 0.03, 0.08, 0.03);
            }
            sw.spawnParticles(Vfx.BROWN_DUST, c.x, c.y + 0.4, c.z, 6, 3.0, 0.2, 3.0, 0);
        }
        if (t % 8 == 0 && t < 160) {                             // la vegetacion cercana reacciona
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.5, c.z, 14, 8.0, 1.5, 8.0, 0);
            sw.spawnParticles(Vfx.LEAVES, c.x, c.y + 2.5, c.z, 8, 7.0, 2.0, 7.0, 0.02);
        }
        if (t == 70) { sound(ModSounds.EARTH_CRACK, 5f, 0.6f); QumaraNetworking.shake(sw, c, 64, 60, 0.35f, getId(), 70); }
        if (t >= 70 && t < 160 && t % 4 == 0) { Vfx.burst(sw, Vfx.DIRT, c.add(0, 0.3, 0), 18, 2.6, 0.12); Vfx.burst(sw, Vfx.STONE, c.add(0, 0.3, 0), 8, 2.0, 0.2); }
        if (t == 110) { sound(ModSounds.SPAWN_RUMBLE, 6f, 0.55f); sound(ModSounds.ROOTS_GROW, 4f, 0.6f); QumaraNetworking.shake(sw, c, 64, 50, 0.6f, getId(), 60); }
        if (t == 150) sound(ModSounds.ARM_MOVE, 4f, 0.5f);
        if (t == 160) {                                          // golpe de presentacion
            sound(ModSounds.IMPACT, 6f, 0.7f); sound(ModSounds.EARTH_CRACK, 5f, 0.7f);
            QumaraNetworking.shake(sw, c, 64, 25, 1.0f, -1, 0);
            Vfx.slamImpact(sw, c.add(forward().multiply(4.8 * Scale.K)));
            for (ServerPlayerEntity p : sw.getPlayers()) {
                double d = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
                if (d < 10 && !p.isSpectator()) { Vec3d away = new Vec3d(p.getX() - c.x, 0, p.getZ() - c.z).normalize(); p.setVelocity(away.x * 0.9, 0.5, away.z * 0.9); p.velocityModified = true; }
            }
            addLingering(new Shockwave(c, 3.0, 12.0 * Scale.K, 14, 0f));
        }
        if (t == 172) {                                          // rugido y titulo
            sound(ModSounds.FURY, 6f, 0.6f);
            QumaraNetworking.shake(sw, c, 64, 35, 0.8f, -1, 0);
            for (ServerPlayerEntity p : sw.getPlayers()) {
                if (p.squaredDistanceTo(this) > 64 * 64) continue;
                p.networkHandler.sendPacket(new TitleFadeS2CPacket(8, 50, 16));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("qumara.subtitle")));
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("qumara.title")));
            }
        }
        if (t == 176) barVisible = true;                         // aparece la BossBar
        if (t >= INTRO_TICKS) { cooldown = 30; setState(QumaraState.IDLE, "idle", null); }
    }

    // ------------------------------------------------------------------------------------ IDLE / ATAQUES
    private void idleTick() {
        ticksInState++;
        brain.tick();
        ServerPlayerEntity target = brain.target();
        if (fury() && !rage() && getHealth() <= getMaxHealth() * 0.25f) { startRage(); return; }
        if (!fury() && getHealth() <= getMaxHealth() * 0.5f) { startFury(); return; }
        if (poise >= getMaxHealth() * 0.08f) { poise = 0; cancelPending(); setState(QumaraState.STUN, "stun", "lengua_idle"); sound(ModSounds.HURT, 4f, 0.5f); return; }
        if (target != null) faceTowards(target, pending != null ? 9f : 3.5f);
        if (cooldown > 0) { cooldown--; return; }
        if (forcedNext == null && pending == null) {
            AttackType t = brain.pick();
            if (t == null) return;
            pendingType = t; pending = t.create(); aimTicks = 0;
        }
        BossAttack a = forcedNext != null ? forcedNext : pending;
        if (target == null) return;
        aimTicks++;
        double want = Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90.0;
        double diff = Math.abs(MathHelper.wrapDegrees(want - getYaw()));
        if (diff < 6 || aimTicks > 24) beginAttack(a);
    }

    private void cancelPending() { pending = null; pendingType = null; forcedNext = null; }

    private void beginAttack(BossAttack a) {
        boolean chained = a == forcedNext;
        current = a; currentType = chained ? null : pendingType;
        pending = null; pendingType = null; forcedNext = null;
        clock = 0;
        a.start(this, brain.target());
        if (currentType != null) brain.used(currentType);
        setState(QumaraState.ATTACK, a.animation(), a.tongueAnimation());
    }

    private void attackTick() {
        ticksInState++;
        float prev = clock;
        clock += (float) speed();
        current.tick(this, prev, clock);
        if (clock >= current.total()) {
            BossAttack done = current;
            done.end(this);
            BossAttack next = done.next(this);
            current = null;
            cooldown = next != null ? 4 : (rage() ? 10 : (fury() ? 15 : 30));
            forcedNext = next;
            setState(QumaraState.IDLE, "idle", null);
        }
    }

    // ------------------------------------------------------------------------------------ FASE DE FURIA
    private void startFury() {
        cancelPending();
        setState(QumaraState.FURY, "fury", "lengua_rugir");
        Vec3d c = getPos();
        zones.show(new DangerShape.Circle(c.x, c.z, 9f * Scale.KF), groundY(c.x, c.z), 31, 12, dev.qumara.combat.DangerZone.RING);
        sound(ModSounds.FURY, 7f, 0.5f);
        QumaraNetworking.shake(sw(), c, 64, 40, 0.7f, getId(), 30);
    }

    private void furyTick(ServerWorld sw) {
        if (raging) { rageTick(sw); return; }
        int t = ticksInState++;
        Vec3d c = getPos();
        if (t % 3 == 0) Vfx.furyAura(sw, c.add(0, Scale.HEAD_Y, 0));
        if (t == 30) dataTracker.set(FURY, true);
        if (t == 31) {
            sound(ModSounds.IMPACT, 6f, 0.6f);
            Vfx.slamImpact(sw, c.add(forward().multiply(4.8 * Scale.K)));
            Vfx.ring(sw, Vfx.RED_DUST, c.add(0, 0.2, 0), 4.5, 40, 0.2, 0);
            addLingering(new Shockwave(c, 3.0, 9.0 * Scale.K, 12, 8f));
            QumaraNetworking.shake(sw, c, 64, 25, 0.9f, -1, 0);
        }
        if (t >= FURY_TICKS) { cooldown = 20; setState(QumaraState.IDLE, "idle", null); }
    }

    // ------------------------------------------------------------------------------------ FASE DE IRA (<25 %)
    private void startRage() {
        cancelPending();
        raging = true;
        setState(QumaraState.FURY, "rage", "lengua_rugir");
        Vec3d c = getPos();
        zones.show(new DangerShape.Circle(c.x, c.z, 12f * (float) Scale.K), groundY(c.x, c.z), 24, 8, dev.qumara.combat.DangerZone.RING);
        sound(ModSounds.FURY, 8f, 0.4f);
        QumaraNetworking.shake(sw(), c, 72, 60, 0.9f, getId(), 40);
    }

    private void rageTick(ServerWorld sw) {
        int t = ticksInState++;
        Vec3d c = getPos();
        if (t % 3 == 0) Vfx.furyAura(sw, c.add(0, Scale.HEAD_Y, 0));
        if (t % 3 == 0) Vfx.burst(sw, Vfx.DIRT, c.add(0, 0.3, 0), 10, 4.0, 0.2);
        if (t == 24) dataTracker.set(RAGE, true);
        if (t == 24 || t == 40 || t == 56) {
            int k = t == 24 ? 0 : (t == 40 ? 1 : 2);
            if (k < 2) zones.show(new DangerShape.Circle(c.x, c.z, (12f + 3f * (k + 1)) * (float) Scale.K), groundY(c.x, c.z), 16, 8, dev.qumara.combat.DangerZone.RING);
            sound(ModSounds.IMPACT, 8f, 0.5f + 0.1f * k);
            sound(ModSounds.EARTH_CRACK, 6f, 0.6f);
            Vfx.slamImpact(sw, c.add(forward().multiply(4.8 * Scale.K)));
            Vfx.slamImpact(sw, c.add(right().multiply(5.0 * Scale.K)));
            Vfx.slamImpact(sw, c.add(right().multiply(-5.0 * Scale.K)));
            Vfx.ring(sw, Vfx.RED_DUST, c.add(0, 0.2, 0), 4.5, 48, 0.2, 0);
            addLingering(new Shockwave(c, 3.0, (12.0 + 3.0 * k) * Scale.K, 14, 8f));
            QumaraNetworking.shake(sw, c, 72, 28, 1.0f, -1, 0);
        }
        if (t >= 80) { raging = false; cooldown = 14; setState(QumaraState.IDLE, "idle", null); }
    }

    // ------------------------------------------------------------------------------------ ambiente / barra
    private void ambient(ServerWorld sw) {
        Vec3d c = getPos();
        if (age % 6 == 0) sw.spawnParticles(net.minecraft.particle.ParticleTypes.FALLING_SPORE_BLOSSOM, c.x, c.y + 4 * Scale.K + rng.nextDouble() * 4 * Scale.K, c.z, 1, 3.3 * Scale.K, 1.3 * Scale.K, 3.3 * Scale.K, 0);
        if (state() == QumaraState.IDLE && age % 10 == 0) sw.spawnParticles(Vfx.LEAVES, c.x, c.y + 6 * Scale.K, c.z, 1, 2.0 * Scale.K, 1.3 * Scale.K, 2.0 * Scale.K, 0.01);
        if (fury() && state() != QumaraState.DEAD && age % 3 == 0) Vfx.furyAura(sw, c.add(0, Scale.HEAD_Y, 0));
        if (state() == QumaraState.IDLE || state() == QumaraState.STUN) {
            if (--nextIdleSound <= 0) { sound(ModSounds.IDLE, 3.5f, 0.9f + rng.nextFloat() * 0.2f); nextIdleSound = 70 + rng.nextInt(60); }
        }
    }

    private void updateBar(ServerWorld sw) {
        bossBar.setPercent(MathHelper.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        bossBar.setColor(fury() ? BossBar.Color.RED : BossBar.Color.PINK);
        if (!barVisible) return;
        for (ServerPlayerEntity p : sw.getPlayers()) {
            boolean near = p.squaredDistanceTo(this) <= 80 * 80 && !p.isSpectator();
            if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
        }
    }

    public void faceTowards(Entity t, float maxStep) {
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        float want = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90f;
        float y = MathHelper.stepUnwrappedAngleTowards(getYaw(), want, maxStep);
        setYaw(y); setBodyYaw(y); setHeadYaw(y); prevYaw = y; prevBodyYaw = y; prevHeadYaw = y;
    }

    // ------------------------------------------------------------------------------------ dano / muerte
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        QumaraState s = state();
        if (s == QumaraState.SPAWN || s == QumaraState.FURY || s == QumaraState.DEAD) return false;
        amount = (float) (amount / divisor);                                    // escalado por numero de jugadores
        amount = Math.min(amount, (float) (getMaxHealth() * HIT_CAP_FRACTION));  // nadie la mata de un golpe
        this.timeUntilRegen = 0;                                                // sin frames de invulnerabilidad: cada golpe de cada jugador cuenta
        boolean r = super.damage(source, amount);
        if (r) {
            poise += amount;
            if (brain != null && source.getAttacker() instanceof ServerPlayerEntity p) brain.registerDamage(p, amount);
            if ((s == QumaraState.IDLE || s == QumaraState.STUN) && age - lastHurtAnim >= 12) { lastHurtAnim = age; dataTracker.set(HURT_SEQ, dataTracker.get(HURT_SEQ) + 1); }
        }
        return r;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (getWorld().isClient) return;
        cleanup();
        barVisible = false;
        bossBar.clearPlayers();
        setState(QumaraState.DEAD, "death", "lengua_morir");
        sound(ModSounds.DEATH, 7f, 0.7f);
        shake(60, 0.5f);
    }

    @Override
    protected void updatePostDeath() {
        deathTime++;
        if (!(getWorld() instanceof ServerWorld sw)) return;
        Vec3d c = getPos();
        if (deathTime % 3 == 0) { Vfx.burst(sw, Vfx.LEAVES, c.add(0, 4, 0), 12, 2.5, 0.06); Vfx.burst(sw, Vfx.GREEN_DUST, c.add(0, 5, 0), 8, 2.0, 0.02); }
        if (deathTime == 60) { sound(ModSounds.IMPACT, 5f, 0.5f); shake(30, 0.7f); }
        if (deathTime == 40) sound(ModSounds.EARTH_CRACK, 4f, 0.6f);
        if (deathTime >= 110) {
            ExperienceOrbEntity.spawn(sw, c, 12000);
            sw.sendEntityStatus(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    private void cleanup() {
        if (zones != null) zones.clear();
        if (plants != null) plants.clearAll();
        if (getWorld() instanceof ServerWorld sw) arena.restoreAll(sw);
        lingering.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) {
            bossBar.clearPlayers();
            if (reason.shouldDestroy()) cleanup();
            else if (zones != null) zones.clear();      // descarga de chunk: los bloques temporales quedan guardados en el NBT
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------------------------ comportamiento de "planta anclada"
    @Override public void travel(Vec3d movementInput) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean isCollidable() { return true; }
    @Override public void takeKnockback(double strength, double x, double z) {}
    @Override public void pushAwayFrom(Entity entity) {}
    @Override public boolean canHaveStatusEffect(StatusEffectInstance effect) { return false; }
    @Override public boolean isDisallowedInPeaceful() { return false; }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override public void playAmbientSound() {}
    @Override public boolean isFireImmune() { return true; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(11 * Scale.K, 3 * Scale.K, 11 * Scale.K).stretch(0, 6 * Scale.K, 0); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.HURT; }
    @Override protected void playHurtSound(DamageSource source) { if (age - lastHurtSound >= 8) { lastHurtSound = age; super.playHurtSound(source); } }
    @Override protected SoundEvent getDeathSound() { return null; }

    // ------------------------------------------------------------------------------------ NBT
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Spawned", spawned);
        nbt.putBoolean("Fury", fury());
        nbt.putBoolean("Rage", rage());
        arena.write(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        spawned = nbt.getBoolean("Spawned");
        dataTracker.set(FURY, nbt.getBoolean("Fury"));
        dataTracker.set(RAGE, nbt.getBoolean("Rage"));
        arena.read(nbt);
        barVisible = spawned;
        if (spawned) setState(QumaraState.IDLE, "idle", null);
    }

    // ------------------------------------------------------------------------------------ posicion de la mano (agarres)
    /**
     * Posicion en el mundo de la palma del brazo en el instante 'clock' (ticks de animacion) de la animacion 'anim'. Sale de la
     * trayectoria exportada por tools/build_model.py, asi que coincide con lo que ve el jugador. null si la animacion no existe.
     */
    public Vec3d handPos(String anim, float clock) {
        float[] h = QumaraRig.get().handAt(anim, clock / 20f);
        if (h == null) return null;
        double k = Scale.RENDER / 16.0;
        return getPos().add(forward().multiply(h[2] * k)).add(right().multiply(-h[0] * k)).add(0, h[1] * k, 0);
    }
}
