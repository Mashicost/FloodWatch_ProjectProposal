import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Swing UI for FloodWatch. Replaces the old Window class. */
public class Main extends JPanel {
	private static final int W = 1107, H = 614;
	private static final Font MONO = new Font(Font.MONOSPACED, Font.BOLD, 22);
	private static final Font BIG = new Font(Font.MONOSPACED, Font.BOLD, 56);

	private FloodEngine game = new FloodEngine(1.0);
	private final BufferedImage[] camImages;
	private BufferedImage jumpscare;
	private final Random rng = new Random();

	private final JButton[] camButtons = new JButton[6];
	private final JButton monitorBtn = new JButton("Monitor (SPACE)");
	private final JButton wallBtn = new JButton("Flood Wall (W)");
	private final JButton fixBtn = new JButton("Fix Pipe (F)");
	private final JButton patchBtn = new JButton("Patch Window (P)");
	private final JButton rebootBtn = new JButton("Reboot Power (R)");
	private final JButton restartBtn = new JButton("Restart Night");

	public Main() {
		setPreferredSize(new Dimension(W, H));
		setLayout(null);
		camImages = new BufferedImage[game.areas.length];
		for (int i = 0; i < camImages.length; i++) camImages[i] = loadImage(game.areas[i].imageFile);
		jumpscare = loadImage("jump.jpg");
		buildControls();

		new Timer(100, e -> {
			game.tick(0.1);
			updateButtons();
			repaint();
		}).start();
	}

	private BufferedImage loadImage(String name) {
		try {
			java.io.InputStream in = ResourceLoader.load(name);
			return in == null ? null : ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	// ---------- Controls ----------
	private void buildControls() {
		int y = H - 44, x = 10;
		for (int i = 0; i < camButtons.length; i++) {
			final int idx = i;
			camButtons[i] = new JButton("CAM " + (i + 1));
			camButtons[i].setBounds(x, y, 80, 32);
			camButtons[i].addActionListener(e -> game.selectCam(idx));
			add(camButtons[i]);
			x += 84;
		}
		x += 10;
		x = place(wallBtn, x, y, 130, () -> game.deployWall());
		x = place(fixBtn, x, y, 110, () -> game.fixPipe());
		x = place(patchBtn, x, y, 140, () -> game.patchWindow());
		x = place(rebootBtn, x, y, 150, () -> game.reboot());
		place(monitorBtn, x, y, 150, () -> game.toggleMonitor());
		restartBtn.setBounds(W / 2 - 80, H / 2 + 80, 160, 36);
		restartBtn.addActionListener(e -> game = new FloodEngine(1.0));
		add(restartBtn);

		bindKey("SPACE", () -> game.toggleMonitor());
		bindKey("W", () -> game.deployWall());
		bindKey("F", () -> game.fixPipe());
		bindKey("P", () -> game.patchWindow());
		bindKey("R", () -> game.reboot());
		for (int i = 0; i < 6; i++) {
			final int idx = i;
			bindKey(String.valueOf(i + 1), () -> game.selectCam(idx));
		}
	}

	private int place(JButton b, int x, int y, int w, Runnable action) {
		b.setBounds(x, y, w, 32);
		b.setFocusable(false);
		b.addActionListener(e -> action.run());
		add(b);
		return x + w + 4;
	}

	private void bindKey(String key, Runnable r) {
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
		getActionMap().put(key, new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				r.run();
			}
		});
	}

	private void updateButtons() {
		Office o = game.office;
		boolean idle = !game.isOver() && !o.isBusy();
		boolean cams = idle && o.monitorUp && !o.blackout;
		for (JButton b : camButtons) b.setEnabled(idle && o.monitorUp);
		Area cur = game.areas[o.currentCam];
		wallBtn.setEnabled(cams && !cur.flooded && !cur.wallUp());
		fixBtn.setEnabled(cams && cur.pipeBurst);
		patchBtn.setEnabled(idle && o.windowBroken && !o.monitorUp);
		rebootBtn.setEnabled(idle);
		monitorBtn.setEnabled(idle && !o.blackout);
		restartBtn.setVisible(game.isOver());
	}

