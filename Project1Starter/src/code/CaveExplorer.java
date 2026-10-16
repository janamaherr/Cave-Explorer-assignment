package code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The cave explorer search problem.
 *
 * <p><b>States</b> are {@link CaveState}s (position, energy, rope, lives,
 * carried key, keys still in the cave, doors still locked).
 *
 * <p><b>Operators</b>: left, right, climbup, climbdown, jumpdown, collect,
 * unlock. Every move (including jumpdown) consumes the difficulty of the
 * destination cell as energy and requires at least that much energy; climbing
 * uses one meter of rope; jumpdown uses one life and needs more than one life.
 *
 * <p><b>Goal</b>: all doors unlocked with at least one life left.
 *
 * <p><b>Path cost</b>: the task minimises the lives used first and the energy
 * used second. This lexicographic order is encoded in a single integer:
 * {@code cost = livesUsed * LIFE_WEIGHT + energyUsed}, where
 * {@code LIFE_WEIGHT = initialEnergy + 1}. Since the energy used can never
 * exceed the initial energy, one life always outweighs any amount of energy.
 */
public class CaveExplorer extends GenericSearchProblem<CaveState> {

    /** The explorer always starts with 3 lives. */
    private static final int INITIAL_LIVES = 3;
    /** Marks an unreachable target in the distance table / heuristics. */
    private static final int UNREACHABLE = Integer.MAX_VALUE / 4;

    /** Number of rows (H). */
    private final int height;
    /** Number of columns (W). */
    private final int width;
    /** difficulty[y][x]: energy needed to enter the cell; 0 means wall. */
    private final int[][] difficulty;
    /** Starting column of the explorer. */
    private final int startX;
    /** Starting row of the explorer. */
    private final int startY;
    /** Energy at the start (EN). */
    private final int initialEnergy;
    /** Rope length at the start (RL). */
    private final int initialRope;
    /** Coordinates of door i (in input order). */
    private final int[] doorX;
    private final int[] doorY;
    /** Coordinates of key j (in input order). */
    private final int[] keyX;
    private final int[] keyY;
    /** Index of the door / key at [y][x], or -1 if there is none. */
    private final int[][] doorAt;
    private final int[][] keyAt;
    /** Cost of one life in path-cost units (see class comment). */
    private final int lifeWeight;
    /** energyDistance[a][b]: minimum energy needed to walk from cell a to cell b. */
    private final int[][] energyDistance;
    /** stepDistance[a][b]: minimum number of moves needed to walk from cell a to cell b. */
    private final int[][] stepDistance;

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    /**
     * Solves the cave described by {@code initString} using {@code strategy}
     * ("ID", "UC" or "AS").
     *
     * @return "plan;livesUsed;energyUsed;expandedNodes", or "No Solution"
     */
    public static String solve(String initString, String strategy) {
        // Build the problem, run the generic search, then format the answer.
        CaveExplorer problem = new CaveExplorer(initString);
        GenericSearch.Result<CaveState> result = GenericSearch.search(problem, strategy);
        Node<CaveState> goal = result.getGoal();
        if (goal == null) {
            return "No Solution";
        }
        CaveState end = goal.getState();
        int livesUsed = INITIAL_LIVES - end.lives;
        int energyUsed = problem.initialEnergy - end.energy;
        return goal.plan() + ";" + livesUsed + ";" + energyUsed + ";" + result.getExpandedNodes();
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Parses "H,W;X,Y;EN,RL;row_0;...;row_(H-1);doors;keys;" where H is the
     * number of rows, W the number of columns, and (0,0) is the top-left cell.
     */
    private CaveExplorer(String initString) {
        // Sections are separated by ';' (a trailing ';' gives no empty section).
        String[] parts = initString.split(";");
        int[] size = parseNumbers(parts[0]);
        height = size[0];
        width = size[1];
        int[] start = parseNumbers(parts[1]);
        startX = start[0];
        startY = start[1];
        int[] resources = parseNumbers(parts[2]);
        initialEnergy = resources[0];
        initialRope = resources[1];

        // One section per row of cell difficulties.
        difficulty = new int[height][];
        for (int y = 0; y < height; y++) {
            difficulty[y] = parseNumbers(parts[3 + y]);
        }

        // The last two sections list the doors and the keys as x,y pairs.
        int[] doors = parseNumbers(parts.length > 3 + height ? parts[3 + height] : "");
        int[] keys = parseNumbers(parts.length > 4 + height ? parts[4 + height] : "");
        doorX = evenEntries(doors, 0);
        doorY = evenEntries(doors, 1);
        keyX = evenEntries(keys, 0);
        keyY = evenEntries(keys, 1);
        doorAt = indexGrid(doorX, doorY);
        keyAt = indexGrid(keyX, keyY);

        // Precompute the cost of a life and the distance tables.
        lifeWeight = initialEnergy + 1;
        energyDistance = computeDistances(false);
        stepDistance = computeDistances(true);
    }

    /** Parses a comma-separated list of integers (spaces and newlines around numbers are ignored). */
    private static int[] parseNumbers(String text) {
        if (text.trim().isEmpty()) {
            return new int[0];
        }
        String[] tokens = text.split(",");
        int[] numbers = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            numbers[i] = Integer.parseInt(tokens[i].trim());
        }
        return numbers;
    }

