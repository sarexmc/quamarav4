package dev.qumara.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * Cambios temporales del terreno (musgo del Jardin Maldito y obstaculos vegetales) con restauracion garantizada:
 * guarda el bloque original, solo restaura si el bloque sigue siendo el que pusimos y persiste en NBT por si el
 * servidor se cierra durante el combate.
 */
public class ArenaTransformer {
    private record Change(BlockPos pos, BlockState original, BlockState applied) {}
    private record Pending(BlockPos pos, BlockState state, long when) {}

    private final Map<BlockPos, Change> changes = new LinkedHashMap<>();
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private final List<Pending> restoreQueue = new ArrayList<>();
    private final Random rng = new Random();

    /** Programa la conversion progresiva (de dentro hacia fuera) de tierra/hierba a musgo y su posterior restauracion. */
    public void mossDisc(ServerWorld w, BlockPos center, int radius, long startTime, long durationTicks) {
        List<Pending> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius || rng.nextFloat() > 0.8f) continue;
            int x = center.getX() + dx, z = center.getZ() + dz;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (Math.abs(y - center.getY()) > 6) continue;
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = w.getBlockState(p);
            if (!s.isIn(BlockTags.DIRT) || s.isOf(Blocks.MOSS_BLOCK)) continue;
            list.add(new Pending(p, rng.nextFloat() < 0.15f ? Blocks.ROOTED_DIRT.getDefaultState() : Blocks.MOSS_BLOCK.getDefaultState(),
                    startTime + (long) (d * 1.4)));
        }
        list.sort(Comparator.comparingLong(Pending::when));
        queue.addAll(list);
        // marca la restauracion tras 'durationTicks' (se resuelve en tick())
        restoreAt = Math.max(restoreAt, startTime + durationTicks);
    }
    private long restoreAt = Long.MIN_VALUE;

    /** Coloca un bloque temporal (obstaculos). 'when' = tiempo de mundo en el que aparece. */
    public void place(BlockPos pos, BlockState state, long when) {
        queue.add(new Pending(pos, state, when));
    }

    /** Altura util para apoyar un obstaculo (primer bloque de aire sobre el suelo). */
    public static BlockPos groundAbove(ServerWorld w, int x, int z, int refY) {
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - refY) > 6) y = refY;
        return new BlockPos(x, y, z);
    }

    public void tick(ServerWorld w) {
        long now = w.getTime();
        int budget = 40;
        while (budget-- > 0 && !queue.isEmpty() && queue.peekFirst().when() <= now) {
            Pending p = queue.pollFirst();
            BlockState cur = w.getBlockState(p.pos());
            if (changes.containsKey(p.pos())) continue;
            boolean obstacle = !p.state().isOf(Blocks.MOSS_BLOCK) && !p.state().isOf(Blocks.ROOTED_DIRT);
            if (obstacle && !cur.isAir() && !cur.isReplaceable()) continue;
            changes.put(p.pos(), new Change(p.pos(), cur, p.state()));
            w.setBlockState(p.pos(), p.state(), Block.NOTIFY_LISTENERS);
        }
        if (queue.isEmpty() && !changes.isEmpty() && now >= restoreAt) restoreSome(w, 60);
    }

    /** Programa la restauracion de los bloques temporales (obstaculos) a partir de 'time'. */
    public void scheduleRestore(long time) { restoreAt = Math.max(restoreAt, time); }

    private void restoreSome(ServerWorld w, int n) {
        Iterator<Change> it = changes.values().iterator();
        while (n-- > 0 && it.hasNext()) {
            Change c = it.next(); it.remove();
            if (w.isChunkLoaded(c.pos()) && w.getBlockState(c.pos()).isOf(c.applied().getBlock()))
                w.setBlockState(c.pos(), c.original(), Block.NOTIFY_LISTENERS);
        }
    }

    public void restoreAll(ServerWorld w) {
        queue.clear();
        for (Change c : changes.values())
            if (w.isChunkLoaded(c.pos()) && w.getBlockState(c.pos()).isOf(c.applied().getBlock()))
                w.setBlockState(c.pos(), c.original(), Block.NOTIFY_LISTENERS);
        changes.clear();
    }

    public boolean isEmpty() { return changes.isEmpty() && queue.isEmpty(); }

    public void write(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Change c : changes.values()) {
            NbtCompound t = new NbtCompound();
            t.putLong("p", c.pos().asLong());
            t.put("o", NbtHelper.fromBlockState(c.original()));
            t.put("a", NbtHelper.fromBlockState(c.applied()));
            list.add(t);
        }
        nbt.put("ArenaChanges", list);
    }

    public void read(NbtCompound nbt) {
        changes.clear();
        NbtList list = nbt.getList("ArenaChanges", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound t = list.getCompound(i);
            BlockPos p = BlockPos.fromLong(t.getLong("p"));
            BlockState o = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), t.getCompound("o"));
            BlockState a = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), t.getCompound("a"));
            changes.put(p, new Change(p, o, a));
        }
        restoreAt = Long.MIN_VALUE;
    }
}
