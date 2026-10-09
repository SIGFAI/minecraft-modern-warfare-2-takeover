package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** Small effect helpers: far-visible particles, per-tick tasks, display entities. */
public final class Fx {
	private Fx() {}

	static final class Task {
		final IntConsumer fn; int i; final int len;
		Task(int len, IntConsumer fn) { this.len = len; this.fn = fn; }
	}
	static final List<Task> tasks = new ArrayList<>();
	static final List<Task> incoming = new ArrayList<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				synchronized (incoming) { tasks.addAll(incoming); incoming.clear(); }
				for (int k = tasks.size() - 1; k >= 0; k--) {
					Task t = tasks.get(k);
					t.fn.accept(t.i++);
					if (t.i >= t.len) tasks.remove(k);
				}
			} catch (Throwable e) {
				Sigf.log("MW2 task error: " + e);
				tasks.clear();
			}
		});
	}

	/** Runs fn(0..ticks-1), once per game tick. */
	public static void run(int ticks, IntConsumer fn) { synchronized (incoming) { incoming.add(new Task(ticks, fn)); } }

	/** Particles every player sees, even far away (up to 200 blocks). */
	public static void far(ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
		for (ServerPlayer pl : Sigf.players())
			Sigf.level().sendParticles(pl, p, true, true, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	public static void far(ParticleOptions p, Vec3 pos, int count, double sx, double sy, double sz, double speed) {
		for (ServerPlayer pl : Sigf.players())
			Sigf.level().sendParticles(pl, p, true, true, pos.x, pos.y, pos.z, count, sx, sy, sz, speed);
	}

	public static String f(double d) { return String.format(Locale.ROOT, "%.3f", d); }

	/** Summons a block display with a size and an offset from its position; returns nothing, tagged for lookup. */
	public static void display(String block, Vec3 at, double[] scale, double[] off, String tag, int teleportTicks) {
		Sigf.command("summon minecraft:block_display " + f(at.x) + " " + f(at.y) + " " + f(at.z)
			+ " {block_state:{id:\"" + block + "\"},teleport_duration:" + teleportTicks + ",Tags:[\"" + tag + "\"],"
			+ "brightness:{sky:15,block:15},view_range:8.0f,"
			+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:["
			+ f(scale[0]) + "f," + f(scale[1]) + "f," + f(scale[2]) + "f],translation:["
			+ f(off[0]) + "f," + f(off[1]) + "f," + f(off[2]) + "f]}}");
	}

	public static List<Entity> tagged(String tag) {
		List<Entity> out = new ArrayList<>();
		for (Entity e : Sigf.level().getAllEntities()) if (e.entityTags().contains(tag)) out.add(e);
		return out;
	}

	/** A block display that appears after delayTicks, stays lifeTicks, then is removed. */
	public static void stage(String block, Vec3 at, double[] scale, double[] off, String tag, int delayTicks, int lifeTicks) {
		run(delayTicks + lifeTicks + 1, i -> {
			if (i == delayTicks) display(block, at, scale, off, tag, 0);
			if (i == delayTicks + lifeTicks) Sigf.command("kill @e[tag=" + tag + "]");
		});
	}
}