	// ---------- Drawing ----------
	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());

		if (game.lost) {
			drawJumpscare(g);
			return;
		}
		if (game.won) {
			g.setColor(Color.WHITE);
			g.setFont(BIG);
			center(g, "6 AM", H / 2 - 20);
			g.setFont(MONO);
			center(g, "You kept the barangay dry. Night survived!", H / 2 + 30);
			return;
		}

		if (game.office.monitorUp) drawCamera(g);
		else drawBooth(g);
		drawHud(g);
	}

	private void drawCamera(Graphics2D g) {
		Area a = game.areas[game.office.currentCam];
		boolean dead = a.flooded || game.office.blackout;
		if (camImages[game.office.currentCam] != null) {
			g.drawImage(camImages[game.office.currentCam], 0, 0, W, H, null);
		} else {
			g.setPaint(new GradientPaint(0, 0, new Color(10, 15, 30), 0, H, new Color(40, 50, 60)));
			g.fillRect(0, 0, W, H);
		}
		// flood water rising from the bottom of the frame
		int waterH = (int) (H * 0.55 * a.level / 100.0);
		g.setColor(new Color(40, 110, 200, 120));
		g.fillRect(0, H - waterH, W, waterH);
		// flood wall
		if (a.wallUp()) {
			g.setColor(new Color(170, 170, 170, 230));
			g.fillRect(0, H - 150, W, 24);
			g.setColor(Color.BLACK);
			g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
			g.drawString("FLOOD WALL  " + (int) a.wallTimeLeft + "s", 20, H - 132);
		}
		if (dead) drawStatic(g, a.flooded ? "CAMERA LOST - AREA FLOODED" : "NO POWER");
		else if (game.office.busyAction == Office.FIX) {
			g.setColor(Color.ORANGE);
			g.setFont(MONO);
			center(g, "REPAIRING PIPE... " + (int) Math.ceil(game.office.busyTime) + "s", H / 2);
		} else if (a.pipeBurst) {
			g.setColor(Color.RED);
			g.setFont(MONO);
			center(g, "PIPE BURST - FIX IT (F)", H / 2);
		}
		g.setColor(new Color(255, 255, 255, 220));
		g.setFont(MONO);
		g.drawString("SECURITY FEED", 55, 85);
		g.drawString("PLAY \u25B6", 55, 120);
		g.drawString("CAM " + (game.office.currentCam + 1) + " - " + a.name, 55, 160);
		g.drawString("FLOOD " + (int) a.level + "%", 55, H - 70);
	}

	private void drawBooth(Graphics2D g) {
		g.setPaint(new GradientPaint(0, 0, new Color(15, 20, 28), 0, H, new Color(35, 40, 50)));
		g.fillRect(0, 0, W, H);
		g.setColor(new Color(120, 160, 200, 90));
		for (int i = 0; i < 120; i++) { // rain
			int x = rng.nextInt(W), y = rng.nextInt(H);
			g.drawLine(x, y, x - 6, y + 22);
		}
		int waterH = (int) (H * 0.7 * game.getWaterLevel() / 100.0);
		g.setColor(new Color(40, 110, 200, 130));
		g.fillRect(0, H - waterH, W, waterH);
		g.setColor(Color.WHITE);
		g.setFont(MONO);
		g.drawString("GUARD BOOTH", 55, 85);
		if (game.office.windowBroken) {
			g.setColor(Color.RED);
			g.drawString("WINDOW SHATTERED - rain is blowing in (P)", 55, 125);
		}
		if (game.office.blackout) {
			g.setColor(new Color(0, 0, 0, 200));
			g.fillRect(0, 0, W, H);
			g.setColor(Color.RED);
			center(g, "BLACKOUT - REBOOT POWER (R)", H / 2);
		}
		if (game.office.busyAction == Office.REBOOT) {
			g.setColor(Color.ORANGE);
			center(g, "REBOOTING... " + (int) Math.ceil(game.office.busyTime) + "s (flood rising faster!)", H / 2 + 40);
		} else if (game.office.busyAction == Office.PATCH) {
			g.setColor(Color.ORANGE);
			center(g, "PATCHING WINDOW... " + (int) Math.ceil(game.office.busyTime) + "s", H / 2 + 40);
		}
	}

	private void drawHud(Graphics2D g) {
		Office o = game.office;
		g.setFont(MONO);
		g.setColor(Color.WHITE);
		g.drawString(game.getClockText(), W - 140, 85);
		g.drawString("Power: " + (int) Math.ceil(o.power) + "%", W - 260, 125);
		StringBuilder bars = new StringBuilder();
		for (int i = 0; i < o.getUsage(game.countWalls()); i++) bars.append('\u2588');
		g.drawString("Usage: " + bars, W - 260, 155);
		int w = (int) game.getWaterLevel();
		g.drawString("Water: " + w + "%", W - 260, 185);
		g.setColor(new Color(40, 110, 200));
		g.fillRect(W - 260, 195, (int) (200 * w / 100.0), 12);
		g.setColor(Color.WHITE);
		g.drawRect(W - 260, 195, 200, 12);
		if (game.alertTime > 0) {
			g.setColor(Color.YELLOW);
			center(g, game.alert, H - 90);
		}
	}

	private void drawStatic(Graphics2D g, String msg) {
		for (int i = 0; i < 1500; i++) {
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
