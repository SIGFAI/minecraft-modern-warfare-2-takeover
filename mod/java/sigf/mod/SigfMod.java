package sigf.mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** Modern Warfare 2 Takeover: every monster is an OpFor soldier, Ghost sniper, Juggernaut or mine bot. Kill streaks call in air support. */
public final class SigfMod implements ModInitializer {
	public static Item RIFLE, SMOKE_GRENADE;
	public static SoundEvent RIFLE_SHOT, HITMARKER, NUKE, JET, SMOKE, V_CARE, V_HARRIER, V_NUKE;
	public static boolean hitHead;

	/** kills, streak */
	static final Map<UUID, int[]> stats = new HashMap<>();
	static int waveNo;
	static long holdUntil;
	/** The demo camera is busy for ticks game ticks: the bot stops aiming at soldiers. */
	public static void hold(int ticks) { holdUntil = Math.max(holdUntil, Sigf.level().getGameTime() + ticks); }
	static int demoReward = -1;
	static boolean demoFired;
	/** Entities that burn on purpose (air strike) until this game tick. */
	static final Map<UUID, Long> burnUntil = new HashMap<>();

	@Override
	public void onInitialize() {
		RIFLE = Sigf.item("rifle", p -> new Gun.RifleItem(p.stacksTo(1)));
		SMOKE_GRENADE = Sigf.item("smoke_grenade", p -> new Gun.SmokeItem(p.stacksTo(8)));
		RIFLE_SHOT = Sigf.registerSound("rifle_shot");
		HITMARKER = Sigf.registerSound("hitmarker");
		NUKE = Sigf.registerSound("nuke");
		JET = Sigf.registerSound("jet");
		SMOKE = Sigf.registerSound("smoke");
		V_CARE = Sigf.registerSound("v_care");
		V_HARRIER = Sigf.registerSound("v_harrier");
		V_NUKE = Sigf.registerSound("v_nuke");
		Fx.init();

		ServerLivingEntityEvents.AFTER_DEATH.register(SigfMod::onDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Sigf.after(1.5, () -> kitUp(handler.getPlayer())));

		// OpFor waves
		Sigf.every(Sigf.isDemo() ? 5 : 8, () -> {
			if (Streaks.nukeActive || Sigf.host() == null) return;
			int alive = (int) Sigf.near(Sigf.host().position(), 70, SigfMod::isFoe).size();
			if (alive < (Sigf.isPlayer() ? 10 : 9)) squad(false);
		});
		// the sun must not set the soldiers on fire (only the air strikes do)
		Sigf.every(0.25, () -> {
			long now = Sigf.level().getGameTime();
			for (Entity e : Sigf.level().getAllEntities())
				if (e.entityTags().contains("mw2") && e.isOnFire() && burnUntil.getOrDefault(e.getUUID(), 0L) < now) e.clearFire();
		});
		// soldiers shoot back
		Sigf.every(1, SigfMod::soldiersFire);
		Sigf.every(2, SigfMod::juggernautHunt);
		// stream showcase when nobody plays (camera circling): air support on a loop
		if (!Sigf.isDemo()) {
			final int[] k = { 0 };
			Sigf.every(14, () -> {
				ServerPlayer h = Sigf.host();
				if (h == null || Sigf.isPlayer() || Streaks.nukeActive) return;
				switch (k[0]++ % 3) {
					case 0 -> Streaks.carePackage(Sigf.ground(8), false);
					case 1 -> Streaks.harrier(h);
					default -> Streaks.uav(h);
				}
			});
		}
		demoScript();
	}

	// ---- helpers ---------------------------------------------------------------
	public static boolean isFoe(Entity e) {
		return e.isAlive() && (e instanceof Enemy || e.entityTags().contains("mw2"));
	}

	static void kitUp(ServerPlayer p) {
		if (p == null) return;
		Sigf.command("give " + p.getScoreboardName() + " sigf:rifle 1");
		Sigf.command("give " + p.getScoreboardName() + " sigf:smoke_grenade 3");
		Sigf.command("give " + p.getScoreboardName() + " minecraft:cooked_beef 8");
		if (!Sigf.isDemo()) {
			Sigf.title("MODERN WARFARE 2 TAKEOVER", "Right-click the rifle. Get 10 kills in a row for a TACTICAL NUKE.", 5);
		}
	}

