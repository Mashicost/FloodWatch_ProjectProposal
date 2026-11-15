/** INHERITANCE: low ground collects water, so it floods 20% faster. */
public class LowLyingArea extends Area {
	public LowLyingArea(String name, String imageFile) {
		super(name, imageFile);
	}

	@Override
	public double getFloodMultiplier() {
		return 1.2;
	}

	@Override
	public String getTerrainLabel() {
		return "LOW-LYING";
	}
}
