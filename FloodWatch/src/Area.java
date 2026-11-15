/**
 * One camera-covered location (base class).
 * ENCAPSULATION: every field is private; outside code can only use the methods below,
 * so an Area can never be put in an invalid state (e.g. level above 100).
 * INHERITANCE: LowLyingArea and HighGroundArea extend this and override
 * getFloodMultiplier() and getTerrainLabel().
 */
public class Area {
	private final String name;
	private final String imageFile; // looked up in Resources/
	private double level = 0; // 0-100
	private boolean flooded = false;
	private double wallTimeLeft = 0;
	private boolean pipeBurst = false;

	public Area(String name, String imageFile) {
		this.name = name;
		this.imageFile = imageFile;
	}

	// ---- Getters ----
	public String getName() {
		return name;
	}

	public String getImageFile() {
		return imageFile;
	}

	public double getLevel() {
		return level;
	}

	public boolean isFlooded() {
		return flooded;
	}

	public boolean hasWall() {
		return wallTimeLeft > 0;
	}

	public double getWallTimeLeft() {
		return wallTimeLeft;
	}

	public boolean isPipeBurst() {
		return pipeBurst;
	}

	// ---- Polymorphic behaviour: subclasses override these ----
	/** How much faster/slower this terrain floods (1.0 = normal). */
	public double getFloodMultiplier() {
		return 1.0;
	}

	public String getTerrainLabel() {
		return "STANDARD";
	}

	// ---- Controlled mutators ----
	public void setPipeBurst(boolean burst) {
		if (!flooded) pipeBurst = burst;
	}

	/** OVERLOADED: add water at the normal rate. Returns true if this just flooded the area. */
	public boolean addWater(double amount) {
		return addWater(amount, 1.0);
	}

	/** OVERLOADED: add water with an extra multiplier (e.g. x3 for a burst pipe). */
	public boolean addWater(double amount, double multiplier) {
		if (flooded) return false;
		level = Math.min(100, level + amount * multiplier * getFloodMultiplier());
		if (level >= 100) {
			flooded = true;
			pipeBurst = false;
			wallTimeLeft = 0;
			return true;
		}
		return false;
	}

	/** OVERLOADED: raise a wall for the given number of seconds. */
	public boolean raiseWall(double seconds) {
		if (flooded || hasWall()) return false;
		wallTimeLeft = seconds;
		return true;
	}

	/** OVERLOADED: raise a wall for the default duration. */
	public boolean raiseWall() {
		return raiseWall(FloodEngine.WALL_DURATION);
	}

	public void tickWall(double dt) {
		if (wallTimeLeft > 0) wallTimeLeft = Math.max(0, wallTimeLeft - dt);
	}

	public void dropWall() {
		wallTimeLeft = 0;
	}

	@Override
	public String toString() {
		return name + " (" + getTerrainLabel() + ", " + (int) level + "%)";
	}
}