    /** Picks every other entry of a flat "x1,y1,x2,y2,..." list. */
    private static int[] evenEntries(int[] coordinates, int offset) {
        int[] result = new int[coordinates.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = coordinates[2 * i + offset];
        }
        return result;
    }

    /** Builds a grid holding the index of the item at each cell, or -1. */
    private int[][] indexGrid(int[] xs, int[] ys) {
        int[][] grid = new int[height][width];
        for (int[] row : grid) {
            Arrays.fill(row, -1);
        }
        for (int i = 0; i < xs.length; i++) {
            grid[ys[i]][xs[i]] = i;
        }
        return grid;
    }

    // ------------------------------------------------------------------
    // Problem definition
    // ------------------------------------------------------------------

    /** Start position and resources; every key is in the cave, every door locked. */
    @Override
    public CaveState initialState() {
        return new CaveState(startX, startY, initialEnergy, initialRope, INITIAL_LIVES,
                false, (1 << keyX.length) - 1, (1 << doorX.length) - 1);
    }

    /** Goal: no locked door left and at least one life. */
    @Override
    public boolean goalTest(CaveState state) {
        return state.lockedDoors == 0 && state.lives >= 1;
    }

    /**
     * Applies each of the 7 operators that is allowed in the state.
     * Step cost = lives used * LIFE_WEIGHT + energy used.
     */
    @Override
    public List<Successor<CaveState>> successors(CaveState state) {
        List<Successor<CaveState>> result = new ArrayList<>();
        // The five movement operators as (dx, dy); row 0 is the top, so up is dy = -1.
        addMove(result, state, "left", -1, 0);
        addMove(result, state, "right", 1, 0);
        addMove(result, state, "climbup", 0, -1);
        addMove(result, state, "climbdown", 0, 1);
        addMove(result, state, "jumpdown", 0, 1);

        // collect: only on a key that is still there, with empty hands.
        int key = keyAt[state.y][state.x];
        if (key >= 0 && !state.carryingKey && (state.availableKeys & (1 << key)) != 0) {
            addIfAlive(result, "collect", state.collect(key), 0);
        }

        // unlock: only on a locked door while carrying a key.
        int door = doorAt[state.y][state.x];
        if (door >= 0 && state.carryingKey && (state.lockedDoors & (1 << door)) != 0) {
            addIfAlive(result, "unlock", state.unlock(door), 0);
        }
        return result;
    }