	static int[] st(ServerPlayer p) { return stats.computeIfAbsent(p.getUUID(), u -> new int[2]); }

	// ---- kills and streaks -------------------------------------------------------
	static void onDeath(LivingEntity victim, net.minecraft.world.damagesource.DamageSource src) {
		try {
			if (victim instanceof ServerPlayer sp) { st(sp)[1] = 0; return; }
			if (!(src.getEntity() instanceof ServerPlayer p) || !isFoe(victim)) return;
			int[] s = st(p);
			s[0]++; s[1]++;
			boolean head = hitHead; hitHead = false;
			String what = victim instanceof net.minecraft.world.entity.animal.golem.IronGolem ? "JUGGERNAUT DOWN" : victim instanceof net.minecraft.world.entity.monster.Creeper ? "MINE BOT DOWN" : "ENEMY DOWN";
			p.sendSystemMessage(Component.literal("§a§l+" + (head ? 150 : 100) + "  §f§l" + what + (head ? " §c(HEADSHOT)" : "") + "  §7STREAK §e" + s[1] + "  §7KILLS §e" + s[0]), true);
			Vec3 at = victim.position();
			Sigf.sound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, at, 0.8f, 0.7f + 0.1f * Math.min(8, s[1]));
			streak(p, s);
		} catch (Throwable t) {
			Sigf.log("MW2 death error " + t);
		}
	}

	/** Rewards at 3, 5, 7 and 10 kills in a row. */
	static void streak(ServerPlayer p, int[] s) {
		int r = s[1];
		if (Sigf.isDemo()) {   // the demo spaces the rewards out on a script
			if (demoFired || s[1] < demoReward) return;
			demoFired = true; r = demoReward;
		}
		Sigf.log("MW2 streak " + r);
		switch (r) {
			case 3 -> Streaks.uav(p);
			case 5 -> Streaks.carePackage(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(9)), false);
			case 7 -> Streaks.harrier(p);
			case 10 -> { Streaks.nuke(p); s[1] = 0; }
			default -> { }
		}
	}

	// ---- enemies ---------------------------------------------------------------------
	static void tagFoe(Mob m, String name) {
		m.addTag("mw2");
		m.setCustomName(Component.literal(name));
		m.setPersistenceRequired();
		m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
	}

	static Mob spawnFoe(EntityType<? extends Mob> type, Vec3 pos) {
		Mob m = Sigf.spawn(type, pos);
		if (m == null) return null;
		if (type == EntityTypes.ZOMBIE) {
			tagFoe(m, "OpFor Soldier");
			m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RIFLE));
			m.setDropChance(EquipmentSlot.MAINHAND, 0f);
		} else if (type == EntityTypes.SKELETON) {
			tagFoe(m, "Ghost");
		} else if (type == EntityTypes.CREEPER) {
			tagFoe(m, "Mine Bot");
		} else if (type == EntityTypes.IRON_GOLEM) {
			tagFoe(m, "Juggernaut");
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
			m.setHealth(60f);
			m.getAttribute(Attributes.SCALE).setBaseValue(1.15);
			m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9);
		}
		ServerPlayer h = Sigf.host();
		if (h != null && !h.isSpectator()) m.setTarget(h);
		return m;
	}

	static Vec3 spawnSpot() {
		ServerPlayer h = Sigf.host();
		var r = Sigf.level().getRandom();
		if (h == null || (!Sigf.isDemo() && !Sigf.isPlayer())) return Sigf.ground(14);
		double base = Sigf.isDemo() ? Math.atan2(h.getLookAngle().z, h.getLookAngle().x) : r.nextDouble() * Math.PI * 2;
		double a = Sigf.isDemo() ? base + (r.nextDouble() - 0.5) * 1.5 : base;
		double d = Sigf.isDemo() ? 9 + r.nextDouble() * 5 : 18 + r.nextDouble() * 8;
		return Sigf.ground(h.position().add(Math.cos(a) * d, 0, Math.sin(a) * d), 1.5);
	}

	static void squad(boolean forceJugg) {
		waveNo++;
		Vec3 c = spawnSpot();
		var r = Sigf.level().getRandom();
		int n = 2 + r.nextInt(3);
		for (int i = 0; i < n; i++) {
			Vec3 p = Sigf.ground(c, 3.5);
			int roll = r.nextInt(10);
			spawnFoe(roll < 6 ? EntityTypes.ZOMBIE : roll < 8 ? EntityTypes.SKELETON : EntityTypes.CREEPER, p);
		}
		if (forceJugg || waveNo % 5 == 0) {
			spawnFoe(EntityTypes.IRON_GOLEM, Sigf.ground(c, 4));
			Sigf.say("§7[Command] §eJuggernaut spotted!");
		}
	}

	static void soldiersFire() {
		ServerPlayer h = Sigf.host();
		if (h == null || h.isSpectator()) return;
		var r = Sigf.level().getRandom();
		for (Entity e : Sigf.near(h.position(), 30, x -> x.entityTags().contains("mw2") && x instanceof net.minecraft.world.entity.monster.zombie.Zombie)) {
			LivingEntity z = (LivingEntity) e;
			double d = z.distanceTo(h);
			if (d < 5 || r.nextInt(100) > 40 || !z.hasLineOfSight(h)) continue;
			Vec3 from = z.getEyePosition();
			Vec3 dir = h.position().add(0, 1.0, 0).subtract(from);
			for (int i = 0; i < 2; i++) {
				final int k = i;
				Fx.run(k * 3 + 1, n -> { if (n == k * 3 && z.isAlive()) Gun.shoot(z, from, dir, 40, 1.5f, 0.07, false); });
			}
		}
	}

	static void juggernautHunt() {
		ServerPlayer h = Sigf.host();
		if (h == null || h.isSpectator()) return;
		for (Entity e : Sigf.near(h.position(), 80, x -> x instanceof net.minecraft.world.entity.animal.golem.IronGolem && x.entityTags().contains("mw2")))
			((Mob) e).setTarget(h);
	}

	// ---- demo --------------------------------------------------------------------------
	static void demoStreak(double at, int setTo, int reward) {
		Sigf.demo(at, () -> { st(Sigf.host())[1] = setTo; demoReward = reward; demoFired = false; });
		Sigf.demo(at + 7, () -> {
			int[] s = st(Sigf.host());
			if (!demoFired) { s[1] = reward; streak(Sigf.host(), s); }
		});
	}

	static void demoScript() {
		Sigf.demo(0.5, () -> {
			Sigf.command("time set 12200");
			Sigf.command("gamerule advance_time false");
			Sigf.command("weather clear");
			for (String g : new String[] { "short_grass", "tall_grass", "fern", "large_fern", "dead_bush" })
				Sigf.command("execute at @p run fill ~-24 ~-2 ~-24 ~24 ~4 ~24 air replace minecraft:" + g);
		});
		Sigf.demo(1, () -> Sigf.title("MODERN WARFARE 2", "TAKEOVER", 3));
		Sigf.demo(2.5, () -> {
			squad(false);
		});
		// the bot plays: aims at the nearest foe and fires bursts
		Sigf.every(0.5, () -> {
			if (!Sigf.isDemo()) return;
			ServerPlayer h = Sigf.host();
			if (h == null || Sigf.level().getGameTime() < holdUntil) return;
			Entity best = null; double bd = 1e9;
			for (Entity e : Sigf.near(h.position(), 45, SigfMod::isFoe)) {
				double d = e.distanceTo(h) + (h.hasLineOfSight(e) ? 0 : 30);
				if (d < bd) { bd = d; best = e; }
			}
			if (best == null || bd > 60) return;
			Sigf.lookAt(h, best.position().add(0, best.getBbHeight() * 0.8, 0));
			Gun.burst(h);
		});
		Sigf.demo(6, () -> { squad(false); });
		// each reward is earned by the next kill; if the fight is slow the script gives the kill streak anyway
		demoStreak(10, 2, 3);
		demoStreak(22, 4, 5);
		demoStreak(36, 6, 7);
		Sigf.demo(28, () -> squad(true));
		Sigf.demo(44, () -> squad(false));
		demoStreak(52, 9, 10);
		Sigf.demo(61, () -> squad(false));
	}
}
