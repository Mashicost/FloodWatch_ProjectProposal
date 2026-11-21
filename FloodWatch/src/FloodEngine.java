import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Game rules. ENCAPSULATION: all state is private; the UI only sees getters and
 * validated action methods, and never touches Area or Office directly.
 * POLYMORPHISM: areas and hazards are stored by their base types (Area, Hazard)
 * and each subclass behaves differently when the same method is called.
 */
public class FloodEngine {
	// ---- Tunables (balance here) ----
	public static final double NIGHT_SECONDS = 360; // real seconds for 12AM -> 6AM
	public static final double WALL_DURATION = 25;
	public static final double REBOOT_TIME = 8;
	public static final double PATCH_TIME = 3;
	public static final double FIX_TIME = 4;
	public static final double BASE_RATE = 0.15; // % per second at 12AM
	public static final double RATE_PER_HOUR = 0.05;
	public static final double DEFAULT_TICK = 0.1;

	private final List<Area> areas = new ArrayList<Area>();
	private final List<Hazard> hazards = new ArrayList<Hazard>();
	private final Office office = new Office();
	private final Random rng = new Random();
	private final double difficulty; // 1.0 = normal
	private double elapsed = 0;
	private boolean won = false;
	private boolean lost = false;
	private String alert = "";
	private double alertTime = 0;

	/** OVERLOADED constructor: normal difficulty. */
	public FloodEngine() {
		this(1.0);
	}

	/** OVERLOADED constructor: custom difficulty multiplier. */
	public FloodEngine(double difficulty) {
		this.difficulty = difficulty;
		areas.add(new Area("RUBBLE LOT", "cam1.png"));                // CAM 1 top-left
		areas.add(new Area("GREEN HOUSE", "cam2.png"));               // CAM 2 top-right
		areas.add(new LowLyingArea("CANAL", "cam3.png"));             // CAM 3 canal
		areas.add(new HighGroundArea("OFFICE ROAD", "cam4.png"));     // CAM 4 lower-left
		areas.add(new LowLyingArea("BASKETBALL COURT", "cam5.png"));  // CAM 5 lower-right
		hazards.add(new BlackoutHazard(0.003));
		hazards.add(new WindowHazard(0.006));
		hazards.add(new PipeBurstHazard(0.006));
	}

	// ================= Read-only access for the UI =================
	public int getAreaCount() {
		return areas.size();
	}

	public Area getArea(int i) {
		return areas.get(i);
	}

	public List<Area> getAreas() {
		return Collections.unmodifiableList(areas);
	}

	public Area getCurrentArea() {
		return areas.get(office.getCurrentCam());
	}

	public boolean isMonitorUp() {
		return office.isMonitorUp();
	}

	public int getCurrentCam() {
		return office.getCurrentCam();
	}

	public double getPower() {
		return office.getPower();
	}

	public boolean isBlackout() {
		return office.isBlackout();
	}

	public boolean isWindowBroken() {
		return office.isWindowBroken();
	}

	public Office.Task getTask() {
		return office.getTask();
	}

	public double getTaskTimeLeft() {
		return office.getTaskTime();
	}

	public boolean isBusy() {
		return office.isBusy();
	}

	public int getUsage() {
		return office.getUsage(countWalls());
	}

	public boolean isWon() {
		return won;
	}

	public boolean isLost() {
		return lost;
	}

	public boolean isOver() {
		return won || lost;
	}

	public String getAlert() {
		return alert;
	}

	public boolean hasAlert() {
		return alertTime > 0;
	}

	public double getWaterLevel() {
		double sum = 0;
		for (Area a : areas) sum += a.getLevel();
		return sum / areas.size();
	}

	public int countFlooded() {
		int n = 0;
		for (Area a : areas) if (a.isFlooded()) n++;
		return n;
	}

	public int countWalls() {
		int n = 0;
		for (Area a : areas) if (a.hasWall()) n++;
		return n;
	}

	public double getHour() {
		return elapsed / (NIGHT_SECONDS / 6.0);
	}

	public String getClockText() {
		int h = (int) getHour();
		return (h == 0 ? 12 : h) + " AM";
	}

	// ================= Hooks used by Hazard subclasses =================
	public void causeBlackout() {
		office.setBlackout(true);
		office.setMonitorUp(false);
		for (Area a : areas) a.dropWall();
	}

	public void setWindowBroken(boolean broken) {
		office.setWindowBroken(broken);
	}

	/** OVERLOADED: show an alert for the default 4 seconds. */
	private void raise(String msg) {
		raise(msg, 4);
	}

	/** OVERLOADED: show an alert for a custom time. */
	private void raise(String msg, double seconds) {
		alert = msg;
		alertTime = seconds;
	}

