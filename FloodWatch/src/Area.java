/**
 * One camera-covered location. Replaces the "room" an animatronic stood in.
 * level 0-100 is how flooded the area is.
 */
public class Area {
	public final String name;
	public final String imageFile; // looked up in Resources/
	public final double weight; // how fast this area floods (1.0 = normal)

	public double level = 0;
	public boolean flooded = false; // overflowed -> camera lost
	public double wallTimeLeft = 0; // seconds the flood wall will still hold
	public boolean pipeBurst = false;

	public Area(String name, String imageFile, double weight) {
		this.name = name;
		this.imageFile = imageFile;
		this.weight = weight;
	}

	public boolean wallUp() {
		return wallTimeLeft > 0;
	}
}
