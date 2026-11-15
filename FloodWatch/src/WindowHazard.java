import java.util.Random;

/** Debris shatters the booth window; rain blows in until it is patched. */
public class WindowHazard extends Hazard {
	public WindowHazard(double chancePerSecond) {
		super(chancePerSecond);
	}

	@Override
	public boolean canTrigger(FloodEngine engine) {
		return !engine.isWindowBroken();
	}

	@Override
	public String trigger(FloodEngine engine, Random rng) {
		engine.setWindowBroken(true);
		return "WINDOW SHATTERED - PATCH IT";
	}
}
