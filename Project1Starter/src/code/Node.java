package code;

/**
 * Search-tree node ADT (as presented in class).
 *
 * A node wraps a problem state together with the bookkeeping needed by the
 * search: the parent node, the operator that generated it, its depth in the
 * tree and the accumulated path cost g(n) from the root.
 *
 * @param <S> the type of the problem states
 */
public class Node<S> {

    /** The problem state this node represents. */
    private final S state;
    /** The node this one was generated from (null for the root). */
    private final Node<S> parent;
    /** The operator applied to the parent to get here (null for the root). */
    private final String operator;
    /** Number of operators from the root to this node. */
    private final int depth;
    /** Accumulated path cost g(n) from the root to this node. */
    private final int pathCost;

    /** Creates a node with all of its fields given explicitly. */
    public Node(S state, Node<S> parent, String operator, int depth, int pathCost) {
        this.state = state;
        this.parent = parent;
        this.operator = operator;
        this.depth = depth;
        this.pathCost = pathCost;
    }

    /** Creates the root node of a search tree. */
    public static <S> Node<S> root(S state) {
        return new Node<>(state, null, null, 0, 0);
    }

    /** Creates the child reached from this node by applying a successor. */
    public Node<S> child(GenericSearchProblem.Successor<S> successor) {
        return new Node<>(successor.getState(), this, successor.getOperator(),
                depth + 1, pathCost + successor.getStepCost());
    }

    /** The state stored in this node. */
    public S getState() {
        return state;
    }

    /** The parent node, or null for the root. */
    public Node<S> getParent() {
        return parent;
    }

    /** The operator that generated this node, or null for the root. */
    public String getOperator() {
        return operator;
    }

    /** The depth of this node in the search tree (root = 0). */
    public int getDepth() {
        return depth;
    }

    /** The path cost g(n) from the root to this node. */
    public int getPathCost() {
        return pathCost;
    }

    /**
     * Reconstructs the plan by walking up the parent pointers to the root.
     *
     * @return the operators from the root to this node, separated by commas
     */
    public String plan() {
        StringBuilder plan = new StringBuilder();
        // Walk from this node up to the root, prepending each operator.
        for (Node<S> current = this; current.parent != null; current = current.parent) {
            if (plan.length() > 0) {
                plan.insert(0, ',');
            }
            plan.insert(0, current.operator);
        }
        return plan.toString();
    }
}