    /** Adds one of the five movement operators if it is applicable. */
    private void addMove(List<Successor<CaveState>> result, CaveState state,
                         String operator, int dx, int dy) {
        int newX = state.x + dx;
        int newY = state.y + dy;
        if (!isCave(newX, newY)) {
            return; // outside the grid or a wall
        }
        int energyCost = difficulty[newY][newX];
        if (state.energy < energyCost) {
            return; // not enough energy for the destination cell
        }
        boolean climbing = operator.startsWith("climb");
        boolean jumping = operator.equals("jumpdown");
        if (climbing && state.rope < 1) {
            return; // no rope left
        }
        if (jumping && state.lives <= 1) {
            return; // jumping needs more than one life
        }
        int ropeCost = climbing ? 1 : 0;
        int livesCost = jumping ? 1 : 0;
        CaveState next = state.moveTo(newX, newY, energyCost, ropeCost, livesCost);
        addIfAlive(result, operator, next, livesCost * lifeWeight + energyCost);
    }

    /**
     * Adds a successor unless it is a dead end, i.e. a state from which the
     * lower bounds below prove that the remaining doors can no longer be
     * opened with the remaining energy, rope, lives and keys. Such states can
     * never lead to a goal, so discarding them does not affect completeness
     * or optimality; it only saves search effort.
     */
    private void addIfAlive(List<Successor<CaveState>> result, String operator,
                            CaveState next, int stepCost) {
        if (!isDeadEnd(next)) {
            result.add(new Successor<>(operator, next, stepCost));
        }
    }

    /** Whether (x, y) is inside the grid and not a wall. */
    private boolean isCave(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && difficulty[y][x] != 0;
    }

    /**
     * Redundancy key: the position, the carried key and the keys / doors left.
     * States sharing it differ only in their resources (energy, rope, lives).
     */
    @Override
    public Object duplicateKey(CaveState state) {
        return state.keyWithoutResources();
    }

    /**
     * Having more energy, rope or lives never forbids an action and does not
     * change any step cost, so a state with at least as much of each resource
     * can do everything the other one can.
     */
    @Override
    public boolean dominates(CaveState a, CaveState b) {
        return a.energy >= b.energy && a.rope >= b.rope && a.lives >= b.lives;
    }

    // ------------------------------------------------------------------
    // A* heuristic
    // ------------------------------------------------------------------

    /*
     * HEURISTIC h(n) = minLives(n) * LIFE_WEIGHT + minEnergy(n)
     *
     * The path cost counts lives first and energy second, so the heuristic
     * estimates both parts separately. Each part is a lower bound on what is
     * still needed, so their weighted sum never overestimates the true
     * remaining cost, and h is ADMISSIBLE.
     *
     * 1) minEnergy - a lower bound on the energy still needed.
     *    Every move (left, right, climb or jump) costs exactly the difficulty
     *    of the cell it enters, so the cheapest energy to get from cell a to
     *    cell b is the shortest path where entering a cell costs its
     *    difficulty. This is precomputed for all pairs with Dijkstra. Rope and
     *    lives are ignored there, which can only make the paths cheaper.
     *    The rest of any solution splits into R legs, where R is the number of
     *    locked doors, each ending with an unlock:
     *      - first leg:  if a key is carried, walk to some locked door;
     *                    otherwise walk to an uncollected key and from there
     *                    to a locked door. We take the cheapest such choice.
     *      - each of the other R-1 legs goes from one locked door to a key and
     *        then to a different locked door, so it costs at least
     *        min over doors a != b and keys k of dist(a,k) + dist(k,b).
     *    The legs share no moves, so their bounds can be added up.
     *
     * 2) minLives - a lower bound on the lives (jumpdowns) still needed.
     *    Going down a row takes a climbdown (1m of rope) or a jumpdown (1
     *    life). Going up a row takes a climbup (1m of rope). To reach the
     *    lowest locked door the explorer must go down at least `down` rows,
     *    and to reach the highest one it must go up at least `up` rows. The
     *    ups use `up` meters of rope, so at most (rope - up) meters are left
     *    for descents. The other descents must be jumps:
     *      minLives = max(0, down - (rope - up)).
     *
     * So the heuristic accounts for energy (cell difficulties), lives and the
     * rope, as well as the order keys and doors must be visited in. It is not
     * just a distance. In the goal state there are no locked doors, so h = 0.
     * The same bounds detect dead ends (see isDeadEnd).
     */
    @Override
    public int heuristic(CaveState state) {
        int lives = minLivesNeeded(state);
        int energy = minEnergyNeeded(state);
        if (lives >= UNREACHABLE || energy >= UNREACHABLE) {
            return 0; // only possible for an initial state that is a dead end
        }
        return lives * lifeWeight + energy;
    }

