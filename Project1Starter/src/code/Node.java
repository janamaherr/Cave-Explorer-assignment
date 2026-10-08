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

    private final S state;
    private final Node<S> parent;
    private final String operator;
    private final int depth;
    private final int pathCost;

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

    public S getState() {
        return state;
    }

    public Node<S> getParent() {
        return parent;
    }

    public String getOperator() {
        return operator;
    }

    public int getDepth() {
        return depth;
    }

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
        for (Node<S> current = this; current.parent != null; current = current.parent) {
            if (plan.length() > 0) {
                plan.insert(0, ',');
            }
            plan.insert(0, current.operator);
        }
        return plan.toString();
    }
}
