package sigf.mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import sigf.kit.Sigf;

/** Hitscan rifle shared by the player and the OpFor soldiers. */
public final class Gun {
	private Gun() {}

	static final DustParticleOptions TRACER = new DustParticleOptions(0xFFD34A, 0.4f);
	static final DustParticleOptions BLOOD = new DustParticleOptions(0xB01818, 0.45f);
	static final Map<UUID, Long> nextBurst = new HashMap<>();

	/** One bullet. Returns the living entity hit, or null. */
	public static LivingEntity shoot(LivingEntity shooter, Vec3 from, Vec3 dir, double range, float damage, double spread, boolean loud) {
		ServerLevel level = Sigf.level();
		var r = level.getRandom();
		Vec3 d = dir.normalize().add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread).normalize();
		Vec3 to = from.add(d.scale(range));
		HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		double maxDist = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(from);
		Vec3 end = from.add(d.scale(maxDist));
		AABB box = new AABB(from, end).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, from, end, box,
			e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && e != shooter && !(e instanceof Player p && p.isCreative()) && (shooter instanceof Player || !e.entityTags().contains("mw2")), maxDist * maxDist);
		Vec3 stop = hit != null ? hit.getLocation() : end;

		// tracer
		double len = stop.distanceTo(from);
		for (double t = 1.5; t < len; t += 1.1) {
			Vec3 p = from.add(d.scale(t));
			level.sendParticles(TRACER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		// muzzle flash
		Vec3 muzzle = from.add(d.scale(shooter instanceof ServerPlayer ? 2.6 : 1.2));
		level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y - 0.2, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
		Sigf.sound(SigfMod.RIFLE_SHOT, from, loud ? 1.0f : 0.6f, 0.92f + r.nextFloat() * 0.2f);

		if (hit == null) {
			if (block.getType() != HitResult.Type.MISS && shooter instanceof Player) {
				level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.1, 0.1, 0.1, 0.15);
				level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 2, 0.05, 0.05, 0.05, 0.01);
			}
			return null;
		}
		LivingEntity target = (LivingEntity) hit.getEntity();
		boolean head = stop.y > target.getEyeY() - 0.28;
		float dmg = head ? damage * 2f : damage;
		DamageSource src = shooter instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(shooter);
		if (shooter instanceof ServerPlayer) SigfMod.hitHead = head;
		target.hurtServer(level, src, dmg);
		boolean near = stop.distanceTo(from) < 6;
		level.sendParticles(BLOOD, stop.x, stop.y, stop.z, near ? 2 : head ? 12 : 7, 0.12, 0.12, 0.12, 0.05);
		level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.15, 0.15, 0.15, 0.3);
		if (shooter instanceof ServerPlayer sp) {
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SigfMod.HITMARKER, net.minecraft.sounds.SoundSource.MASTER, 0.7f, head ? 1.5f : 1.1f);
			sp.sendSystemMessage(Component.literal(head ? "§c§l[ X ]  HEADSHOT" : "§f§l[ X ]"), true);
		}
		return target;
	}

	/** A three-round burst from the player's eyes along their view (rate limited). */
	public static boolean burst(ServerPlayer p) {
		long now = Sigf.level().getGameTime();
		if (nextBurst.getOrDefault(p.getUUID(), 0L) > now) return false;
		nextBurst.put(p.getUUID(), now + 12);
		for (int i = 0; i < 3; i++) {
			final int k = i;
			Fx.run(k * 2 + 1, n -> {
				if (n == k * 2 && p.isAlive()) {
					Vec3 eye = p.getEyePosition();
					Vec3 side = p.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale(0.25);
					shoot(p, eye.add(side).add(0, -0.1, 0), p.getLookAngle(), 70, Sigf.isDemo() ? 6.0f : 4.0f, 0.012, true);
				}
			});
		}
		return true;
	}

	public static final class RifleItem extends Item {
		public RifleItem(Properties p) { super(p); }
		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) burst(sp);
			return InteractionResult.SUCCESS;
		}
	}

	public static final class SmokeItem extends Item {
		public SmokeItem(Properties p) { super(p); }
		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
			Vec3 eye = sp.getEyePosition();
			Vec3 to = eye.add(sp.getLookAngle().scale(30));
			HitResult hr = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
			Vec3 spot = hr.getType() == HitResult.Type.MISS ? to : hr.getLocation();
			ItemStack st = sp.getItemInHand(hand);
			if (!sp.getAbilities().instabuild) st.shrink(1);
			Streaks.carePackage(spot, true);
			return InteractionResult.SUCCESS;
		}
	}
}
