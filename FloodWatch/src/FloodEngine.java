import java.util.Random;

/**
 * Game rules. Replaces Foxy/Freddy/Bonnie/Chicka: instead of animatronics moving
 * toward the office, the flood rises in each area and random storm events hit.
 * Call tick(dt) with the elapsed seconds (the UI calls it every 100 ms).
 */
public class FloodEngine {
	// ---- Tunables (balance here) ----
	public static final double NIGHT_SECONDS = 360; // real seconds for 12AM -> 6AM
	public static final double WALL_DURATION = 25; // seconds a wall holds
	public static final double REBOOT_TIME = 8;
	public static final double PATCH_TIME = 3;
	public static final double FIX_TIME = 4;
	public static final double BASE_RATE = 0.15; // % per second at 12AM
	public static final double RATE_PER_HOUR = 0.05; // extra % per second each hour
	public static final double CHANCE_BLACKOUT = 0.003; // per second
	public static final double CHANCE_WINDOW = 0.006;
	public static final double CHANCE_PIPE = 0.006;

	public final Area[] areas;
	public final Office office = new Office();
	public double elapsed = 0;
	public boolean won = false;
	public boolean lost = false; // drowned -> alligator jumpscare
	public String alert = ""; // latest event message for the HUD
	public double alertTime = 0;
	public double difficulty; // 1.0 = normal

	private final Random rng = new Random();

	public FloodEngine(double difficulty) {
		this.difficulty = difficulty;
		areas = new Area[] {
			new Area("RUBBLE LOT", "cam1.png", 1.0),
			new Area("GREEN HOUSE", "cam2.png", 1.0),
			new Area("MAIN ROAD", "cam3.png", 1.1),
			new Area("CREEK BANK", "cam4.png", 1.2),
			new Area("MARKET ALLEY", "cam5.png", 0.9),
			new Area("BARANGAY GATE", "cam6.png", 0.8)
		};
	}

	// ---------- Derived values ----------
	public double getWaterLevel() {
		double sum = 0;
		for (Area a : areas) sum += a.level;
		return sum / areas.length;
	}

	public int countFlooded() {
		int n = 0;
		for (Area a : areas) if (a.flooded) n++;
		return n;
	}

	public int countWalls() {
		int n = 0;
		for (Area a : areas) if (a.wallUp()) n++;
		return n;
	}

	public double getHour() {
		return elapsed / (NIGHT_SECONDS / 6.0);
	}

	public String getClockText() {
		int h = (int) getHour();
		return (h == 0 ? 12 : h) + " AM";
	}

	public boolean isOver() {
		return won || lost;
	}

	private void raise(String msg) {
		alert = msg;
		alertTime = 4;
	}

	// ---------- Main loop (the old Foxy.tick() etc.) ----------
	public void tick(double dt) {
		if (isOver()) return;
		elapsed += dt;
		if (alertTime > 0) alertTime -= dt;
		if (elapsed >= NIGHT_SECONDS) {
			won = true;
			return;
		}

		updateBusy(dt);
		updateFlood(dt);
		updatePower(dt);
		rollRandomEvents(dt);

		if (getWaterLevel() >= 99.99) {
			lost = true;
			office.monitorUp = false;
		}
	}

	private void updateFlood(double dt) {
		double base = (BASE_RATE + RATE_PER_HOUR * getHour()) * difficulty;
		base *= 1 + 0.15 * countFlooded(); // flooded areas make the rest rise faster
		if (office.windowBroken) base *= 1.5;
		if (office.busyAction == Office.REBOOT) base *= 2; // flooding while rebooting

		for (Area a : areas) {
			if (a.flooded) continue;
			if (a.wallUp()) {
				a.wallTimeLeft -= dt;
				continue; // flood is held back
			}
			double rate = base * a.weight * (0.7 + rng.nextDouble() * 0.6);
			if (a.pipeBurst) rate *= 3;
			a.level += rate * dt;
			if (a.level >= 100) {
				a.level = 100;
				a.flooded = true;
				a.pipeBurst = false;
				raise(a.name + " HAS FLOODED - CAMERA LOST");
			}
		}
	}

