import java.util.Random;

/**
 * INHERITANCE / POLYMORPHISM: abstract base for random storm events.
 * The engine holds a List<Hazard> and treats every event the same way;
 * each subclass supplies its own canTrigger() and trigger() behaviour.
 */
public abstract class Hazard {
	private final double chancePerSecond;

	protected Hazard(double chancePerSecond) {
		this.chancePerSecond = chancePerSecond;
	}

	public double getChancePerSecond() {
		return chancePerSecond;
	}

	/** Can this event happen right now? */
	public abstract boolean canTrigger(FloodEngine engine);

	/** Apply the event and return the alert text to show the player. */
	public abstract String trigger(FloodEngine engine, Random rng);
}
