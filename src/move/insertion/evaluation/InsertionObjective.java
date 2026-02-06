package move.insertion.evaluation;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Node;
import model.Instance;

import java.util.Arrays;

/**
 * 插入代价/目标增量计算工具（从 {@link EvaluateInsertion} 中抽出）。
 *
 * <p>目的：让 {@link EvaluateInsertion} 更聚焦于“枚举候选插法 + 选择最优”，
 * 把可复用、可单独理解的公式计算抽到独立模块。</p>
 */
final class InsertionObjective {

    private InsertionObjective() {}

    /**
     * 在 prevNode 与 prevNode.next 之间插入 customer（不插 depot）时的目标增量。
     *
     * <p>增量 = 路程增量 + makespan 增量 * alpha。</p>
     */
    static int objAfterInsertCustomer(Node prevNode, CustomerNode customerNode, Instance dataModel, SimulatedAnnealing sa) {
        Node nextNode = prevNode.next;
        int insertCost = dataModel.distanceMatrix[prevNode.index][customerNode.index]
                + dataModel.distanceMatrix[customerNode.index][nextNode.index]
                - dataModel.distanceMatrix[prevNode.index][nextNode.index];

        int oldMakespan = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();
        return Math.max(0, prevNode.route.duration + insertCost - oldMakespan) * sa.MAKESPAN_MULTIPLIER + insertCost;
    }

    /**
     * 在 prevNode 与 prevNode.next 之间插入 customer，并在 customer 后插入 depot（... prev -> customer -> depot -> next ...）的目标增量。
     *
     * <p>用于 MTID 相关候选。</p>
     */
    static int objAfterInsertCustomerThenDepot(Node prevNode, CustomerNode customerNode, Instance dataModel, SimulatedAnnealing sa) {
        Node nextNode = prevNode.next;
        int insertCost = dataModel.distanceMatrix[prevNode.index][customerNode.index]
                + dataModel.distanceMatrix[customerNode.index][0]
                + dataModel.distanceMatrix[0][nextNode.index]
                - dataModel.distanceMatrix[prevNode.index][nextNode.index];

        int oldMakespan = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();
        return Math.max(0, prevNode.route.duration + insertCost - oldMakespan) * sa.MAKESPAN_MULTIPLIER + insertCost;
    }

    /**
     * 在 prevNode 与 prevNode.next 之间插入 depot，并在 depot 后插入 customer（... prev -> depot -> customer -> next ...）的目标增量。
     *
     * <p>用于 MTDI 相关候选。</p>
     */
    static int objAfterInsertDepotThenCustomer(Node prevNode, CustomerNode customerNode, Instance dataModel, SimulatedAnnealing sa) {
        Node nextNode = prevNode.next;
        int insertCost = dataModel.distanceMatrix[prevNode.index][0]
                + dataModel.distanceMatrix[0][customerNode.index]
                + dataModel.distanceMatrix[customerNode.index][nextNode.index]
                - dataModel.distanceMatrix[prevNode.index][nextNode.index];

        int oldMakespan = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();
        return Math.max(0, prevNode.route.duration + insertCost - oldMakespan) * sa.MAKESPAN_MULTIPLIER + insertCost;
    }
}


