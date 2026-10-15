package code;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Self-check for the cave explorer solver (not part of the graded solution).
 *
 * Every answer returned by {@link CaveExplorer#solve} is verified
 * independently of the search code:
 * <ol>
 *   <li>the plan is replayed by a small simulator that follows the rules of
 *       the assignment PDF, and must open every door with at least one life
 *       left;</li>
 *   <li>the reported lives and energy must match what the replay used;</li>
 *   <li>UC and AS must be optimal (fewest lives first, then least energy).
 *       For small caves this is compared with a brute-force search over all
 *       reachable states; for big caves UC and AS must at least agree;</li>
 *   <li>all strategies must agree on whether a solution exists;</li>
 *   <li>every call must finish well within the 120 s limit of the tests.</li>
 * </ol>
 *
 * Run it with the "Run" button above main, or from Project1Starter:
 * <pre>
 *   javac -d out -cp "lib/*" src/code/*.java
 *   java -cp out code.Demo
 * </pre>
 */
public class Demo {

    /** The search strategies required by the assignment. */
    private static final String[] STRATEGIES = {"ID", "UC", "AS"};
    /** The explorer always starts with 3 lives. */
    private static final int INITIAL_LIVES = 3;
    /** A single solve call slower than this is reported as a failure. */
    private static final long TIME_LIMIT_MS = 60_000;

    /** Number of passed / failed checks. */
    private static int passed = 0;
    private static int failed = 0;
    /** Slowest solve call seen so far, per strategy. */
    private static final long[] slowestMs = new long[STRATEGIES.length];

    public static void main(String[] args) {
        System.out.println("=== 1. Example from the assignment PDF ===");
        // Expected (PDF): right,climbup,collect,climbdown,jumpdown,right,right,unlock;1;65;272
        checkCave("PDF example", "3,4;0,1;100,2;0,3,0,0;4,12,0,0;0,7,23,8;3,2;1,0;", 1, 65);

        System.out.println("\n=== 2. Public test caves ===");
        String[] publicCaves = {
                "4,5;0,3;180,10;6,12,8,15,10;5,0,0,0,9;7,11,4,6,8;3,5,7,9,6;4,0;1,2;",
                "5,5;2,4;250,12;8,14,6,22,9;7,0,0,0,10;5,11,18,0,12;9,0,6,0,8;4,5,7,6,3;0,0;2,2;",
        };
        for (int i = 0; i < publicCaves.length; i++) {
            checkCave("public cave " + (i + 1), publicCaves[i], -1, -1);
        }

        System.out.println("\n=== 3. Hand-made edge cases ===");
        // Explorer stands on the key; the door is two cells to the right.
        checkCave("start on key", "3,3;0,0;50,0;1,2,3;0,0,0;0,0,0;2,0;0,0;", 0, 5);
        // Same cave but not enough energy to reach the door (needs 5, has 4).
        checkCave("not enough energy", "3,3;0,0;4,0;1,2,3;0,0,0;0,0,0;2,0;0,0;", -2, -2);
        // Exactly enough energy (5) must still be solvable.
        checkCave("exactly enough energy", "3,3;0,0;5,0;1,2,3;0,0,0;0,0,0;2,0;0,0;", 0, 5);
        // The key is walled off from the explorer.
        checkCave("key behind a wall", "3,3;0,0;100,5;1,1,0;1,1,0;0,0,1;1,1;2,2;", -2, -2);
        // Door is one row up but there is no rope: climbing up is impossible.
        checkCave("no rope to climb up", "3,3;0,1;100,0;5,5,5;1,5,5;5,5,5;0,0;0,1;", -2, -2);
        // Door is three rows down and there is no rope: needs 3 jumps but only
        // 2 are possible (a jump needs more than one life).
        checkCave("too many jumps", "4,1;0,0;100,0;1;1;1;1;0,3;0,0;", -2, -2);
        // Same cave with two rows down: 2 jumps use 2 lives, 1 is left.
        checkCave("two jumps allowed", "3,1;0,0;100,0;1;1;1;0,2;0,0;", 2, 2);
        // A cheap path with a jump (energy 5, 1 life) vs. a path without jumps
        // (energy 101, 0 lives): fewer lives must win even though it costs
        // much more energy.
        checkCave("lives before energy", "3,3;0,1;200,2;1,1,1;1,0,1;50,50,1;2,2;0,1;", 0, 101);
        // Two doors but only one key reachable.
        checkCave("one key two doors", "3,3;0,0;100,5;1,1,1;1,1,0;0,0,1;1,0,2,0;0,1,2,2;", -2, -2);
        // Only one key may be carried: two keys, two doors, must alternate.
        checkCave("carry one key at a time", "1,4;0,0;100,0;1,1,1,1;2,0,3,0;0,0,1,0;", 0, 5);
        // Zero energy: no move at all is possible.
        checkCave("zero energy", "3,3;0,0;0,5;1,1,1;1,1,1;1,1,1;2,2;0,0;", -2, -2);
        // Large difficulties (45) and an input containing spaces / newlines.
        checkCave("spaces and newlines", "3, 3;\n0, 0;\n500, 25;\n45,45,45;\n45,45,45;\n45,45,45;\n2,2;\n0,0;", 0, 180);

        System.out.println("\n=== 4. Random small caves vs. brute force (optimality) ===");
        Random random = new Random(2026);
        for (int i = 0; i < 300; i++) {
            String cave = randomCave(random, 3, 5, 120, 6, 3, 4);
            checkCave("small #" + i, cave, -1, -1, true, false);
        }

        System.out.println("\n=== 5. Random large caves (limits of the PDF, speed) ===");
        for (int i = 0; i < 40; i++) {
            String cave = randomCave(random, 7, 10, 500, 25, 5, 8);
            checkCave("large #" + i, cave, -1, -1, false, false);
        }

        System.out.println("\n=== Summary ===");
        for (int s = 0; s < STRATEGIES.length; s++) {
            System.out.println("slowest " + STRATEGIES[s] + " call: " + slowestMs[s] + " ms");
        }
        System.out.println(passed + " checks passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Checking one cave
    // ------------------------------------------------------------------

    /** Checks a cave and prints every answer. */
    private static void checkCave(String name, String cave, int expectedLives, int expectedEnergy) {
        checkCave(name, cave, expectedLives, expectedEnergy, cave.length() < 40, true);
    }

    /**
     * Solves a cave with every strategy and verifies the answers.
     *
     * @param expectedLives  optimal lives used, -1 if unknown, -2 if "No Solution" is expected
     * @param expectedEnergy optimal energy used (same conventions)
     * @param bruteForce     also compare with an exhaustive search (small caves only)
     * @param verbose        print every answer, not only failures
     */
    private static void checkCave(String name, String cave, int expectedLives, int expectedEnergy,
                                  boolean bruteForce, boolean verbose) {
        Cave parsed = new Cave(cave);
        int[] optimum = bruteForce ? parsed.bruteForceOptimum() : null; // {lives, energy} or null
        if (bruteForce && optimum == null && expectedLives == -1) {
            expectedLives = expectedEnergy = -2;
        } else if (bruteForce && optimum != null && expectedLives == -1) {
            expectedLives = optimum[0];
            expectedEnergy = optimum[1];
        }

        int[][] costs = new int[STRATEGIES.length][]; // {lives, energy} per strategy, null = no solution
        for (int s = 0; s < STRATEGIES.length; s++) {
            String strategy = STRATEGIES[s];
            long start = System.currentTimeMillis();
            String answer;
            try {
                answer = CaveExplorer.solve(cave, strategy);
            } catch (RuntimeException e) {
                fail(name, strategy, "threw " + e);
                continue;
            }
            long time = System.currentTimeMillis() - start;
            slowestMs[s] = Math.max(slowestMs[s], time);
            if (verbose) {
                System.out.println("  " + name + " [" + strategy + "] " + time + " ms -> " + answer);
            }
            if (time > TIME_LIMIT_MS) {
                fail(name, strategy, "too slow: " + time + " ms");
            }

            if (answer.equals("No Solution")) {
                costs[s] = null;
                check(expectedLives < 0, name, strategy,
                        "returned No Solution but a solution exists");
                continue;
            }
            String error = parsed.validate(answer);
            if (error != null) {
                fail(name, strategy, error + " | answer: " + answer);
                continue;
            }
            String[] fields = answer.split(";");
            costs[s] = new int[]{Integer.parseInt(fields[1]), Integer.parseInt(fields[2])};
            check(expectedLives != -2, name, strategy, "found a plan but none should exist");
            // Only UC and AS are required to be optimal.
            if (!strategy.equals("ID") && expectedLives >= 0) {
                check(costs[s][0] == expectedLives && costs[s][1] == expectedEnergy, name, strategy,
                        "not optimal: got lives " + costs[s][0] + " energy " + costs[s][1]
                                + ", expected lives " + expectedLives + " energy " + expectedEnergy);
            }
        }

        // All strategies must agree on solvability; UC and AS on the optimal cost.
        boolean idSolved = costs[0] != null;
        boolean ucSolved = costs[1] != null;
        boolean asSolved = costs[2] != null;
        check(idSolved == ucSolved && ucSolved == asSolved, name, "ALL",
                "strategies disagree on whether a solution exists");
        if (ucSolved && asSolved) {
            check(costs[1][0] == costs[2][0] && costs[1][1] == costs[2][1], name, "UC/AS",
                    "UC and AS found different costs");
        }
        if (idSolved && ucSolved) {
            // UC is optimal, so ID can never be strictly better than UC.
            boolean idNotBetter = costs[0][0] > costs[1][0]
                    || (costs[0][0] == costs[1][0] && costs[0][1] >= costs[1][1]);
            check(idNotBetter, name, "ID/UC", "ID beat UC, so UC is not optimal");
        }
    }

    /** Records one check. */
    private static void check(boolean ok, String name, String strategy, String message) {
        if (ok) {
            passed++;
        } else {
            fail(name, strategy, message);
        }
    }

    /** Records and prints one failed check. */
    private static void fail(String name, String strategy, String message) {
        failed++;
        System.out.println("  FAIL " + name + " [" + strategy + "]: " + message);
    }

    // ------------------------------------------------------------------
    // Random cave generator (follows the limits in the PDF)
    // ------------------------------------------------------------------

    /**
     * Builds a random cave string. Walls are placed with 20% probability;
     * the explorer, doors and keys are always on caves, doors and keys never
     * share a cell, and there are at least as many keys as doors.
     */
    private static String randomCave(Random random, int minSize, int maxSize,
                                     int maxEnergy, int maxRope, int maxDoors, int maxKeys) {
        int height = minSize + random.nextInt(maxSize - minSize + 1);
        int width = minSize + random.nextInt(maxSize - minSize + 1);
        int[][] difficulty = new int[height][width];
        List<int[]> caves = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                difficulty[y][x] = random.nextInt(5) == 0 ? 0 : 1 + random.nextInt(45);
                if (difficulty[y][x] != 0) {
                    caves.add(new int[]{x, y});
                }
            }
        }
        if (caves.size() < 3) {
            return randomCave(random, minSize, maxSize, maxEnergy, maxRope,
                    maxDoors, maxKeys);
        }
        java.util.Collections.shuffle(caves, random);
        int doors = 1 + random.nextInt(Math.min(maxDoors, (caves.size() - 1) / 2));
        int keys = doors + random.nextInt(Math.min(maxKeys, caves.size() - 1 - doors) - doors + 1);

        StringBuilder text = new StringBuilder();
        text.append(height).append(',').append(width).append(';');
        int[] start = caves.get(random.nextInt(caves.size())); // may share a cell with a key / door
        text.append(start[0]).append(',').append(start[1]).append(';');
        text.append(random.nextInt(maxEnergy + 1)).append(',').append(random.nextInt(maxRope + 1)).append(';');
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                text.append(difficulty[y][x]).append(x + 1 < width ? "," : ";");
            }
        }
        appendCells(text, caves, 0, doors);
        appendCells(text, caves, doors, doors + keys);
        return text.toString();
    }

    /** Appends "x1,y1,x2,y2,...;" for caves[from..to). */
    private static void appendCells(StringBuilder text, List<int[]> caves, int from, int to) {
        for (int i = from; i < to; i++) {
            text.append(caves.get(i)[0]).append(',').append(caves.get(i)[1]).append(i + 1 < to ? "," : ";");
        }
    }

    // ------------------------------------------------------------------
    // Independent model of the rules (does not use the solver's code)
    // ------------------------------------------------------------------

    /** A parsed cave plus a simulator of the seven actions from the PDF. */
    private static final class Cave {
        final int height;
        final int width;
        final int startX;
        final int startY;
        final int energy;
        final int rope;
        final int[][] difficulty; // [y][x], 0 = wall
        final int[][] doors;      // {x, y}
        final int[][] keys;       // {x, y}

        /** Parses the input format of the PDF. */
        Cave(String text) {
            String[] parts = text.replaceAll("\\s", "").split(";");
            int[] size = numbers(parts[0]);
            height = size[0];
            width = size[1];
            int[] start = numbers(parts[1]);
            startX = start[0];
            startY = start[1];
            int[] resources = numbers(parts[2]);
            energy = resources[0];
            rope = resources[1];
            difficulty = new int[height][];
            for (int y = 0; y < height; y++) {
                difficulty[y] = numbers(parts[3 + y]);
            }
            doors = pairs(numbers(parts[3 + height]));
            keys = pairs(numbers(parts[4 + height]));
        }

        /** Splits "a,b,c" into numbers. */
        static int[] numbers(String text) {
            String[] tokens = text.split(",");
            int[] result = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                result[i] = Integer.parseInt(tokens[i]);
            }
            return result;
        }

        /** Groups a flat "x1,y1,x2,y2" list into {x, y} pairs. */
        static int[][] pairs(int[] flat) {
            int[][] result = new int[flat.length / 2][];
            for (int i = 0; i < result.length; i++) {
                result[i] = new int[]{flat[2 * i], flat[2 * i + 1]};
            }
            return result;
        }

        /** Index of the door / key at (x, y), or -1. */
        int indexAt(int[][] cells, int x, int y) {
            for (int i = 0; i < cells.length; i++) {
                if (cells[i][0] == x && cells[i][1] == y) {
                    return i;
                }
            }
            return -1;
        }

        /** Whether (x, y) is inside the grid and not a wall. */
        boolean isCave(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && difficulty[y][x] != 0;
        }

        /**
         * The start state: {x, y, energy, rope, lives, carrying (0/1),
         * keys still in the cave (bit mask), locked doors (bit mask)}.
         */
        int[] startState() {
            return new int[]{startX, startY, energy, rope, INITIAL_LIVES, 0,
                    (1 << keys.length) - 1, (1 << doors.length) - 1};
        }

        /** Applies one action following the PDF, or returns null if it is not allowed. */
        int[] apply(int[] s, String action) {
            int[] n = s.clone();
            switch (action) {
                case "left":
                case "right":
                case "climbup":
                case "climbdown":
                case "jumpdown": {
                    int dx = action.equals("left") ? -1 : action.equals("right") ? 1 : 0;
                    int dy = action.equals("climbup") ? -1 : action.endsWith("down") ? 1 : 0;
                    int nx = s[0] + dx;
                    int ny = s[1] + dy;
                    if (!isCave(nx, ny) || s[2] < difficulty[ny][nx]) {
                        return null; // wall / outside / not enough energy
                    }
                    if (action.startsWith("climb")) {
                        if (s[3] < 1) {
                            return null; // no rope left
                        }
                        n[3]--;
                    }
                    if (action.equals("jumpdown")) {
                        if (s[4] <= 1) {
                            return null; // needs more than one life
                        }
                        n[4]--;
                    }
                    n[0] = nx;
                    n[1] = ny;
                    n[2] -= difficulty[ny][nx];
                    return n;
                }
                case "collect": {
                    int key = indexAt(keys, s[0], s[1]);
                    if (key < 0 || s[5] == 1 || (s[6] & (1 << key)) == 0) {
                        return null; // no key here, or already carrying one
                    }
                    n[5] = 1;
                    n[6] &= ~(1 << key);
                    return n;
                }
                case "unlock": {
                    int door = indexAt(doors, s[0], s[1]);
                    if (door < 0 || s[5] == 0 || (s[7] & (1 << door)) == 0) {
                        return null; // no locked door here, or no key
                    }
                    n[5] = 0;
                    n[7] &= ~(1 << door);
                    return n;
                }
                default:
                    return null;
            }
        }

        /** Replays an answer; returns an error message, or null if it is correct. */
        String validate(String answer) {
            String[] fields = answer.split(";", -1);
            if (fields.length != 4) {
                return "answer must have 4 fields plan;lives;energy;nodes";
            }
            int[] state = startState();
            if (!fields[0].isEmpty()) {
                for (String action : fields[0].split(",")) {
                    int[] next = apply(state, action);
                    if (next == null) {
                        return "illegal action '" + action + "'";
                    }
                    state = next;
                }
            }
            if (state[7] != 0) {
                return "plan leaves doors locked";
            }
            if (state[4] < 1) {
                return "no life left";
            }
            if (Integer.parseInt(fields[1]) != INITIAL_LIVES - state[4]) {
                return "reported lives " + fields[1] + " but used " + (INITIAL_LIVES - state[4]);
            }
            if (Integer.parseInt(fields[2]) != energy - state[2]) {
                return "reported energy " + fields[2] + " but used " + (energy - state[2]);
            }
            if (Integer.parseInt(fields[3]) < 0) {
                return "negative node count";
            }
            return null;
        }

        /**
         * Exhaustively explores every reachable state and returns the best
         * goal {lives used, energy used}, or null if there is none. A state
         * fully determines the lives and energy used so far, so the best goal
         * is simply the best goal state that can be reached.
         */
        int[] bruteForceOptimum() {
            String[] actions = {"left", "right", "climbup", "climbdown", "jumpdown", "collect", "unlock"};
            Set<String> seen = new HashSet<>();
            Deque<int[]> queue = new ArrayDeque<>();
            int[] start = startState();
            seen.add(java.util.Arrays.toString(start));
            queue.add(start);
            int[] best = null;
            while (!queue.isEmpty()) {
                int[] state = queue.remove();
                if (state[7] == 0 && state[4] >= 1) {
                    int[] cost = {INITIAL_LIVES - state[4], energy - state[2]};
                    if (best == null || cost[0] < best[0] || (cost[0] == best[0] && cost[1] < best[1])) {
                        best = cost;
                    }
                    continue; // the search stops at the goal
                }
                for (String action : actions) {
                    int[] next = apply(state, action);
                    if (next != null && seen.add(java.util.Arrays.toString(next))) {
                        queue.add(next);
                    }
                }
            }
            return best;
        }
    }
}
