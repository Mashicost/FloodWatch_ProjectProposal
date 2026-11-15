import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * FNAF-style UI.
 *  - Office: wide image that pans when the mouse is near the left/right edge.
 *            Pan fully right to reach the reboot panel and click it.
 *  - Cameras: hover the bar at the bottom of the screen to flip the monitor up/down.
 *            Click a camera on the map to switch to it.
 * Talks to FloodEngine only through its public getters/actions (encapsulation).
 */
public class Main extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int W = 1107, H = 614;
	private static final Font MONO = new Font(Font.MONOSPACED, Font.BOLD, 22);
	private static final Font SMALL = new Font(Font.MONOSPACED, Font.BOLD, 16);
	private static final Font BIG = new Font(Font.MONOSPACED, Font.BOLD, 56);

	// ---- Office panning ----
	private static final double OFFICE_ZOOM = 1.5; // 1.0 = no panning; use wider art and lower this
	private static final double PAN_SPEED = 700; // pixels per second at the very edge
	private static final double EDGE = 0.25; // outer 25% of the screen scrolls

	// ---- Monitor flip ----
	private static final double FLIP_COOLDOWN = 0.6;
	private final Rectangle flipRect = new Rectangle(W / 2 - 180, H - 44, 360, 44);

	// ---- Camera map (hotspot centres in map.png pixel coordinates, 362x464) ----
	private static final int MAP_IMG_W = 362, MAP_IMG_H = 464, MAP_DRAW_H = 270, SPOT_R = 26;
	private static final int[][] CAM_SPOTS = { { 140, 185 }, { 217, 185 }, { 40, 235 }, { 143, 275 }, { 217, 275 } };
	private final double mapScale = MAP_DRAW_H / (double) MAP_IMG_H;
	private final int mapX = W - (int) (MAP_IMG_W * mapScale) - 15;
	private final int mapY = H - MAP_DRAW_H - 60;

	// ---- On-screen buttons ----
	private final Rectangle wallRect = new Rectangle(20, H - 140, 190, 36);
	private final Rectangle fixRect = new Rectangle(20, H - 96, 190, 36);
	private final Rectangle patchRect = new Rectangle(20, H - 96, 220, 36);

	private FloodEngine game = new FloodEngine();
	private final BufferedImage officeImg, mapFallback;
	private final BufferedImage[] camImages, mapImages; // mapImages[i] = map with camera i lit (optional)
	private final BufferedImage jumpscare;
	private final Random rng = new Random();
	private final JButton restartBtn = new JButton("Restart Night");

	private final double officeDrawW, officeDrawH;
	private final double maxScroll;
	private double scrollX;
	private int mouseX = W / 2, mouseY = H / 2;
	private boolean wasInFlip = false;
	private double flipCooldown = 0;
	private long lastNanos = System.nanoTime();

	public Main() {
		setPreferredSize(new Dimension(W, H));
		setLayout(null);

		officeImg = loadImage("office.jpg");
		mapFallback = loadImage("map.jpg");
		jumpscare = loadImage("jump.jpg");
		camImages = new BufferedImage[game.getAreaCount()];
		mapImages = new BufferedImage[game.getAreaCount()];
		for (int i = 0; i < camImages.length; i++) {
			camImages[i] = loadImage(game.getArea(i).getImageFile());
			mapImages[i] = loadQuiet("map" + (i + 1) + ".png"); // optional per-camera lit maps
		}

		double iw = officeImg != null ? officeImg.getWidth() : 1920;
		double ih = officeImg != null ? officeImg.getHeight() : 1080;
		double scale = (H / ih) * OFFICE_ZOOM;
		officeDrawW = iw * scale;
		officeDrawH = ih * scale;
		maxScroll = Math.max(0, officeDrawW - W);
		scrollX = maxScroll / 2;

		restartBtn.setBounds(W / 2 - 80, H / 2 + 80, 160, 36);
		restartBtn.setVisible(false);
		restartBtn.addActionListener(e -> {
			game = new FloodEngine();
			scrollX = maxScroll / 2;
		});
		add(restartBtn);

		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseMoved(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();
			}

			public void mouseDragged(MouseEvent e) {
				mouseMoved(e);
			}
		});
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				handleClick(e.getX(), e.getY());
			}
		});
		bindKeys();

		new Timer(16, e -> step()).start(); // ~60 fps
	}

	// ================= Loading =================
	private BufferedImage loadImage(String name) {
		try {
			java.io.InputStream in = ResourceLoader.load(name);

			if (in == null) {
				System.out.println("NOT FOUND: " + name);
				return null;
			}

			BufferedImage img = ImageIO.read(in);

			System.out.println("File: " + name);
			System.out.println("Image: " + img);

			in.close();

			return img;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/** Same as loadImage but silent (for optional files). */
	private BufferedImage loadQuiet(String name) {
		try {
			java.io.InputStream in = ResourceLoader.load(name);
			return in == null ? null : ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	// ================= Game loop =================
	private void step() {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;

		game.tick(dt);
		flipCooldown = Math.max(0, flipCooldown - dt);
		updatePan(dt);
		updateFlip();
		restartBtn.setVisible(game.isOver());
		repaint();
	}

	/** Mouse near the left/right edge scrolls the office. Faster the closer to the edge. */
	private void updatePan(double dt) {
		if (game.isMonitorUp() || game.isOver()) return;
		double edgePx = W * EDGE;
		if (mouseX < edgePx) {
			scrollX -= PAN_SPEED * dt * (1 - mouseX / edgePx);
		} else if (mouseX > W - edgePx) {
			scrollX += PAN_SPEED * dt * (1 - (W - mouseX) / edgePx);
		}
		scrollX = Math.max(0, Math.min(maxScroll, scrollX));
	}

	/** Moving the mouse onto the bottom bar flips the monitor (with a short cooldown). */
	private void updateFlip() {
		boolean inside = flipRect.contains(mouseX, mouseY);
		if (inside && !wasInFlip && flipCooldown <= 0 && !game.isOver()) {
			if (game.toggleMonitor()) flipCooldown = FLIP_COOLDOWN;
		}
		wasInFlip = inside;
	}

	// ================= Input =================
	private Rectangle2D.Double panelRect() { // reboot panel in office WORLD coordinates
		return new Rectangle2D.Double(officeDrawW - 330, 235, 260, 295);
	}

	private int hitCamSpot(int x, int y) {
		for (int i = 0; i < CAM_SPOTS.length && i < game.getAreaCount(); i++) {
			double cx = mapX + CAM_SPOTS[i][0] * mapScale;
			double cy = mapY + CAM_SPOTS[i][1] * mapScale;
			if (Math.hypot(x - cx, y - cy) <= SPOT_R * mapScale) return i;
		}
		return -1;
	}

	private void handleClick(int x, int y) {
		if (game.isOver()) return;
		if (game.isMonitorUp()) {
			if (wallRect.contains(x, y)) game.deployWall();
			else if (fixRect.contains(x, y)) game.fixPipe();
			else {
				int cam = hitCamSpot(x, y);
				if (cam >= 0) game.selectCam(cam);
			}
		} else {
			if (game.isWindowBroken() && patchRect.contains(x, y)) game.patchWindow();
			else if (panelRect().contains(x + scrollX, y)) game.reboot();
		}
	}

	private void bindKeys() { // keyboard shortcuts, handy for testing
		bindKey("SPACE", () -> game.toggleMonitor());
		bindKey("W", () -> game.deployWall());
		bindKey("F", () -> game.fixPipe());
		bindKey("P", () -> game.patchWindow());
		bindKey("R", () -> game.reboot());
		for (int i = 0; i < 5; i++) {
			final int idx = i;
			bindKey(String.valueOf(i + 1), () -> game.selectCam(idx));
		}
	}

	private void bindKey(String key, Runnable r) {
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
		getActionMap().put(key, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent e) {
				r.run();
			}
		});
	}

	// ================= Drawing =================
	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());

		if (game.isLost()) {
			drawJumpscare(g);
			return;
		}
		if (game.isWon()) {
			g.setColor(Color.WHITE);
			g.setFont(BIG);
			center(g, "6 AM", H / 2 - 20);
			g.setFont(MONO);
			center(g, "You kept the barangay dry. Night survived!", H / 2 + 30);
			return;
		}
		if (game.isMonitorUp()) drawCamera(g);
		else drawOffice(g);
		drawHud(g);
		drawFlipBar(g);
	}

	// ---------- Office (pannable) ----------
	private void drawOffice(Graphics2D g) {
		int x0 = (int) -scrollX;
		int y0 = (int) ((H - officeDrawH) / 2);
		if (officeImg != null) {
			g.drawImage(officeImg, x0, y0, (int) officeDrawW, (int) officeDrawH, null);
		} else {
			g.setPaint(new GradientPaint(0, 0, new Color(15, 20, 28), 0, H, new Color(35, 40, 50)));
			g.fillRect(0, 0, W, H);
		}
		int waterH = (int) (H * 0.7 * game.getWaterLevel() / 100.0);
		g.setColor(new Color(40, 110, 200, 130));
		g.fillRect(0, H - waterH, W, waterH);

		if (game.isBlackout()) {
			g.setColor(new Color(0, 0, 0, 215));
			g.fillRect(0, 0, W, H);
		}
		drawRebootPanel(g, x0);

		g.setFont(MONO);
		if (game.isWindowBroken()) {
			g.setColor(Color.RED);
			g.drawString("WINDOW SHATTERED - rain is blowing in", 55, 125);
			drawButton(g, patchRect, "PATCH WINDOW (P)", !game.isBusy());
		}
		if (game.isBlackout()) {
			g.setColor(Color.RED);
			center(g, "BLACKOUT - PAN RIGHT TO THE POWER PANEL", 60);
		}
		g.setColor(Color.ORANGE);
		if (game.getTask() == Office.Task.REBOOT) {
			center(g, "REBOOTING... " + (int) Math.ceil(game.getTaskTimeLeft()) + "s (flood rising faster!)", H / 2);
		} else if (game.getTask() == Office.Task.PATCH) {
			center(g, "PATCHING WINDOW... " + (int) Math.ceil(game.getTaskTimeLeft()) + "s", H / 2);
		}
		// edge arrows show which way you can pan
		g.setColor(new Color(255, 255, 255, 120));
		g.setFont(BIG);
		if (scrollX > 1) g.drawString("\u25C0", 10, H / 2);
		if (scrollX < maxScroll - 1) g.drawString("\u25B6", W - 60, H / 2);
	}

	private void drawRebootPanel(Graphics2D g, int x0) {
		Rectangle2D.Double p = panelRect();
		int px = (int) (p.x + x0), py = (int) p.y, pw = (int) p.width, ph = (int) p.height;
		if (px > W || px + pw < 0) return; // off-screen
		boolean needed = game.isBlackout();
		g.setColor(new Color(20, 22, 26));
		g.fillRect(px, py, pw, ph);
		g.setColor(needed ? Color.RED : new Color(150, 150, 150));
		g.setStroke(new BasicStroke(4));
		g.drawRect(px, py, pw, ph);
		g.setStroke(new BasicStroke(1));
		g.setFont(MONO);
		g.setColor(Color.WHITE);
		g.drawString("POWER PANEL", px + 30, py + 40);
		g.setFont(SMALL);
		g.drawString("Power: " + (int) Math.ceil(game.getPower()) + "%", px + 30, py + 75);
		// big reboot button
		boolean ok = !game.isBusy();
		g.setColor(needed ? new Color(190, 30, 30) : new Color(60, 90, 60));
		if (!ok) g.setColor(Color.DARK_GRAY);
		g.fillRect(px + 30, py + 110, pw - 60, 90);
		g.setColor(Color.WHITE);
		g.setFont(MONO);
		g.drawString("REBOOT", px + 70, py + 165);
		g.setFont(SMALL);
		g.drawString("+25% power", px + 55, py + 235);
		g.drawString("flood rises faster", px + 40, py + 260);
		g.drawString("while rebooting", px + 50, py + 280);
	}

	// ---------- Cameras ----------
	private void drawCamera(Graphics2D g) {
		Area a = game.getCurrentArea();
		BufferedImage img = camImages[game.getCurrentCam()];
		boolean dead = a.isFlooded() || game.isBlackout();
		if (img != null) {
			g.drawImage(img, 0, 0, W, H, null);
		} else {
			g.setPaint(new GradientPaint(0, 0, new Color(10, 15, 30), 0, H, new Color(40, 50, 60)));
			g.fillRect(0, 0, W, H);
		}
		int waterH = (int) (H * 0.55 * a.getLevel() / 100.0);
		g.setColor(new Color(40, 110, 200, 120));
		g.fillRect(0, H - waterH, W, waterH);

		if (a.hasWall()) {
			g.setColor(new Color(170, 170, 170, 230));
			g.fillRect(0, H - 220, W, 24);
			g.setColor(Color.BLACK);
			g.setFont(SMALL);
			g.drawString("FLOOD WALL  " + (int) a.getWallTimeLeft() + "s", 20, H - 202);
		}
		g.setFont(MONO);
		if (dead) {
			drawStatic(g, a.isFlooded() ? "CAMERA LOST - AREA FLOODED" : "NO POWER");
		} else if (game.getTask() == Office.Task.FIX) {
			g.setColor(Color.ORANGE);
			center(g, "REPAIRING PIPE... " + (int) Math.ceil(game.getTaskTimeLeft()) + "s", H / 2);
		} else if (a.isPipeBurst()) {
			g.setColor(Color.RED);
			center(g, "PIPE BURST - FIX IT", H / 2);
		}
		g.setColor(new Color(255, 255, 255, 220));
		g.setFont(MONO);
		g.drawString("SECURITY FEED", 55, 85);
		g.drawString("PLAY \u25B6", 55, 120);
		g.drawString("CAM " + (game.getCurrentCam() + 1) + " - " + a.getName(), 55, 160);
		g.drawString(a.getTerrainLabel(), 55, 190);
		g.drawString("FLOOD " + (int) a.getLevel() + "%", 55, H - 230);

		boolean act = !game.isBusy() && !game.isBlackout();
		drawButton(g, wallRect, "FLOOD WALL (W)", act && !a.isFlooded() && !a.hasWall());
		drawButton(g, fixRect, "FIX PIPE (F)", act && a.isPipeBurst());
		drawMap(g);
	}

	private void drawMap(Graphics2D g) {
		int cur = game.getCurrentCam();
		BufferedImage m = mapImages[cur] != null ? mapImages[cur] : mapFallback;
		int mw = (int) (MAP_IMG_W * mapScale), mh = MAP_DRAW_H;
		g.setColor(new Color(0, 0, 0, 150));
		g.fillRect(mapX - 6, mapY - 6, mw + 12, mh + 12);
		if (m != null) g.drawImage(m, mapX, mapY, mw, mh, null);
		if (mapImages[cur] == null) { // no per-camera lit map yet: ring the active camera
			double cx = mapX + CAM_SPOTS[cur][0] * mapScale;
			double cy = mapY + CAM_SPOTS[cur][1] * mapScale;
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2));
			int r = (int) (SPOT_R * mapScale);
			g.drawOval((int) cx - r, (int) cy - r, 2 * r, 2 * r);
			g.setStroke(new BasicStroke(1));
		}
	}

	// ---------- Shared bits ----------
	private void drawButton(Graphics2D g, Rectangle r, String text, boolean enabled) {
		g.setColor(enabled ? new Color(30, 70, 110, 220) : new Color(50, 50, 50, 180));
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(enabled ? Color.WHITE : Color.GRAY);
		g.drawRect(r.x, r.y, r.width, r.height);
		g.setFont(SMALL);
		g.drawString(text, r.x + 12, r.y + 24);
	}

	private void drawFlipBar(Graphics2D g) {
		boolean hot = flipRect.contains(mouseX, mouseY);
		g.setColor(new Color(255, 255, 255, hot ? 90 : 45));
		g.fillRoundRect(flipRect.x, flipRect.y + 6, flipRect.width, flipRect.height, 20, 20);
		g.setColor(new Color(255, 255, 255, 200));
		g.setFont(SMALL);
		center(g, game.isMonitorUp() ? "\u25B2  CLOSE CAMERAS  \u25B2" : "\u25BC  OPEN CAMERAS  \u25BC", H - 14);
	}

	private void drawHud(Graphics2D g) {
		g.setFont(MONO);
		g.setColor(Color.WHITE);
		g.drawString(game.getClockText(), W - 140, 85);
		g.drawString("Power: " + (int) Math.ceil(game.getPower()) + "%", W - 260, 125);
		StringBuilder bars = new StringBuilder();
		for (int i = 0; i < game.getUsage(); i++) bars.append('\u2588');
		g.drawString("Usage: " + bars, W - 260, 155);
		int w = (int) game.getWaterLevel();
		g.drawString("Water: " + w + "%", W - 260, 185);
		g.setColor(new Color(40, 110, 200));
		g.fillRect(W - 260, 195, (int) (200 * w / 100.0), 12);
		g.setColor(Color.WHITE);
		g.drawRect(W - 260, 195, 200, 12);
		if (game.hasAlert()) {
			g.setColor(Color.YELLOW);
			g.setFont(MONO);
			center(g, game.getAlert(), H - 70);
		}
	}

	private void drawStatic(Graphics2D g, String msg) {
		for (int i = 0; i < 900; i++) {
			int v = rng.nextInt(255);
			g.setColor(new Color(v, v, v, 160));
			g.fillRect(rng.nextInt(W), rng.nextInt(H), 6, 3);
		}
		g.setColor(Color.WHITE);
		g.setFont(MONO);
		center(g, msg, H / 2);
	}

	private void drawJumpscare(Graphics2D g) {
		if (jumpscare != null) g.drawImage(jumpscare, 0, 0, W, H, null);
		g.setColor(new Color(150, 0, 0, 160));
		g.fillRect(0, 0, W, H);
		g.setColor(Color.WHITE);
		g.setFont(BIG);
		center(g, "YOU DROWNED", H / 2 - 20);
		g.setFont(MONO);
		center(g, "An alligator found you in the flood...", H / 2 + 30);
	}

	private void center(Graphics2D g, String s, int y) {
		g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, y);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame f = new JFrame("FloodWatch");
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.setContentPane(new Main());
			f.pack();
			f.setResizable(false);
			f.setLocationRelativeTo(null);
			f.setVisible(true);
		});
	}
}