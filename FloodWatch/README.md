# FloodWatch (Java / Swing)

Run (JDK 8+), from this folder:

    mkdir bin
    javac -d bin src/*.java
    cp Resources/* bin/
    java -cp bin Main

(In Eclipse: src/ and Resources/ are both source folders, same as the FNAF project.)

Controls: 1-6 camera, SPACE monitor, W flood wall, F fix pipe, P patch window, R reboot power.

Camera images: Resources/cam1.png ... cam6.png (cam1/cam2 are your mockups; missing ones use a placeholder).
Jumpscare: drop an alligator image at Resources/jump.jpg.
Balance: all tunables are constants at the top of FloodEngine.java.

## OOP concepts used (for grading)

- Encapsulation: every field in Area, Office and FloodEngine is private. Main only calls
  FloodEngine getters/actions and never touches Area or Office fields directly.
  Values are validated inside the setters (e.g. power is clamped to 0-100 in Office.addPower).
- Inheritance: Area -> LowLyingArea, HighGroundArea (override getFloodMultiplier, getTerrainLabel).
  Hazard (abstract) -> BlackoutHazard, WindowHazard, PipeBurstHazard (implement canTrigger, trigger).
- Polymorphism: FloodEngine stores List<Area> and List<Hazard> and calls the same methods on each.
- Overloading: Area.addWater(x) / addWater(x, mult); Area.raiseWall() / raiseWall(sec);
  Office.addPower(x) / addPower(x, max); FloodEngine() / FloodEngine(difficulty);
  tick() / tick(dt); selectCam(int) / selectCam(String); deployWall() / deployWall(int);
  raise(msg) / raise(msg, seconds).
