package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** Killstreak rewards: UAV, care package, Harrier strike, tactical nuke. */
public final class Streaks {
	private Streaks() {}

	static int counter;
	static final DustParticleOptions RED_SMOKE = new DustParticleOptions(0xFF2A1A, 2.2f);
	static final DustParticleOptions YELLOW_SMOKE = new DustParticleOptions(0xFFC400, 2.0f);
	public static boolean nukeActive;

	static Vec3 groundAt(Vec3 p) {
		BlockPos top = Sigf.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p));
		return Vec3.atBottomCenterOf(top);
	}

	// ---- UAV --------------------------------------------------------------
	public static void uav(ServerPlayer p) {
		Sigf.title("UAV ONLINE", "Enemies are marked on your screen", 3);
		Sigf.sound(SigfMod.HITMARKER, p.position(), 1f, 0.6f);
		for (Entity e : Sigf.near(p.position(), 90, x -> SigfMod.isFoe(x) && x instanceof LivingEntity)) {
			((LivingEntity) e).addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 25, 0, false, false));
		}
		Fx.run(30, i -> {
			double r = 2 + i * 3.2;
			for (int k = 0; k < 40; k++) {
				double a = k / 40.0 * Math.PI * 2;
				Fx.far(ParticleTypes.END_ROD, p.position().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0);
			}
		});
	}

	// ---- Care package -----------------------------------------------------
	public static void carePackage(Vec3 spot, boolean byGrenade) {
		ServerLevel level = Sigf.level();
		final Vec3 ground = groundAt(spot);
		final String tag = "mw2cp" + (counter++);
		final double top = 48;
		Sigf.title(byGrenade ? "CARE PACKAGE" : "CARE PACKAGE INBOUND", "Mark the smoke", 2.5);
		Sigf.sound(SigfMod.SMOKE, ground, 1.5f, 1f);
		Sigf.sound(SigfMod.V_CARE, ground, 2.0f, 1f);
		final List<Entity> parts = new ArrayList<>();
		final double[] y = { ground.y + top };
		Fx.run(60 + (int) (top / 0.5) + 80, i -> {
			// colored smoke marker the whole time
			Fx.far(RED_SMOKE, ground.add(0, 0.5 + (i % 12) * 0.35, 0), 4, 0.35, 0.02);
			if (i % 3 == 0) Fx.far(YELLOW_SMOKE, ground.add(0, 0.3 + (i % 20) * 0.3, 0), 3, 0.3, 0.02);
			if (i % 6 == 0) Fx.far(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ground.add(0, 0.5, 0), 1, 0.2, 0.05);
			if (i == 40) {
				Vec3 at = new Vec3(ground.x, y[0], ground.z);
				Fx.display("minecraft:chest", at, new double[] { 1, 1, 1 }, new double[] { -0.5, 0, -0.5 }, tag, 2);
				Fx.display("minecraft:orange_concrete", at, new double[] { 4.4, 0.25, 4.4 }, new double[] { -2.2, 3.4, -2.2 }, tag, 2);
				Fx.display("minecraft:white_concrete", at, new double[] { 3.0, 0.25, 3.0 }, new double[] { -1.5, 3.65, -1.5 }, tag, 2);
				Fx.display("minecraft:orange_concrete", at, new double[] { 1.5, 0.25, 1.5 }, new double[] { -0.75, 3.9, -0.75 }, tag, 2);
				for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
					Fx.display("minecraft:black_concrete", at, new double[] { 0.05, 2.5, 0.05 }, new double[] { sx * 0.42, 1.0, sz * 0.42 }, tag, 2);
			}
			if (i == 42) { parts.addAll(Fx.tagged(tag)); if (Sigf.isDemo()) SigfMod.hold(120); }
			if (Sigf.isDemo() && i > 44 && i % 2 == 0 && !parts.isEmpty() && y[0] > ground.y) Sigf.lookAt(Sigf.host(), new Vec3(ground.x, Math.max(y[0] + 2, ground.y + 1), ground.z));
			if (i > 42 && !parts.isEmpty() && y[0] > ground.y + 0.01) {
				y[0] = Math.max(ground.y, y[0] - 0.5);
				for (Entity e : parts) e.teleportTo(ground.x, y[0], ground.z);
				if (y[0] <= ground.y) {
					for (Entity e : parts) e.discard();
					BlockPos bp = BlockPos.containing(ground);
					level.setBlockAndUpdate(bp, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
					Sigf.command("item replace block " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " container.0 with sigf:rifle");
					Sigf.command("item replace block " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " container.1 with minecraft:golden_apple 4");
					Sigf.command("item replace block " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " container.2 with sigf:smoke_grenade 3");
					Sigf.command("item replace block " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " container.3 with minecraft:cooked_beef 16");
					Sigf.command("item replace block " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " container.4 with minecraft:iron_chestplate 1");
					Sigf.sound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, ground, 1f, 1.4f);
					Fx.far(ParticleTypes.POOF, ground.add(0, 0.5, 0), 25, 0.8, 0.05);
					Fx.far(ParticleTypes.CLOUD, ground.add(0, 0.2, 0), 20, 1.2, 0.08);
				}
			}
		});
	}

	// ---- Harrier strike -----------------------------------------------------
	/** Block-built jet: parts as {block, length, height, width, x(fwd), y, z(lat)}. */
	static final Object[][] JET = {
		{ "minecraft:light_gray_concrete", 7.0, 0.9, 1.0, 0.0, 0.0, 0.0 },
		{ "minecraft:white_concrete", 2.2, 0.6, 0.7, 4.3, -0.05, 0.0 },
		{ "minecraft:light_blue_stained_glass", 1.8, 0.5, 0.6, 1.6, 0.65, 0.0 },
		{ "minecraft:gray_concrete", 2.4, 0.15, 9.0, -0.6, -0.1, 0.0 },
		{ "minecraft:black_concrete", 1.3, 1.5, 0.15, -3.0, 0.95, 0.0 },
		{ "minecraft:gray_concrete", 1.1, 0.12, 3.4, -3.0, 0.05, 0.0 },
		{ "minecraft:black_concrete", 1.9, 0.7, 0.35, 0.9, -0.3, 0.7 },
		{ "minecraft:black_concrete", 1.9, 0.7, 0.35, 0.9, -0.3, -0.7 },
		{ "minecraft:orange_concrete", 0.6, 0.5, 0.5, -3.7, 0.0, 0.0 },
	};

	public static void harrier(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		final double ax, az;   // facing, snapped to a compass direction
		if (Math.abs(look.x) > Math.abs(look.z)) { ax = Math.signum(look.x); az = 0; } else { ax = 0; az = Math.signum(look.z); }
		// the jet crosses in front of the player, left to right, along (fx, fz)
		final double fx = -az, fz = ax;
		final double lx = -fz, lz = fx;
		final Vec3 base = p.position().add(ax * 34, 0, az * 34);
		final double height = p.getY() + 17;
		final String tag = "mw2jet" + (counter++);
		Sigf.title("HARRIER STRIKE", "Danger close!", 3);
		Sigf.sound(SigfMod.V_HARRIER, p.position(), 2.0f, 1f);
		final Vec3 start = new Vec3(base.x - fx * 70, height, base.z - fz * 70);
		final List<Entity> parts = new ArrayList<>();
		final double speed = 1.15;
		final double[] bombAt = new double[9];
		final double[] bombDepth = new double[9];
		var rnd = Sigf.level().getRandom();
		for (int b = 0; b < bombAt.length; b++) { bombAt[b] = -38 + b * 9.5; bombDepth[b] = (rnd.nextDouble() - 0.5) * 14; }
		final boolean[] dropped = new boolean[bombAt.length];
		final double J = 2.3;   // the jet is drawn bigger than a real one so it reads from far away
		Fx.run(140, i -> {
			if (i == 0) {
				if (Sigf.isDemo()) SigfMod.hold(150);
				for (Object[] part : JET) {
					double L = (double) part[1] * J, H = (double) part[2] * J, W = (double) part[3] * J;
					double ox = (double) part[4] * J, oy = (double) part[5] * J, oz = (double) part[6] * J;
					double cx = fx * ox + lx * oz, cz = fz * ox + lz * oz;
					double sx = Math.abs(fx) * L + Math.abs(fz) * W, sz = Math.abs(fz) * L + Math.abs(fx) * W;
					Fx.display((String) part[0], start, new double[] { sx, H, sz }, new double[] { cx - sx / 2, oy - H / 2, cz - sz / 2 }, tag, 3);
				}
				Sigf.sound(SigfMod.JET, p.position(), 3.0f, 1f);
			}
			if (i == 2) parts.addAll(Fx.tagged(tag));
			double s = -70 + i * speed;   // along the pass, 0 = right in front of the player
			Vec3 pos = new Vec3(base.x + fx * s, height, base.z + fz * s);
			if (Sigf.isDemo() && i % 2 == 0 && i >= 6 && i < 136) Sigf.lookAt(p, pos.add(0, -2, 0).lerp(base, 0.35));
			if (i >= 2) {
				for (Entity e : parts) e.teleportTo(pos.x, pos.y, pos.z);
				Vec3 tail = pos.add(-fx * 6.5, 0, -fz * 6.5);
				Fx.far(ParticleTypes.FLAME, tail, 5, 0.15, 0.02);
				Fx.far(ParticleTypes.LARGE_SMOKE, tail, 3, 0.3, 0.01);
			}
			for (int b = 0; b < bombAt.length; b++) {
				if (!dropped[b] && s >= bombAt[b] - 10) {
					dropped[b] = true;
					Vec3 hit = groundAt(new Vec3(base.x + fx * bombAt[b] + ax * bombDepth[b], base.y, base.z + fz * bombAt[b] + az * bombDepth[b]));
					final Vec3 from = pos;
					Fx.run(12, n -> {
						Vec3 q = from.lerp(hit, (n + 1) / 12.0);
						Fx.far(ParticleTypes.SMOKE, q, 3, 0.1, 0.0);
						Fx.far(ParticleTypes.FLAME, q, 2, 0.05, 0.0);
						if (n == 11) {
							Sigf.level().explode(null, hit.x, hit.y + 0.5, hit.z, 3.2f, Level.ExplosionInteraction.NONE);
							Fx.far(ParticleTypes.EXPLOSION_EMITTER, hit.add(0, 1, 0), 1, 0, 0);
							Fx.far(ParticleTypes.FLAME, hit.add(0, 1, 0), 40, 1.5, 0.12);
							Fx.far(ParticleTypes.LARGE_SMOKE, hit.add(0, 1.5, 0), 30, 1.5, 0.1);
							for (Entity e : Sigf.near(hit, 5, x -> SigfMod.isFoe(x) && x instanceof LivingEntity))
								{ ((LivingEntity) e).setRemainingFireTicks(80); SigfMod.burnUntil.put(e.getUUID(), Sigf.level().getGameTime() + 90); }
						}
					});
				}
			}
			if (i == 139) for (Entity e : parts) e.discard();
		});
	}

	// ---- Tactical nuke --------------------------------------------------------
	public static void nuke(ServerPlayer p) {
		if (nukeActive) return;
		nukeActive = true;
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		f = f.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : f.normalize();
		final Vec3 center = groundAt(p.position().add(f.scale(75)));
		Sigf.sound(SigfMod.V_NUKE, p.position(), 2.0f, 1f);
		Fx.run(100, i -> {
			if (i % 20 == 0) {
				Sigf.title("TACTICAL NUKE", "Detonation in " + (5 - i / 20), 1.2);
				if (i >= 60) Sigf.sound(SigfMod.HITMARKER, p.position(), 1.2f, 0.7f + i / 100f);
			}
		});
		Fx.run(100 + 260, i -> {
			if (Sigf.isDemo() && i == 0) SigfMod.hold(100 + 200);
			if (Sigf.isDemo() && i % 2 == 0 && i < 300) Sigf.lookAt(p, center.add(0, Math.min(18, Math.max(4, (i - 100) * 0.3 + 4)), 0));
			if (i < 100) return;
			int n = i - 100;
			ServerLevel level = Sigf.level();
			if (n == 0) {
				final String tg = "mw2nuke" + (counter++);
				final double H = 30;
				Object[][] cloud = {
					{ "minecraft:magma_block", 10.0, 4.0, 10.0, 0.0 },
					{ "minecraft:gray_concrete", 5.0, H, 5.0, 2.0 },
					{ "minecraft:orange_concrete", 16.0, 2.0, 16.0, H - 2 },
					{ "minecraft:gray_concrete", 24.0, 3.0, 24.0, H },
					{ "minecraft:light_gray_concrete", 28.0, 3.0, 28.0, H + 3 },
					{ "minecraft:gray_concrete", 22.0, 3.0, 22.0, H + 6 },
					{ "minecraft:light_gray_concrete", 13.0, 3.0, 13.0, H + 9 },
				};
				for (int k = 0; k < cloud.length; k++) {
					Object[] c = cloud[k];
					double sx = (double) c[1], sy = (double) c[2], sz = (double) c[3], oy = (double) c[4];
					Fx.stage((String) c[0], center, new double[] { sx, sy, sz }, new double[] { -sx / 2, oy, -sz / 2 }, tg + k, k == 0 ? 0 : k == 1 ? 6 : 24 + k * 14, 300);
				}
				Sigf.sound(SigfMod.NUKE, p.position(), 4.0f, 1f);
				Vec3 eye = p.getEyePosition();
				for (int k = 0; k < 4; k++) Fx.far(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), eye.add(p.getLookAngle().scale(0.5)), 1, 0, 0);
				Sigf.command("effect give @a minecraft:blindness 2 0 true");
				Sigf.command("effect give @a minecraft:nausea 6 0 true");
				Sigf.title("NUKE CONFIRMED", "Everyone is gone", 4);
			}
			double h = Math.min(38, n * 0.4);
			var r = level.getRandom();
			// stem: a thick column of grey smoke with fire at the foot
			for (double yy = 0; yy < h; yy += 2.5) {
				double w = 2.2 + 2.2 * Math.exp(-yy / 7.0);
				Fx.far(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.add(0, yy + 1, 0), 1, w * 0.7, 0.4, w * 0.7, 0.0);
				Fx.far(yy < 8 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, center.add(0, yy, 0), yy < 8 ? 8 : 3, w, 0.8, w, 0.01);
			}
			// base fire and dust
			Fx.far(ParticleTypes.FLAME, center.add(0, 1, 0), 18, 6, 0.6, 6, 0.08);
			Fx.far(ParticleTypes.EXPLOSION, center.add((r.nextDouble() - 0.5) * 12, 1, (r.nextDouble() - 0.5) * 12), 1, 0, 0);
			// cap: a fat dome of smoke on a rolling rim, lit orange underneath
			if (h > 12) {
				double R = 9 + Math.min(10, n * 0.05);
				Vec3 c = center.add(0, h + 2, 0);
				for (int k = 0; k < 14; k++) {
					double a = r.nextDouble() * 6.283, b = r.nextDouble() * 3.4;       // b: 0 = outer rim, pi = underside
					double rr = 5.5;
					double q = R + rr * Math.cos(b);
					Vec3 pt = c.add(Math.cos(a) * q * (b > 1.6 ? 0.75 : 1.0), rr * 0.85 * Math.sin(b) - 1.0, Math.sin(a) * q * (b > 1.6 ? 0.75 : 1.0));
					Fx.far(ParticleTypes.CAMPFIRE_COSY_SMOKE, pt, 1, 1.0, 0.8, 1.0, 0.0);
					Fx.far(ParticleTypes.LARGE_SMOKE, pt, 2, 1.4, 1.0, 1.4, 0.0);
					if (b > 2.2) Fx.far(ParticleTypes.FLAME, pt.add(0, -1.2, 0), 3, 1.4, 0.5, 1.4, 0.02);
				}
				for (int k = 0; k < 6; k++) {   // cap top, filled
					double a = r.nextDouble() * 6.283, d = Math.sqrt(r.nextDouble()) * (R - 1);
					Fx.far(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.add(Math.cos(a) * d, 3.5 + r.nextDouble() * 3, Math.sin(a) * d), 1, 0.4, 0.4, 0.4, 0.0);
				}
			}
			// shockwave: kills enemies as it passes, ring on the ground
			double rad = n * 1.8;
			if (rad < 140) {
				int pts = (int) Math.min(120, 12 + rad * 1.5);
				for (int k = 0; k < pts; k++) {
					double a = k / (double) pts * 6.283;
					Vec3 g = groundAt(center.add(Math.cos(a) * rad, 0, Math.sin(a) * rad));
					Fx.far(ParticleTypes.CLOUD, g.add(0, 0.4, 0), 1, 0.2, 0.0);
				}
				for (Entity e : Sigf.near(center, rad + 2, x -> SigfMod.isFoe(x) && x instanceof LivingEntity)) {
					if (e.position().distanceTo(center) > rad - 4) {
						LivingEntity le = (LivingEntity) e;
						Vec3 away = e.position().subtract(center).normalize();
						le.setDeltaMovement(away.x * 1.4, 0.9, away.z * 1.4);
						le.hurtServer(level, level.damageSources().explosion(null, null), 1000f);
						Fx.far(ParticleTypes.FLAME, e.position().add(0, 1, 0), 12, 0.4, 0.05);
					}
				}
			}
			if (n == 300 || n == 259) nukeActive = false;
		});
	}
}