	// ================= Game loop =================
	/** OVERLOADED: advance one default step (0.1 s). */
	public void tick() {
		tick(DEFAULT_TICK);
	}

	/** OVERLOADED: advance by dt seconds. */
	public void tick(double dt) {
		if (isOver()) return;
		elapsed += dt;
		if (alertTime > 0) alertTime -= dt;
		if (elapsed >= NIGHT_SECONDS) {
			won = true;
			return;
		}
		updateTask(dt);
		updateFlood(dt);
		updatePower(dt);
		rollHazards(dt);

		if (getWaterLevel() >= 99.99) {
			lost = true; // alligator jumpscare
			office.setMonitorUp(false);
		}
	}

	private void updateTask(double dt) {
		switch (office.updateTask(dt)) {
		case PATCH:
			office.setWindowBroken(false);
			break;
		case FIX:
			getCurrentArea().setPipeBurst(false);
			break;
		case REBOOT:
			office.setBlackout(false);
			office.addPower(Office.REBOOT_GAIN);
			break;
		default:
			break;
		}
	}

	private void updateFlood(double dt) {
		double base = (BASE_RATE + RATE_PER_HOUR * getHour()) * difficulty;
		base *= 1 + 0.15 * countFlooded(); // flooded areas speed up the rest
		if (office.isWindowBroken()) base *= 1.5;
		if (office.getTask() == Office.Task.REBOOT) base *= 2;

		for (Area a : areas) {
			if (a.isFlooded()) continue;
			if (a.hasWall()) {
				a.tickWall(dt); // wall holds the water back
				continue;
			}
			double rate = base * (0.7 + rng.nextDouble() * 0.6);
			double pipe = a.isPipeBurst() ? 3 : 1;
			// polymorphism: addWater applies this area's own terrain multiplier
			if (a.addWater(rate * dt, pipe)) {
				raise(a.getName() + " HAS FLOODED - CAMERA LOST");
			}
		}
	}

	private void updatePower(double dt) {
		if (office.isBlackout() || office.getTask() == Office.Task.REBOOT) return;
		office.addPower(-office.getDrainPerSecond(countWalls()) * dt);
		if (office.isPowerEmpty()) {
			causeBlackout();
			raise("POWER DEPLETED - REBOOT REQUIRED");
		}
	}

	private void rollHazards(double dt) {
		for (Hazard h : hazards) { // polymorphism: each Hazard acts differently
			if (rng.nextDouble() < h.getChancePerSecond() * dt && h.canTrigger(this)) {
				raise(h.trigger(this, rng));
			}
		}
	}

	// ================= Player actions =================
	private boolean canAct() {
		return !isOver() && !office.isBusy();
	}

	public boolean toggleMonitor() {
		if (!canAct() || office.isBlackout()) return false;
		office.setMonitorUp(!office.isMonitorUp());
		return true;
	}

	/** OVERLOADED: select a camera by index. */
	public boolean selectCam(int i) {
		if (!canAct() || !office.isMonitorUp() || i < 0 || i >= areas.size()) return false;
		office.setCurrentCam(i);
		return true;
	}

	/** OVERLOADED: select a camera by area name, e.g. selectCam("creek bank"). */
	public boolean selectCam(String areaName) {
		for (int i = 0; i < areas.size(); i++) {
			if (areas.get(i).getName().equalsIgnoreCase(areaName)) return selectCam(i);
		}
		return false;
	}

	/** OVERLOADED: deploy a wall on the camera currently being viewed. */
	public boolean deployWall() {
		return deployWall(office.getCurrentCam());
	}

	/** OVERLOADED: deploy a wall on a specific camera. */
	public boolean deployWall(int cam) {
		if (!canAct() || office.isBlackout() || !office.isMonitorUp()) return false;
		if (cam < 0 || cam >= areas.size()) return false;
		if (!areas.get(cam).raiseWall()) return false;
		office.addPower(-Office.WALL_COST);
		return true;
	}

	/** Patch the window with wood (booth view only). */
	public boolean patchWindow() {
		if (!canAct() || !office.isWindowBroken() || office.isMonitorUp()) return false;
		office.startTask(Office.Task.PATCH, PATCH_TIME);
		return true;
	}

	/** Fix the burst pipe in the area on camera; locks you on that camera. */
	public boolean fixPipe() {
		if (!canAct() || office.isBlackout() || !office.isMonitorUp()) return false;
		if (!getCurrentArea().isPipeBurst()) return false;
		office.startTask(Office.Task.FIX, FIX_TIME);
		return true;
	}

	/** Reboot the generator: restores power, but flooding doubles while it runs. */
	public boolean reboot() {
		if (!canAct()) return false;
		office.setMonitorUp(false);
		office.startTask(Office.Task.REBOOT, REBOOT_TIME);
		return true;
	}
}
