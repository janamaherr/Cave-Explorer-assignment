package code;

import java.util.List;

/**
 * Generic search problem ADT (as presented in class).
 *
 * A search problem is defined by:
 * <ul>
 *   <li>an initial state,</li>
 *   <li>a set of operators, given here as a successor function that returns,
 *       for a state, every applicable operator with its resulting state and
 *       step cost,</li>
 *   <li>a goal test, and</li>
 *   <li>a path cost function (the sum of the step costs along a path).</li>
 * </ul>
 * Concrete problems (such as {@link CaveExplorer}) extend this class and are
 * solved by the problem-independent {@link GenericSearch#search} procedure.
 *
 * @param <S> the type of the problem states
 */
public abstract class GenericSearchProblem<S> {

    /** The state the agent starts in. */
    public abstract S initialState();

    /** All (operator, resulting state, step cost) triples applicable in a state. */
    public abstract List<Successor<S>> successors(S state);

    /** Whether a state satisfies the goal. */
    public abstract boolean goalTest(S state);

    /**
     * An estimate of the cheapest cost from the state to a goal, used by A*.
     * The default is the zero heuristic; problems override it with an
     * informed, admissible one.
     */
    public int heuristic(S state) {
        return 0;
    }

    /**
     * A lower bound on the number of operators still needed to reach a goal
     * from the state. Depth-limited search uses it to cut branches that
     * cannot reach a goal within the depth limit. The default (0) never cuts.
     */
    public int minStepsToGoal(S state) {
        return 0;
    }

    /**
     * Groups states for redundant-state elimination: only states with equal
     * keys are ever compared with {@link #dominates}. By default this is the
     * state itself.
     */
    public Object duplicateKey(S state) {
        return state;
    }

    /**
     * Whether state {@code a} is at least as good as state {@code b} (both
     * with the same {@link #duplicateKey}): every operator sequence applicable
     * in {@code b} is also applicable in {@code a} with the same step costs.
     * The search then discards {@code b} when it was reached no better than
     * {@code a}. By default a state only dominates an equal state, which is
     * plain repeated-state checking.
     */
    public boolean dominates(S a, S b) {
        return a.equals(b);
    }

    /** One applicable operator: its name, the state it leads to, and its cost. */
    public static final class Successor<S> {
        /** Name of the operator, e.g. "left". */
        private final String operator;
        /** The state reached by applying the operator. */
        private final S state;
        /** The cost of applying the operator. */
        private final int stepCost;

        /** Creates a successor triple. */
        public Successor(String operator, S state, int stepCost) {
            this.operator = operator;
            this.state = state;
            this.stepCost = stepCost;
        }

        /** The operator name. */
        public String getOperator() {
            return operator;
        }

        /** The resulting state. */
        public S getState() {
            return state;
        }

        /** The step cost of the operator. */
        public int getStepCost() {
            return stepCost;
        }
    }
}
