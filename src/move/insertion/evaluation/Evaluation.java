package move.insertion.evaluation;

import model.CustomerNode;
import model.Node;

import java.util.List;

/**
 * 插入评估结果（由 {@link EvaluateInsertion} 生成）。
 *
 * <p>这是一个很小的“数据载体”对象，会被不同的 repair 算子（Greedy/Regret/Trips 等）使用。
 * 根据 {@link #insertType} 的不同，它表示的动作也不同：</p>
 * <ul>
 *   <li><b>II（整单插入）</b>：把 {@link #insertingNode} 插入到 {@link #prevNode} 之后</li>
 *   <li><b>SI（拆分配送插入）</b>：把 {@link #insertingNode} 拆成多个 {@link #insertingNodes}，并分别插在
 *       {@link #prevNodesSD} 对应的位置之后</li>
 *   <li><b>STI（拆趟插入）</b>：在插入位置附近插入 depot 边界（见 {@link InsertType}）</li>
 * </ul>
 */
public class Evaluation {
    /** II：整单插入位置（插在该节点之后）。 */
    public Node prevNode;
    /** SI：拆分配送的插入位置（每个 split node 对应一个位置）。 */
    public List<Node> prevNodesSD;
    /** 正在被插入的原始 customer 节点（后续可能被拆成多个 visit）。 */
    public CustomerNode insertingNode;
    /** SI：当 {@link #insertType} 为 {@link InsertType#INSERT_SPLITDELIVERY} 时，需要插入的拆分节点列表。 */
    public List<CustomerNode> insertingNodes;

    /** 目标增量 / 插入代价（越小越好）。 */
    public final int insertCost;

    /** 第二优插入代价（regret 算子需要）。 */
    public int secondLeastInsertCost;
    /** 第三优插入代价（regret-3 需要）。 */
    public int thirdLeastInsertCost;
    /** regret 值（best 与 next-best 的差值）。 */
    public int differenceValue;

    /** 具体要执行的插入动作类型。 */
    public final InsertType insertType;

    public Evaluation(Node prevNode, CustomerNode insertingNode, int insertCost, InsertType insertType) {
        this.insertCost = insertCost;
        this.insertType = insertType;
        this.prevNode = prevNode;
        this.insertingNode = insertingNode;
    }

    public Evaluation(List<Node> SDprevNodes, CustomerNode insertingNode, List<CustomerNode> insertingNodes, int insertCost, InsertType insertType) {
        this.insertCost = insertCost;
        this.insertType = insertType;
        this.prevNodesSD = SDprevNodes;
        this.insertingNodes = insertingNodes;
        this.insertingNode = insertingNode;
    }

    public Evaluation(Node prevNode, CustomerNode insertingNode, int insertCost, int secondLeastInsertCost, InsertType insertType) {
        this.secondLeastInsertCost = secondLeastInsertCost;
        this.insertCost = insertCost;
        this.insertType = insertType;
        this.insertingNode = insertingNode;
        this.prevNode = prevNode;
    }

    public Evaluation(List<Node> SDprevNodes, CustomerNode insertingNode, List<CustomerNode> insertingNodes, int insertCost, int secondLeastInsertCost, InsertType insertType) {
        this.insertCost = insertCost;
        this.secondLeastInsertCost = secondLeastInsertCost;
        this.insertType = insertType;
        this.prevNodesSD = SDprevNodes;
        this.insertingNodes = insertingNodes;
        this.insertingNode = insertingNode;
    }

    public Evaluation(int differenceValue, Evaluation e) {
        this.insertCost = e.insertCost;
        this.secondLeastInsertCost = e.secondLeastInsertCost;
        this.thirdLeastInsertCost = e.thirdLeastInsertCost;
        this.insertType = e.insertType;
        this.insertingNode = e.insertingNode;
        this.prevNode = e.prevNode;
        this.differenceValue = differenceValue;
        this.prevNodesSD = e.prevNodesSD;
        this.insertingNodes = e.insertingNodes;
    }

    public Evaluation(Node prevNode, CustomerNode insertingNode, int insertCost, int secondLeastInsertCost, int thirdLeastInsertCost, InsertType insertType) {
        this.secondLeastInsertCost = secondLeastInsertCost;
        this.thirdLeastInsertCost = thirdLeastInsertCost;
        this.insertCost = insertCost;
        this.insertType = insertType;
        this.insertingNode = insertingNode;
        this.prevNode = prevNode;
    }

    public Evaluation(List<Node> SDprevNodes, CustomerNode insertingNode, List<CustomerNode> insertingNodes, int insertCost, int secondLeastInsertCost, int thirdLeastInsertCost, InsertType insertType) {
        this.insertCost = insertCost;
        this.secondLeastInsertCost = secondLeastInsertCost;
        this.thirdLeastInsertCost = thirdLeastInsertCost;
        this.insertType = insertType;
        this.prevNodesSD = SDprevNodes;
        this.insertingNodes = insertingNodes;
        this.insertingNode = insertingNode;
    }
}


