/**
 * The guard booth: player state and power.
 * ENCAPSULATION: all state is private. Power is clamped to 0-100 inside addPower(),
 * and tasks can only be started/finished through startTask()/updateTask().
 */
public class Office {
	public enum Task { NONE, PATCH, FIX, REBOOT }

	public static final double WALL_COST = 3.0; // % power to deploy a wall
	public static final double REBOOT_GAIN = 25.0; // % power restored by a reboot

	private boolean monitorUp = false;
	private int currentCam = 0;
	private double power = 100;
	private boolean blackout = false;
	private boolean windowBroken = false;
	private Task task = Task.NONE;
	private double taskTime = 0;

	// ---- Getters ----
	public boolean isMonitorUp() {
		return monitorUp;
	}

	public int getCurrentCam() {
		return currentCam;
	}

	public double getPower() {
		return power;
	}

	public boolean isBlackout() {
		return blackout;
	}

	public boolean isWindowBroken() {
		return windowBroken;
	}

	public Task getTask() {
		return task;
	}

	public double getTaskTime() {
		return taskTime;
	}

	public boolean isBusy() {
		return task != Task.NONE;
	}

	// ---- Setters ----
	public void setMonitorUp(boolean up) {
		monitorUp = up;
	}

	public void setCurrentCam(int cam) {
		currentCam = cam;
	}

	public void setBlackout(boolean b) {
		blackout = b;
	}

	public void setWindowBroken(boolean b) {
		windowBroken = b;
	}

	/** OVERLOADED: change power, capped at 100. */
	public void addPower(double amount) {
		addPower(amount, 100);
	}

	/** OVERLOADED: change power with a custom cap. Never goes below 0. */
	public void addPower(double amount, double max) {
		power = Math.max(0, Math.min(max, power + amount));
	}

	public boolean isPowerEmpty() {
		return power <= 0;
	}

	public void startTask(Task t, double seconds) {
		task = t;
		taskTime = seconds;
	}

	/** Counts the current task down. Returns the task that just finished, or NONE. */
	public Task updateTask(double dt) {
		if (task == Task.NONE) return Task.NONE;
		taskTime -= dt;
		if (taskTime > 0) return Task.NONE;
		Task done = task;
		task = Task.NONE;
		taskTime = 0;
		return done;
	}

	/** % power drained per second: base load + monitor + each active wall. */
	public double getDrainPerSecond(int wallsUp) {
		double d = 0.07;
		if (monitorUp) d += 0.12;
		return d + 0.08 * wallsUp;
	}

	/** Number of power-using devices (for the Usage bar). */
	public int getUsage(int wallsUp) {
		return 1 + (monitorUp ? 1 : 0) + wallsUp;
	}
}