	private void updatePower(double dt) {
		if (office.blackout) return;
		if (office.busyAction == Office.REBOOT) return; // everything is off while rebooting
		office.power -= office.drainPerSecond(countWalls()) * dt;
		if (office.power <= 0) {
			office.power = 0;
			startBlackout("POWER DEPLETED - REBOOT REQUIRED");
		}
	}

	private void startBlackout(String msg) {
		office.blackout = true;
		office.monitorUp = false;
		for (Area a : areas) a.wallTimeLeft = 0;
		raise(msg);
	}

	private void rollRandomEvents(double dt) {
		if (!office.blackout && rng.nextDouble() < CHANCE_BLACKOUT * dt) {
			startBlackout("STORM KNOCKED OUT THE POWER - REBOOT REQUIRED");
		}
		if (!office.windowBroken && rng.nextDouble() < CHANCE_WINDOW * dt) {
			office.windowBroken = true;
			raise("WINDOW SHATTERED - PATCH IT");
		}
		if (rng.nextDouble() < CHANCE_PIPE * dt) {
			Area a = areas[rng.nextInt(areas.length)];
			if (!a.flooded && !a.pipeBurst) {
				a.pipeBurst = true;
				raise("PIPE BURST AT " + a.name);
			}
		}
	}

	private void updateBusy(double dt) {
		if (!office.isBusy()) return;
		office.busyTime -= dt;
		if (office.busyTime > 0) return;
		switch (office.busyAction) {
		case Office.PATCH:
			office.windowBroken = false;
			break;
		case Office.FIX:
			areas[office.currentCam].pipeBurst = false;
			break;
		case Office.REBOOT:
			office.blackout = false;
			office.power = Math.min(100, office.power + Office.REBOOT_GAIN);
			break;
		}
		office.busyAction = Office.NONE;
		office.busyTime = 0;
	}

	// ---------- Player actions (each returns true if it worked) ----------
	private boolean canAct() {
		return !isOver() && !office.isBusy();
	}

	public boolean toggleMonitor() {
		if (!canAct() || office.blackout) return false;
		office.monitorUp = !office.monitorUp;
		return true;
	}

	public boolean selectCam(int i) {
		if (!canAct() || !office.monitorUp || i < 0 || i >= areas.length) return false;
		office.currentCam = i;
		return true;
	}

	/** Deploy a flood wall in the camera currently being viewed. */
	public boolean deployWall() {
		if (!canAct() || office.blackout || !office.monitorUp) return false;
		Area a = areas[office.currentCam];
		if (a.flooded || a.wallUp()) return false;
		a.wallTimeLeft = WALL_DURATION;
		office.power = Math.max(0, office.power - Office.WALL_COST);
		return true;
	}

	/** Patch the broken window with wood (done from the booth, monitor down). */
	public boolean patchWindow() {
		if (!canAct() || !office.windowBroken || office.monitorUp) return false;
		office.busyAction = Office.PATCH;
		office.busyTime = PATCH_TIME;
		return true;
	}

	/** Fix the burst pipe in the area currently on camera. Locks you on that camera. */
	public boolean fixPipe() {
		if (!canAct() || office.blackout || !office.monitorUp) return false;
		if (!areas[office.currentCam].pipeBurst) return false;
		office.busyAction = Office.FIX;
		office.busyTime = FIX_TIME;
		return true;
	}

	/** Reboot the generator: restores power but floods faster while it runs. */
	public boolean reboot() {
		if (!canAct()) return false;
		office.monitorUp = false;
		office.busyAction = Office.REBOOT;
		office.busyTime = REBOOT_TIME;
		return true;
	}
}
