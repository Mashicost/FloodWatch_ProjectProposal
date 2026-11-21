import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * FNAF-style UI.
 *  - Office: wide image that pans when the mouse is near the left/right edge.
 *            Pan fully right and the power panel's cover lifts open; click REBOOT.
 *  - Cameras: hover the bar at the bottom of the screen to flip the monitor up/down (animated).
 *            Click a camera on the map to switch to it (its cone lights up).
 *  - F11 toggles full screen, ESC leaves it.
 *
 * Everything is drawn on a fixed LOGICAL canvas (W x H) scaled to the window (letterboxed).
 * Talks to FloodEngine only through its public getters/actions (encapsulation).
 */
public class Main extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final boolean START_FULLSCREEN = false;

	// ---- Logical canvas ----
	private static final int W = 1280, H = 720;
	private static final int FRAME_INSET = 26; // white camera frame
	private static final int RIGHT_EDGE = W - 52; // right-aligned HUD text

	// ---- Office panning ----
	private static final double OFFICE_MIN_WIDTH = 1.25; // zoom just enough to allow panning
	private static final double OFFICE_V_ANCHOR = 0.6;
	private static final int PANEL_ROOM = 340; // extra dark wall to the right of the art that holds the power panel
	private static final double PAN_SPEED = 800;
	private static final double EDGE = 0.25;

	// ---- Animations (seconds) ----
	private static final double FLIP_TIME = 0.32;
	private static final double PANEL_TIME = 0.45;
	private static final double PANEL_OPEN_ZONE = 80; // px from the right end that opens the panel

	// ---- Monitor flip ----
	private static final double FLIP_COOLDOWN = 0.6;
	private final Rectangle flipRect = new Rectangle(W / 2 - 290, H - 62, 580, 48);

	// ---- Camera map (hotspot centres in map image pixel coordinates, 362x464) ----
	private static final int MAP_IMG_W = 362, MAP_IMG_H = 464, MAP_DRAW_H = 320, SPOT_R = 26;
	private static final int[][] CAM_SPOTS = { { 140, 185 }, { 217, 185 }, { 40, 235 }, { 143, 275 }, { 217, 275 } };
	private final double mapScale = MAP_DRAW_H / (double) MAP_IMG_H;
	private final int mapX = W - (int) (MAP_IMG_W * mapScale) - 40;
	private final int mapY = H - MAP_DRAW_H - 80;

	// ---- On-screen buttons ----
	private final Rectangle wallRect = new Rectangle(55, H - 150, 200, 38);
	private final Rectangle fixRect = new Rectangle(265, H - 150, 170, 38);
	private final Rectangle patchRect = new Rectangle(55, H - 150, 230, 38);
	private final Rectangle restartRect = new Rectangle(W / 2 - 110, H / 2 + 80, 220, 44);

	private FloodEngine game = new FloodEngine();
	private final BufferedImage officeImg, mapFallback, jumpscare;
	private final BufferedImage[] camImages, mapImages; // mapImages[i] = map with camera i lit
	private final BufferedImage[] flipFrames; // optional pre-rendered monitor flip (flip0.png ...)
	private final StaticOverlay staticOverlay = new StaticOverlay();
	private final Animation flipAnim = new Animation(FLIP_TIME, Animation.Easing.EASE_IN_OUT);
	private final Animation panelAnim = new Animation(PANEL_TIME, Animation.Easing.EASE_OUT);
	private final BufferedImage flipBuf = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

	private final double officeDrawW, officeDrawH;
	private final double maxScroll;
	private double scrollX;
	private int mouseX = W / 2, mouseY = H / 2; // logical coordinates
	private boolean wasInFlip = false;
	private double flipCooldown = 0;
	private double clock = 0; // for blinking things
	private long lastNanos = System.nanoTime();

	// window -> logical canvas transform
	private double viewScale = 1, viewOffX = 0, viewOffY = 0;
	private static boolean fullscreen = false;

	public Main() {
		setPreferredSize(new Dimension(W, H));
		setLayout(null);
		setBackground(Color.BLACK);
		UI.loadFont("font.ttf"); // optional custom pixel font

		officeImg = loadAny("office", true);
		mapFallback = loadAny("map", true);
		jumpscare = loadAny("jump", false);
		camImages = new BufferedImage[game.getAreaCount()];
		mapImages = new BufferedImage[game.getAreaCount()];
		for (int i = 0; i < camImages.length; i++) {
			camImages[i] = loadAny(stripExt(game.getArea(i).getImageFile()), true);
			mapImages[i] = loadAny("map" + (i + 1), false);
		}
		flipFrames = loadSequence("flip");
		BufferedImage[] staticFrames = loadSequence("static");
		if (staticFrames.length > 0) staticOverlay.setFrames(staticFrames);

		double iw = officeImg != null ? officeImg.getWidth() : 1920;
		double ih = officeImg != null ? officeImg.getHeight() : 1080;
		double scale = Math.max(H / ih, (W * OFFICE_MIN_WIDTH) / iw);
		officeDrawW = iw * scale;
		officeDrawH = ih * scale;
		maxScroll = Math.max(0, officeDrawW + PANEL_ROOM - W);
		scrollX = maxScroll / 2;

		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseMoved(MouseEvent e) {
				updateView();
				mouseX = toLogicalX(e.getX());
				mouseY = toLogicalY(e.getY());
			}

			public void mouseDragged(MouseEvent e) {
				mouseMoved(e);
			}
		});
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				updateView();
				handleClick(toLogicalX(e.getX()), toLogicalY(e.getY()));
			}
		});
		bindKeys();

		new Timer(16, e -> step()).start(); // ~60 fps
	}

	// ================= Loading =================
	/** Tries base.png, base.jpg, base.jpeg so you can mix formats freely. */
	private BufferedImage loadAny(String base, boolean warn) {
		String[] exts = { ".png", ".jpg", ".jpeg" };
		for (String ext : exts) {
			try {
				java.io.InputStream in = ResourceLoader.load(base + ext);
				if (in == null) continue;
				BufferedImage img = ImageIO.read(in);
				in.close();
				if (img != null) return img;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (warn) System.out.println("NOT FOUND (.png/.jpg): " + base);
		return null;
	}

	/** Loads prefix0, prefix1, ... until one is missing. */
	private BufferedImage[] loadSequence(String prefix) {
		List<BufferedImage> list = new ArrayList<BufferedImage>();
		for (int i = 0; i < 300; i++) {
			BufferedImage f = loadAny(prefix + i, false);
			if (f == null) break;
			list.add(f);
		}
		if (!list.isEmpty()) System.out.println("Loaded " + list.size() + " '" + prefix + "' frames.");
		return list.toArray(new BufferedImage[0]);
	}

	private String stripExt(String file) {
		int dot = file.lastIndexOf('.');
		return dot < 0 ? file : file.substring(0, dot);
	}

	// ================= View transform (full screen / resizing) =================
	private void updateView() {
		viewScale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
		if (viewScale <= 0) viewScale = 1;
		viewOffX = (getWidth() - W * viewScale) / 2;
		viewOffY = (getHeight() - H * viewScale) / 2;
	}

	private int toLogicalX(int sx) {
		return (int) ((sx - viewOffX) / viewScale);
	}

	private int toLogicalY(int sy) {
		return (int) ((sy - viewOffY) / viewScale);
	}

	private void toggleFullscreen() {
		Window win = SwingUtilities.getWindowAncestor(this);
		if (win instanceof JFrame) setFullscreen((JFrame) win, !fullscreen);
	}

	private static void setFullscreen(JFrame f, boolean on) {
		GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		f.dispose(); // must be hidden to change decorations
		f.setUndecorated(on);
		if (on) {
			f.setResizable(false);
			dev.setFullScreenWindow(f);
		} else {
			dev.setFullScreenWindow(null);
			f.setResizable(true);
			f.pack();
			f.setLocationRelativeTo(null);
			f.setVisible(true);
		}
		fullscreen = on;
		f.requestFocus();
	}

	// ================= Game loop =================
	private void step() {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		clock += dt;

		updateView();
		game.tick(dt);
		staticOverlay.update(dt);
		updateAnimations(dt);
		flipCooldown = Math.max(0, flipCooldown - dt);
		updatePan(dt);
		updateFlip();
		repaint();
	}

	/** The engine decides the state; the animations just chase it smoothly. */
	private void updateAnimations(double dt) {
		flipAnim.setTarget(game.isMonitorUp());
		flipAnim.update(dt);
		boolean atPanel = !game.isMonitorUp() && scrollX >= maxScroll - PANEL_OPEN_ZONE;
		panelAnim.setTarget(atPanel);
		panelAnim.update(dt);
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

	private void updateFlip() {
		boolean inside = flipRect.contains(mouseX, mouseY);
		if (inside && !wasInFlip && flipCooldown <= 0 && !game.isOver()) flipMonitor();
		wasInFlip = inside;
	}

	private void flipMonitor() {
		if (game.toggleMonitor()) {
			flipCooldown = FLIP_COOLDOWN;
			if (game.isMonitorUp()) staticOverlay.pulse(0.25);
		}
	}

	private void selectCam(int i) {
		if (i != game.getCurrentCam() && game.selectCam(i)) staticOverlay.pulse(0.3);
	}

	private void restart() {
		game = new FloodEngine();
		scrollX = maxScroll / 2;
		flipAnim.setTarget(false, true);
		panelAnim.setTarget(false, true);
	}

	// ================= Input =================
	private Rectangle2D.Double panelRect() { // reboot panel in office WORLD coordinates
		return new Rectangle2D.Double(officeDrawW + 40, 250, 260, 300);
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
		if (game.isOver()) {
			if (restartRect.contains(x, y)) restart();
			return;
		}
		if (game.isMonitorUp() && flipAnim.isOpen()) {
			if (wallRect.contains(x, y)) game.deployWall();
			else if (fixRect.contains(x, y)) game.fixPipe();
			else {
				int cam = hitCamSpot(x, y);
				if (cam >= 0) selectCam(cam);
			}
		} else if (!game.isMonitorUp() && flipAnim.isClosed()) {
			if (game.isWindowBroken() && patchRect.contains(x, y)) game.patchWindow();
			else if (panelAnim.isOpen() && panelRect().contains(x + scrollX, y)) game.reboot();
		}
	}

	private void bindKeys() { // keyboard shortcuts, handy for testing
		bindKey("SPACE", () -> flipMonitor());
		bindKey("W", () -> game.deployWall());
		bindKey("F", () -> game.fixPipe());
		bindKey("P", () -> game.patchWindow());
		bindKey("R", () -> game.reboot());
		bindKey("ENTER", () -> {
			if (game.isOver()) restart();
		});
		bindKey("F11", () -> toggleFullscreen());
		bindKey("ESCAPE", () -> {
			if (fullscreen) toggleFullscreen();
		});
		for (int i = 0; i < CAM_SPOTS.length; i++) {
			final int idx = i;
			bindKey(String.valueOf(i + 1), () -> selectCam(idx));
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
	private void quality(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		updateView();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight()); // letterbox bars

		g.translate(viewOffX, viewOffY);
		g.scale(viewScale, viewScale);
		g.clipRect(0, 0, W, H);
		quality(g);

		if (game.isLost()) {
			drawJumpscare(g);
			UI.box(g, restartRect, "RESTART NIGHT");
			return;
		}
		if (game.isWon()) {
			UI.textCenter(g, "6 AM", W / 2, H / 2 - 20, 80, Color.WHITE);
			UI.textCenter(g, "You kept the barangay dry. Night survived!", W / 2, H / 2 + 30, 24, Color.WHITE);
			UI.box(g, restartRect, "RESTART NIGHT");
			return;
		}

		double p = flipAnim.getProgress();
		if (p <= 0) {
			drawOffice(g);
		} else if (p >= 1) {
			drawCameraScreen(g);
		} else if (flipFrames.length > 0) {
			// pre-rendered flip animation: pick the frame that matches the progress
			int idx = Math.min(flipFrames.length - 1, (int) (p * flipFrames.length));
			g.drawImage(flipFrames[idx], 0, 0, W, H, null);
		} else {
			drawOffice(g);
			drawFlipping(g, p);
		}
		drawHud(g);
		UI.flipBar(g, flipRect, flipRect.contains(mouseX, mouseY));
	}

	/**
	 * Procedural monitor flip: the camera screen is rendered to an off-screen image, then drawn as a
	 * trapezoid that rises from the bottom edge. The top edge is narrower while the screen is tilted
	 * back, which gives a cheap perspective effect.
	 */
	private void drawFlipping(Graphics2D g, double p) {
		g.setColor(new Color(0, 0, 0, (int) (150 * p))); // office dims as the monitor rises
		g.fillRect(0, 0, W, H);

		Graphics2D bg = flipBuf.createGraphics();
		quality(bg);
		drawCameraScreen(bg);
		bg.dispose();

		double h = H * p; // projected height of the screen
		double topScale = 0.55 + 0.45 * p; // width of the top edge relative to the bottom
		int strip = 4;
		for (int sy = 0; sy < H; sy += strip) {
			double t0 = sy / (double) H, t1 = Math.min(H, sy + strip) / (double) H;
			int dy0 = (int) Math.round(H - h * (1 - t0));
			int dy1 = (int) Math.round(H - h * (1 - t1));
			if (dy1 <= dy0) continue;
			int dw = (int) (W * (topScale + (1 - topScale) * t0));
			int dx = (W - dw) / 2;
			g.drawImage(flipBuf, dx, dy0, dx + dw, dy1, 0, sy, W, Math.min(H, sy + strip), null);
		}
	}

	// ---------- Office (pannable) ----------
	private void drawOffice(Graphics2D g) {
		int x0 = (int) -scrollX;
		int y0 = (int) (-(officeDrawH - H) * OFFICE_V_ANCHOR);
		if (officeImg != null) {
			g.drawImage(officeImg, x0, y0, (int) officeDrawW, (int) officeDrawH, null);
		} else {
			g.setPaint(new GradientPaint(0, 0, new Color(15, 20, 28), 0, H, new Color(35, 40, 50)));
			g.fillRect(0, 0, W, H);
		}
		// dark alcove to the right of the art, where the power panel lives
		int edge = x0 + (int) officeDrawW;
		g.setPaint(new GradientPaint(edge - 90, 0, new Color(30, 32, 36, 0), edge, 0, new Color(30, 32, 36)));
		g.fillRect(edge - 90, 0, 90, H);
		g.setPaint(new GradientPaint(edge, 0, new Color(30, 32, 36), edge + PANEL_ROOM, 0, new Color(12, 13, 16)));
		g.fillRect(edge, 0, PANEL_ROOM + 2, H);

		int waterH = (int) (H * 0.7 * game.getWaterLevel() / 100.0);
		g.setColor(new Color(40, 110, 200, 130));
		g.fillRect(0, H - waterH, W, waterH);

		UI.vignette(g, W, H, 170);
		if (game.isBlackout()) {
			g.setColor(new Color(0, 0, 0, 215));
			g.fillRect(0, 0, W, H);
		}
		drawRebootPanel(g, x0);

		if (game.isWindowBroken()) {
			UI.textCenter(g, "WINDOW SHATTERED - rain is blowing in", W / 2, 185, 22, new Color(255, 70, 70));
			UI.box(g, patchRect, "PATCH WINDOW (P)", !game.isBusy());
		}
		if (game.isBlackout()) {
			UI.textCenter(g, "BLACKOUT - PAN RIGHT TO THE POWER PANEL", W / 2, 225, 24, new Color(255, 70, 70));
		}
		if (game.getTask() == Office.Task.PATCH) {
			UI.textCenter(g, "PATCHING WINDOW... " + (int) Math.ceil(game.getTaskTimeLeft()) + "s", W / 2, H / 2, 26, Color.ORANGE);
		}
		// edge arrows show which way you can pan
		Color arrow = new Color(255, 255, 255, 120);
		if (scrollX > 1) UI.text(g, "\u25C0", 14, H / 2, 56, arrow);
		if (scrollX < maxScroll - 1) UI.text(g, "\u25B6", W - 70, H / 2, 56, arrow);
	}

	/** The power panel: a metal cover lifts away (panelAnim) when you pan to it. */
	private void drawRebootPanel(Graphics2D g, int x0) {
		Rectangle2D.Double pr = panelRect();
		int px = (int) (pr.x + x0), py = (int) pr.y, pw = (int) pr.width, ph = (int) pr.height;
		if (px > W || px + pw < 0) return; // off-screen
		double p = panelAnim.getProgress();
		boolean needed = game.isBlackout();
		boolean rebooting = game.getTask() == Office.Task.REBOOT;

		g.setColor(new Color(18, 20, 24));
		g.fillRect(px, py, pw, ph);

		if (p > 0.15) { // contents fade in as the cover lifts
			Composite old = g.getComposite();
			float a = (float) Math.min(1, (p - 0.15) / 0.5);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
			UI.text(g, "POWER PANEL", px + 28, py + 40, 24, Color.WHITE);
			UI.text(g, "Power left: " + (int) Math.ceil(game.getPower()) + "%", px + 28, py + 70, 17, Color.WHITE);

			Rectangle btn = new Rectangle(px + 28, py + 90, pw - 56, 80);
			boolean ok = !game.isBusy();
			g.setColor(rebooting ? new Color(90, 70, 20) : needed ? new Color(185, 30, 30) : new Color(50, 90, 50));
			if (!ok && !rebooting) g.setColor(Color.DARK_GRAY);
			g.fillRect(btn.x, btn.y, btn.width, btn.height);
			g.setColor(Color.WHITE);
			g.drawRect(btn.x, btn.y, btn.width, btn.height);
			UI.textCenter(g, rebooting ? "REBOOTING" : "REBOOT", btn.x + btn.width / 2, btn.y + 50, 24, Color.WHITE);

			// segmented progress bar (FNAF 3 style)
			double frac = rebooting ? 1 - game.getTaskTimeLeft() / FloodEngine.REBOOT_TIME : 0;
			int segs = 10, sw = (pw - 56) / segs;
			for (int s = 0; s < segs; s++) {
				g.setColor(s < Math.round(frac * segs) ? new Color(235, 205, 20) : new Color(50, 50, 50));
				g.fillRect(px + 28 + s * sw, py + 188, sw - 3, 20);
			}
			UI.text(g, "+25% power", px + 28, py + 242, 16, Color.WHITE);
			UI.text(g, "flood rises faster", px + 28, py + 264, 16, Color.WHITE);
			UI.text(g, "while rebooting", px + 28, py + 284, 16, Color.WHITE);
			g.setComposite(old);
		}

		// the cover plate
		int coverH = (int) Math.ceil(ph * (1 - p));
		if (coverH > 0) {
			g.setPaint(new GradientPaint(0, py, new Color(95, 98, 105), 0, py + ph, new Color(55, 58, 64)));
			g.fillRect(px, py, pw, coverH);
			g.setColor(new Color(20, 20, 20));
			g.fillRect(px, py + coverH - 5, pw, 5); // lower lip
			if (coverH > 70) {
				g.setColor(new Color(30, 30, 30));
				for (int i = 0; i < 4; i++) g.fillOval(px + (i % 2 == 0 ? 10 : pw - 22), py + 10 + (i / 2) * (coverH - 40), 12, 12);
				UI.textCenter(g, "MAINTENANCE", px + pw / 2, py + coverH / 2 + 6, 20, new Color(235, 205, 20));
			}
		}

		boolean blink = needed && ((int) (clock * 3) % 2 == 0);
		g.setColor(blink || (needed && p > 0.9) ? Color.RED : new Color(150, 150, 150));
		g.setStroke(new BasicStroke(4));
		g.drawRect(px, py, pw, ph);
		g.setStroke(new BasicStroke(1));
	}

	// ---------- Cameras ----------
	private void drawCameraScreen(Graphics2D g) {
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
			g.fillRect(0, H - 250, W, 24);
			UI.text(g, "FLOOD WALL  " + (int) a.getWallTimeLeft() + "s", 55, H - 232, 16, Color.BLACK);
		}

		// Security-camera static: light grain on a live feed, solid static on a dead one.
		if (dead) staticOverlay.draw(g, W, H, 1.0f);
		else staticOverlay.draw(g, W, H);

		if (dead) {
			UI.textCenter(g, a.isFlooded() ? "CAMERA LOST - AREA FLOODED" : "NO POWER", W / 2, H / 2, 30, Color.WHITE);
		} else if (game.getTask() == Office.Task.FIX) {
			UI.textCenter(g, "REPAIRING PIPE... " + (int) Math.ceil(game.getTaskTimeLeft()) + "s", W / 2, H / 2, 28, Color.ORANGE);
		} else if (a.isPipeBurst()) {
			UI.textCenter(g, "PIPE BURST - FIX IT", W / 2, H / 2, 28, new Color(255, 60, 60));
		}

		UI.frameBorder(g, W, H, FRAME_INSET);
		if ((int) (clock * 1.5) % 2 == 0) { // blinking red REC dot
			g.setColor(new Color(220, 20, 20));
			g.fillOval(410, 52, 24, 24);
		}

		// area name above the map, like "Show Stage" in FNAF 1
		UI.textRight(g, a.getName(), RIGHT_EDGE, mapY - 44, 34, Color.WHITE);
		UI.textRight(g, "FLOOD " + (int) a.getLevel() + "%  -  " + a.getTerrainLabel(), RIGHT_EDGE, mapY - 14, 18,
				new Color(150, 200, 255));

		boolean act = !game.isBusy() && !game.isBlackout();
		UI.box(g, wallRect, "FLOOD WALL (W)", act && !a.isFlooded() && !a.hasWall());
		UI.box(g, fixRect, "FIX PIPE (F)", act && a.isPipeBurst());
		drawMap(g);
	}

	private void drawMap(Graphics2D g) {
		int cur = game.getCurrentCam();
		boolean hasLit = mapImages[cur] != null;
		BufferedImage m = hasLit ? mapImages[cur] : mapFallback;
		int mw = (int) (MAP_IMG_W * mapScale), mh = MAP_DRAW_H;
		g.setColor(new Color(0, 0, 0, 150));
		g.fillRect(mapX - 6, mapY - 6, mw + 12, mh + 12);
		if (m != null) g.drawImage(m, mapX, mapY, mw, mh, null);
		if (!hasLit && cur < CAM_SPOTS.length) { // no per-camera lit map yet: ring the active camera
			double cx = mapX + CAM_SPOTS[cur][0] * mapScale;
			double cy = mapY + CAM_SPOTS[cur][1] * mapScale;
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2));
			int r = (int) (SPOT_R * mapScale);
			g.drawOval((int) cx - r, (int) cy - r, 2 * r, 2 * r);
			g.setStroke(new BasicStroke(1));
		}
	}

	// ---------- HUD (shared by office and cameras) ----------
	private void drawHud(Graphics2D g) {
		// top-centre: water level as a battery-style gauge (FNAF 2 homage)
		UI.battery(g, W / 2 - 150, 44, 96, 40, game.getWaterLevel() / 100.0, new Color(60, 140, 230));
		UI.text(g, "WATER " + (int) game.getWaterLevel() + "%", W / 2 - 32, 72, 20, Color.WHITE);

		// top-right: clock and night
		UI.textRight(g, game.getClockText(), RIGHT_EDGE, 88, 48, Color.WHITE);
		UI.textRight(g, "Night 1", RIGHT_EDGE, 122, 22, Color.WHITE);

		// bottom-left: power and usage
		int pw = (int) Math.ceil(game.getPower());
		Color pc = pw <= 20 && ((int) (clock * 3) % 2 == 0) ? new Color(255, 70, 70) : Color.WHITE;
		UI.text(g, "Power left: " + pw + "%", 55, H - 78, 26, pc);
		UI.text(g, "Usage:", 55, H - 42, 26, Color.WHITE);
		UI.usageBars(g, 175, H - 66, game.getUsage());

		// alerts appear as a bordered box at the top centre (like the MUTE CALL button)
		if (game.hasAlert()) {
			String s = game.getAlert();
			int w = UI.textWidth(g, s, 20) + 36;
			UI.box(g, new Rectangle(W / 2 - w / 2, 104, w, 40), s);
		}
	}

	private void drawJumpscare(Graphics2D g) {
		if (jumpscare != null) g.drawImage(jumpscare, 0, 0, W, H, null);
		g.setColor(new Color(150, 0, 0, 160));
		g.fillRect(0, 0, W, H);
		UI.textCenter(g, "YOU DROWNED", W / 2, H / 2 - 20, 70, Color.WHITE);
		UI.textCenter(g, "An alligator found you in the flood...", W / 2, H / 2 + 30, 24, Color.WHITE);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame f = new JFrame("FloodWatch");
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.setContentPane(new Main());
			f.pack();
			f.setLocationRelativeTo(null);
			f.setVisible(true);
			if (START_FULLSCREEN) setFullscreen(f, true);
		});
	}
}