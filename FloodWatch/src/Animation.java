/**
 * A reversible 0..1 animation timer (used for the monitor flip and the reboot panel).
 *
 * Call setTarget(true) to play toward "open" and setTarget(false) to play back toward "closed".
 * You can change direction in the middle of an animation and it reverses smoothly from where it is,
 * so flipping the monitor down while it is still rising does not jump.
 *
 * ENCAPSULATION: the raw time is private; read the result with getProgress() (0 = closed, 1 = open).
 * OVERLOADING:   two constructors and two setTarget() versions.
 */
public class Animation {
    public enum Easing { LINEAR, EASE_IN_OUT, EASE_OUT }

    private final double duration; // seconds for a full 0 -> 1
    private final Easing easing;
    private double time = 0; // 0 .. duration
    private boolean target = false; // true = heading toward open

    /** OVERLOADED: smooth ease-in-out. */
    public Animation(double durationSeconds) {
        this(durationSeconds, Easing.EASE_IN_OUT);
    }

    /** OVERLOADED: choose the easing curve. */
    public Animation(double durationSeconds, Easing easing) {
        this.duration = Math.max(0.01, durationSeconds);
        this.easing = easing;
    }

    /** OVERLOADED: play toward open (true) or closed (false). */
    public void setTarget(boolean open) {
        target = open;
    }

    /** OVERLOADED: same, but optionally jump there instantly. */
    public void setTarget(boolean open, boolean instant) {
        target = open;
        if (instant) time = open ? duration : 0;
    }

    public void update(double dt) {
        time += target ? dt : -dt;
        time = Math.max(0, Math.min(duration, time));
    }

    /** Eased progress: 0 = fully closed, 1 = fully open. */
    public double getProgress() {
        double x = time / duration;
        switch (easing) {
            case EASE_OUT:
                return 1 - Math.pow(1 - x, 3);
            case EASE_IN_OUT:
                return x * x * (3 - 2 * x); // smoothstep
            default:
                return x;
        }
    }

    public boolean isOpen() {
        return time >= duration;
    }

    public boolean isClosed() {
        return time <= 0;
    }

    public boolean isAnimating() {
        return !isOpen() && !isClosed();
    }
}