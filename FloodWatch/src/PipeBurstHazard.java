import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Water pressure bursts a pipe in a random, not-yet-flooded area. */
public class PipeBurstHazard extends Hazard {
	public PipeBurstHazard(double chancePerSecond) {
		super(chancePerSecond);
	}

	private List<Area> candidates(FloodEngine engine) {
		List<Area> list = new ArrayList<Area>();
		for (int i = 0; i < engine.getAreaCount(); i++) {
			Area a = engine.getArea(i);
			if (!a.isFlooded() && !a.isPipeBurst()) list.add(a);
		}
		return list;
	}

	@Override
	public boolean canTrigger(FloodEngine engine) {
		return !candidates(engine).isEmpty();
	}

	@Override
	public String trigger(FloodEngine engine, Random rng) {
		List<Area> list = candidates(engine);
		Area a = list.get(rng.nextInt(list.size()));
		a.setPipeBurst(true);
		return "PIPE BURST AT " + a.getName();
	}
}
