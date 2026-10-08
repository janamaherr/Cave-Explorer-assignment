package code;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The generic search procedure presented in class:
 *
 * <pre>
 *   function GENERAL-SEARCH(problem, QING-FUN)
 *     nodes &lt;- MAKE-Q(MAKE-NODE(INIT-STATE(problem)))
 *     loop do
 *       if EMPTY?(nodes) then return failure
 *       node &lt;- REMOVE-FRONT(nodes)
 *       if GOAL-TEST(problem)(STATE(node)) then return node
 *       nodes &lt;- QING-FUN(nodes, EXPAND(node, OPERATORS(problem)))
 * </pre>
 *
 * The procedure only talks to the problem through the
 * {@link GenericSearchProblem} interface, and the strategy only decides the
 * queuing function (the order in which generated nodes are removed):
 * <ul>
 *   <li>UC - priority queue ordered by g(n);</li>
 *   <li>AS - priority queue ordered by f(n) = g(n) + h(n);</li>
 *   <li>ID - LIFO queue (depth-first) restricted to a depth limit, repeated
 *       with limits 0, 1, 2, ... until a goal is found.</li>
 * </ul>
 *
 * Redundant states are eliminated with a table that keeps, for every
 * {@link GenericSearchProblem#duplicateKey key}, the nodes generated so far.
 * A new node is discarded when a recorded node dominates it: its state is at
 * least as good ({@link GenericSearchProblem#dominates}) and it was reached no
 * worse (path cost for UC/A*, depth for ID). The discarded node cannot lead to
 * anything better than the recorded one, so completeness and optimality are
 * kept. Recorded nodes dominated by a newer node are dropped from the table,
 * and a queued node that was dropped this way is skipped when it is removed
 * from the queue.
 */
public final class GenericSearch {

    private GenericSearch() {
    }

    /** The outcome of a search: the goal node (null on failure) and the expansion count. */
    public static final class Result<S> {
        private final Node<S> goal;
        private final int expandedNodes;

        Result(Node<S> goal, int expandedNodes) {
            this.goal = goal;
            this.expandedNodes = expandedNodes;
        }

        public Node<S> getGoal() {
            return goal;
        }

        public int getExpandedNodes() {
            return expandedNodes;
        }
    }

    /**
     * Searches the problem with the given strategy ("UC", "AS" or "ID").
     */
    public static <S> Result<S> search(GenericSearchProblem<S> problem, String strategy) {
        switch (strategy) {
            case "UC":
                return bestFirst(problem, false);
            case "AS":
                return bestFirst(problem, true);
            case "ID":
                return iterativeDeepening(problem);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    // ------------------------------------------------------------------
    // Queuing functions
    // ------------------------------------------------------------------

    /** The queue of generated-but-not-yet-expanded nodes (the frontier). */
    private interface Frontier<S> {
        void add(Node<S> node);

        Node<S> removeFront();

        boolean isEmpty();
    }

    /**
     * Priority-queue frontier ordered by an evaluation function. Ties are
     * broken by insertion order (FIFO) so the search is deterministic.
     */
    private static final class PriorityFrontier<S> implements Frontier<S> {
        private final PriorityQueue<Entry<S>> queue = new PriorityQueue<>(
                Comparator.<Entry<S>>comparingInt(entry -> entry.priority)
                        .thenComparingLong(entry -> entry.order));
        private final EvaluationFunction<S> evaluation;
        private long insertions;

        PriorityFrontier(EvaluationFunction<S> evaluation) {
            this.evaluation = evaluation;
        }

        @Override
        public void add(Node<S> node) {
            queue.add(new Entry<>(node, evaluation.evaluate(node), insertions++));
        }

        @Override
        public Node<S> removeFront() {
            return queue.remove().node;
        }

        @Override
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        private static final class Entry<S> {
            final Node<S> node;
            final int priority;
            final long order;

            Entry(Node<S> node, int priority, long order) {
                this.node = node;
                this.priority = priority;
                this.order = order;
            }
        }
    }

    /** LIFO frontier used by depth-limited (depth-first) search. */
    private static final class StackFrontier<S> implements Frontier<S> {
        private final Deque<Node<S>> stack = new ArrayDeque<>();

        @Override
        public void add(Node<S> node) {
            stack.push(node);
        }

        @Override
        public Node<S> removeFront() {
            return stack.pop();
        }

        @Override
        public boolean isEmpty() {
            return stack.isEmpty();
        }
    }

    private interface EvaluationFunction<S> {
        int evaluate(Node<S> node);
    }

    /** Extracts the value a redundancy table compares (lower is better). */
    private interface NodeMeasure<S> {
        int of(Node<S> node);
    }

    // ------------------------------------------------------------------
    // Strategies
    // ------------------------------------------------------------------

    /** Uniform cost (f = g) or A* (f = g + h) search. */
    private static <S> Result<S> bestFirst(GenericSearchProblem<S> problem, boolean useHeuristic) {
        EvaluationFunction<S> evaluation = useHeuristic
                ? node -> node.getPathCost() + problem.heuristic(node.getState())
                : Node::getPathCost;
        Outcome<S> outcome = generalSearch(problem, new PriorityFrontier<>(evaluation),
                Integer.MAX_VALUE, Node::getPathCost);
        return new Result<>(outcome.goal, outcome.expandedNodes);
    }

    /**
     * Iterative deepening: depth-limited search with limits 0, 1, 2, ... The
     * expansion count is accumulated over all iterations. If an iteration
     * finishes without ever being cut off by the limit, the whole reachable
     * space was explored and there is no solution.
     */
    private static <S> Result<S> iterativeDeepening(GenericSearchProblem<S> problem) {
        int expanded = 0;
        for (int limit = 0; ; limit++) {
            // The depth, not the path cost, is what matters for redundancy
            // here: a state reached deeper than before has less of the depth
            // budget left.
            Outcome<S> outcome = generalSearch(problem, new StackFrontier<S>(), limit,
                    Node::getDepth);
            expanded += outcome.expandedNodes;
            if (outcome.goal != null || !outcome.cutOff) {
                return new Result<>(outcome.goal, expanded);
            }
        }
    }

    // ------------------------------------------------------------------
    // The general search loop
    // ------------------------------------------------------------------

    private static final class Outcome<S> {
        Node<S> goal;
        int expandedNodes;
        boolean cutOff;
    }

    /**
     * GENERAL-SEARCH with a queuing function (frontier), an optional depth
     * limit, and redundant-state elimination.
     *
     * @param frontier  the queuing function of the strategy
     * @param depthLimit nodes at this depth are goal-tested but not expanded
     * @param measure   how well a node was reached (lower is better)
     */
    private static <S> Outcome<S> generalSearch(GenericSearchProblem<S> problem,
                                                Frontier<S> frontier, int depthLimit,
                                                NodeMeasure<S> measure) {
        Outcome<S> outcome = new Outcome<>();
        Map<Object, List<Node<S>>> recorded = new HashMap<>();

        Node<S> root = Node.root(problem.initialState());
        record(problem, recorded, root, measure);
        frontier.add(root);

        while (!frontier.isEmpty()) {
            Node<S> node = frontier.removeFront();

            // Skip stale nodes: a dominating node was generated after this one.
            if (!isRecorded(problem, recorded, node)) {
                continue;
            }
            if (problem.goalTest(node.getState())) {
                outcome.goal = node;
                return outcome;
            }
            if (node.getDepth() >= depthLimit) {
                outcome.cutOff = true;
                continue;
            }

            outcome.expandedNodes++;
            List<GenericSearchProblem.Successor<S>> successors = problem.successors(node.getState());
            // Children are added in reverse so that a LIFO frontier still
            // tries the operators in their natural order.
            for (int i = successors.size() - 1; i >= 0; i--) {
                Node<S> child = node.child(successors.get(i));
                if (record(problem, recorded, child, measure)) {
                    frontier.add(child);
                }
            }
        }
        return outcome;
    }

    /**
     * Records a node in the redundancy table unless it is redundant.
     *
     * @return false if a recorded node dominates it (it should be discarded)
     */
    private static <S> boolean record(GenericSearchProblem<S> problem,
                                      Map<Object, List<Node<S>>> recorded,
                                      Node<S> node, NodeMeasure<S> measure) {
        List<Node<S>> similar = recorded.computeIfAbsent(
                problem.duplicateKey(node.getState()), key -> new ArrayList<>());
        for (Node<S> other : similar) {
            if (measure.of(other) <= measure.of(node)
                    && problem.dominates(other.getState(), node.getState())) {
                return false;
            }
        }
        // The new node makes every recorded node it dominates redundant.
        similar.removeIf(other -> measure.of(node) <= measure.of(other)
                && problem.dominates(node.getState(), other.getState()));
        similar.add(node);
        return true;
    }

    private static <S> boolean isRecorded(GenericSearchProblem<S> problem,
                                          Map<Object, List<Node<S>>> recorded, Node<S> node) {
        for (Node<S> other : recorded.get(problem.duplicateKey(node.getState()))) {
            if (other == node) {
                return true;
            }
        }
        return false;
    }
}
