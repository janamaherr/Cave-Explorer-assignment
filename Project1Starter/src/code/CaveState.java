package code;

/**
 * An immutable state of the cave explorer problem.
 *
 * Besides the explorer's position it records every resource that limits the
 * future actions (energy, rope, lives), whether a key is being carried, and
 * which keys / doors are still in the cave (as bit masks indexed by the order
 * in which keys and doors appear in the input string).
 */
public final class CaveState {

    /** Column of the explorer (0 = leftmost). */
    final int x;
    /** Row of the explorer (0 = top). */
    final int y;
    /** Energy left. */
    final int energy;
    /** Meters of rope left. */
    final int rope;
    /** Lives left. */
    final int lives;
    /** Whether the explorer is holding a key. */
    final boolean carryingKey;
    /** Bit i is set while key i is still lying in the cave (not collected). */
    final int availableKeys;
    /** Bit i is set while door i is still locked. */
    final int lockedDoors;

    /** Creates a state with all of its fields given explicitly. */
    CaveState(int x, int y, int energy, int rope, int lives, boolean carryingKey,
              int availableKeys, int lockedDoors) {
        this.x = x;
        this.y = y;
        this.energy = energy;
        this.rope = rope;
        this.lives = lives;
        this.carryingKey = carryingKey;
        this.availableKeys = availableKeys;
        this.lockedDoors = lockedDoors;
    }

    /** The state after moving to (newX, newY) while paying the given resources. */
    CaveState moveTo(int newX, int newY, int energyCost, int ropeCost, int livesCost) {
        return new CaveState(newX, newY, energy - energyCost, rope - ropeCost,
                lives - livesCost, carryingKey, availableKeys, lockedDoors);
    }

    /** The state after picking up key number {@code key}. */
    CaveState collect(int key) {
        return new CaveState(x, y, energy, rope, lives, true,
                availableKeys & ~(1 << key), lockedDoors);
    }

    /** The state after using the carried key on door number {@code door}. */
    CaveState unlock(int door) {
        return new CaveState(x, y, energy, rope, lives, false,
                availableKeys, lockedDoors & ~(1 << door));
    }

    /**
     * Packs every field except the resources (energy, rope, lives) into one
     * number. Used as the redundancy key (see {@link CaveExplorer#duplicateKey}).
     */
    long keyWithoutResources() {
        // Each field gets its own bits: x and y < 256, up to 16 keys, 8 doors.
        long key = x;
        key = (key << 8) | y;
        key = (key << 1) | (carryingKey ? 1 : 0);
        key = (key << 16) | availableKeys;
        key = (key << 8) | lockedDoors;
        return key;
    }

    /** Two states are equal when every field is equal. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CaveState)) {
            return false;
        }
        CaveState that = (CaveState) other;
        return x == that.x && y == that.y && energy == that.energy && rope == that.rope
                && lives == that.lives && carryingKey == that.carryingKey
                && availableKeys == that.availableKeys && lockedDoors == that.lockedDoors;
    }

    /** Hash code consistent with equals. */
    @Override
    public int hashCode() {
        long hash = keyWithoutResources();
        hash = hash * 31 + energy;
        hash = hash * 31 + rope;
        hash = hash * 31 + lives;
        return Long.hashCode(hash);
    }
}