    /**
     * Lower bound on the energy still needed to unlock every locked door
     * (part 1 of the heuristic), or UNREACHABLE.
     */
    private int minEnergyNeeded(CaveState state) {
        return cheapestTour(state, energyDistance);
    }

    /**
     * Lower bound on the number of actions still needed to reach a goal, used
     * by iterative deepening to cut branches that cannot finish within the
     * depth limit. It is the cheapest tour (as in minEnergyNeeded) counted in
     * moves instead of energy, plus one unlock per locked door and one collect
     * per locked door that is not covered by the key already being carried.
     */
    @Override
    public int minStepsToGoal(CaveState state) {
        int locked = Integer.bitCount(state.lockedDoors);
        int moves = cheapestTour(state, stepDistance);
        if (moves >= UNREACHABLE) {
            return UNREACHABLE;
        }
        int collects = locked - (state.carryingKey && locked > 0 ? 1 : 0);
        return moves + locked + collects;
    }

    /**
     * Lower bound on the distance (measured by the given all-pairs table) of
     * the rest of any solution: a first leg to the first door to be unlocked (via a
     * key unless one is carried) plus one leg door -> key -> other door for
     * every further locked door. Returns UNREACHABLE if no such tour exists.
     */
    private int cheapestTour(CaveState state, int[][] distance) {
        int lockedCount = Integer.bitCount(state.lockedDoors);
        if (lockedCount == 0) {
            return 0;
        }
        int here = cell(state.x, state.y);

        // First leg: (current cell ->) [key ->] some locked door.
        int firstLeg = UNREACHABLE;
        for (int door = 0; door < doorX.length; door++) {
            if (!isLocked(state, door)) {
                continue;
            }
            int doorCell = cell(doorX[door], doorY[door]);
            if (state.carryingKey) {
                firstLeg = Math.min(firstLeg, distance[here][doorCell]);
            } else {
                for (int key = 0; key < keyX.length; key++) {
                    if (isAvailable(state, key)) {
                        int keyCell = cell(keyX[key], keyY[key]);
                        firstLeg = Math.min(firstLeg,
                                distance[here][keyCell] + distance[keyCell][doorCell]);
                    }
                }
            }
        }
        if (firstLeg >= UNREACHABLE || lockedCount == 1) {
            return Math.min(firstLeg, UNREACHABLE);
        }

        // Every later leg: locked door -> available key -> another locked door.
        int cheapestLeg = UNREACHABLE;
        for (int from = 0; from < doorX.length; from++) {
            for (int to = 0; to < doorX.length; to++) {
                if (from == to || !isLocked(state, from) || !isLocked(state, to)) {
                    continue;
                }
                int fromCell = cell(doorX[from], doorY[from]);
                int toCell = cell(doorX[to], doorY[to]);
                for (int key = 0; key < keyX.length; key++) {
                    if (isAvailable(state, key)) {
                        int keyCell = cell(keyX[key], keyY[key]);
                        cheapestLeg = Math.min(cheapestLeg,
                                distance[fromCell][keyCell] + distance[keyCell][toCell]);
                    }
                }
            }
        }
        if (cheapestLeg >= UNREACHABLE) {
            return UNREACHABLE;
        }
        return firstLeg + (lockedCount - 1) * cheapestLeg;
    }

