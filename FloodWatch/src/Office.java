/**
 * The guard booth: player state and power. Same role as the old Office class
 * (monitorUp, powerRemaining, getUsage) but instance-based so the game can restart.
 * Lights/doors are replaced by flood walls.
 */
public class Office {
	public static final double WALL_COST = 3.0; // % power to deploy a wall
	public static final double REBOOT_GAIN = 25.0; // % power restored by a reboot

	public boolean monitorUp = false;
	public int currentCam = 0;
	public double power = 100;
	public boolean blackout = false;
	public boolean windowBroken = false;

	// The player can only do one task at a time (patching, fixing, rebooting)
	public static final int NONE = 0, PATCH = 1, FIX = 2, REBOOT = 3;
	public int busyAction = NONE;
	public double busyTime = 0;

	public boolean isBusy() {
		return busyAction != NONE;
	}

	/** Number of power-using devices (for the "Usage" bar, like the original). */
	public int getUsage(int wallsUp) {
		return 1 + (monitorUp ? 1 : 0) + wallsUp;
	}

	/** % power drained per second. Base load + monitor + each active wall. */
	public double drainPerSecond(int wallsUp) {
		double d = 0.07;
		if (monitorUp) d += 0.12;
		d += 0.08 * wallsUp;
		return d;
	}
}
