import java.io.InputStream;

/** Unchanged from the FNAF project: loads files from the classpath (Resources/ folder). */
final public class ResourceLoader {
	public static InputStream load(String path) {
		InputStream input = ResourceLoader.class.getResourceAsStream(path);
		if (input == null) {
			input = ResourceLoader.class.getResourceAsStream("/" + path);
		}
		return input;
	}
}
