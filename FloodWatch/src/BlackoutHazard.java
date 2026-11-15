import java.util.Random;

/** The storm knocks out the electricity. */
public class BlackoutHazard extends Hazard {
	public BlackoutHazard(double chancePerSecond) {
		super(chancePerSecond);
	}

	@Override
	public boolean canTrigger(FloodEngine engine) {
		return !engine.isBlackout();
	}

	@Override
	public String trigger(FloodEngine engine, Random rng) {
		engine.causeBlackout();
		return "STORM KNOCKED OUT THE POWER - REBOOT REQUIRED";
	}
}
