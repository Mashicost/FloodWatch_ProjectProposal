import java.io.InputStream;

public final class ResourceLoader {

	public static InputStream load(String path) {
		return ResourceLoader.class.getResourceAsStream("/" + path);
	}
}