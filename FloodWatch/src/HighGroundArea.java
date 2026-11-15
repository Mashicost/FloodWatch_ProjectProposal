/** INHERITANCE: elevated ground floods 20% slower. */
public class HighGroundArea extends Area {
	public HighGroundArea(String name, String imageFile) {
		super(name, imageFile);
	}

	@Override
	public double getFloodMultiplier() {
		return 0.8;
	}

	@Override
	public String getTerrainLabel() {
		return "HIGH GROUND";
	}
}