    /**
     * Lower bound on the jumpdowns (lives) still needed to reach every locked
     * door given the remaining rope (part 2 of the heuristic), or
     * UNREACHABLE if the rope cannot even cover the necessary climbs up.
     */
    private int minLivesNeeded(CaveState state) {
        int highestRow = Integer.MAX_VALUE;
        int lowestRow = Integer.MIN_VALUE;
        for (int door = 0; door < doorX.length; door++) {
            if (isLocked(state, door)) {
                highestRow = Math.min(highestRow, doorY[door]);
                lowestRow = Math.max(lowestRow, doorY[door]);
            }
        }
        if (lowestRow == Integer.MIN_VALUE) {
            return 0; // no locked doors left
        }
        int up = Math.max(0, state.y - highestRow);
        int down = Math.max(0, lowestRow - state.y);
        if (up > state.rope) {
            return UNREACHABLE;
        }
        return Math.max(0, down - (state.rope - up));
    }

    /**
     * A state is a dead end when the lower bounds already prove failure:
     * there are fewer keys left than locked doors, the energy left is below
     * the minimum energy still needed, or more jumps are needed than the
     * lives allow (a jump needs more than one life, so with L lives at most
     * L - 1 jumps are possible).
     */
    private boolean isDeadEnd(CaveState state) {
        int keysLeft = Integer.bitCount(state.availableKeys) + (state.carryingKey ? 1 : 0);
        if (keysLeft < Integer.bitCount(state.lockedDoors)) {
            return true;
        }
        int lives = minLivesNeeded(state);
        if (lives >= UNREACHABLE || lives > state.lives - 1) {
            return true;
        }
        int energy = minEnergyNeeded(state);
        return energy >= UNREACHABLE || energy > state.energy;
    }

    /** Whether door number {@code door} is still locked in the state. */
    private boolean isLocked(CaveState state, int door) {
        return (state.lockedDoors & (1 << door)) != 0;
    }

    /** Whether key number {@code key} is still lying in the cave. */
    private boolean isAvailable(CaveState state, int key) {
        return (state.availableKeys & (1 << key)) != 0;
    }

    // ------------------------------------------------------------------
    // Precomputed energy distances
    // ------------------------------------------------------------------

    /** Index of cell (x, y) in the distance tables. */
    private int cell(int x, int y) {
        return y * width + x;
    }

    /**
     * Runs Dijkstra from every cave cell. Entering a cell costs its difficulty
     * (energy distances) or 1 (step distances, when {@code unitCost} is true).
     * Walls are never entered; unreachable pairs stay UNREACHABLE.
     */
    private int[][] computeDistances(boolean unitCost) {
        int cells = height * width;
        int[][] distance = new int[cells][cells];
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int source = 0; source < cells; source++) {
            Arrays.fill(distance[source], UNREACHABLE);
            if (!isCave(source % width, source / width)) {
                continue;
            }
            int[] dist = distance[source];
            dist[source] = 0;
            PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
            queue.add(new int[]{source, 0});
            while (!queue.isEmpty()) {
                int[] entry = queue.remove();
                int current = entry[0];
                if (entry[1] > dist[current]) {
                    continue; // stale queue entry
                }
                for (int[] direction : directions) {
                    int nx = current % width + direction[0];
                    int ny = current / width + direction[1];
                    if (!isCave(nx, ny)) {
                        continue;
                    }
                    int next = cell(nx, ny);
                    int candidate = dist[current] + (unitCost ? 1 : difficulty[ny][nx]);
                    if (candidate < dist[next]) {
                        dist[next] = candidate;
                        queue.add(new int[]{next, candidate});
                    }
                }
            }
        }
        return distance;
    }
}
