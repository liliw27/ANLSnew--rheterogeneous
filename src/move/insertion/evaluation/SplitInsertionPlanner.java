package move.insertion.evaluation;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;
import model.Node;
import model.Segment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SI（Split Insertion / 拆分配送插入）相关的辅助模块（从 {@link EvaluateInsertion} 中抽出）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>对可拆分的 segment 做排序（rank）</li>
 *   <li>生成拆分插入计划（每个 segment 承担多少 SDQ）</li>
 *   <li>根据插入计划计算目标增量（总路程 + makespan 罚项）</li>
 * </ul>
 *
 * <p>该类是 package-private：只服务于 insertion 模块内部，避免暴露过多入口。</p>
 */
final class SplitInsertionPlanner {

    private SplitInsertionPlanner() {}

    /** ranked segment: 用于 SI 的排序。 */
    static final class RankedSplitSegment implements Comparable<RankedSplitSegment> {
        final Segment segment;
        final int leastInsertCost;
        final int maxQuantitySupply;
        final Integer[] maxQSplitSegments;
        final Node bestPrevNode;
        final Double rankNumber;

        RankedSplitSegment(Segment segment,
                           int leastInsertCost,
                           Integer[] maxQSplitSegments,
                           int maxQuantitySupply,
                           Node bestPrevNode,
                           Double rankNumber) {
            this.segment = segment;
            this.leastInsertCost = leastInsertCost;
            this.maxQSplitSegments = maxQSplitSegments;
            this.maxQuantitySupply = maxQuantitySupply;
            this.bestPrevNode = bestPrevNode;
            this.rankNumber = rankNumber;
        }

        @Override
        public int compareTo(RankedSplitSegment other) {
            return this.rankNumber.compareTo(other.rankNumber);
        }
    }

    /** 一次拆分插入计划：生成的新 visit 列表，以及每个 visit 对应的插入前驱节点。 */
    static final class SplitPlan {
        final List<CustomerNode> insertingNodes;
        final List<Node> bestPrevNodes;

        SplitPlan(List<CustomerNode> insertingNodes, List<Node> bestPrevNodes) {
            this.insertingNodes = insertingNodes;
            this.bestPrevNodes = bestPrevNodes;
        }
    }

    static List<RankedSplitSegment> rankSplitSegments(CustomerNode customerNode,
                                                      List<Segment> splitSegments,
                                                      List<Integer[]> maxQSplitSegments,
                                                      Instance dataModel,
                                                      SimulatedAnnealing sa) {
        List<RankedSplitSegment> ranked = new ArrayList<>();
        for (int i = 0; i < splitSegments.size(); i++) {
            Segment segment = splitSegments.get(i);

            int leastInsertCost = Integer.MAX_VALUE;
            Node bestPrevNode = segment.segmentHead;

            // 保持与原实现一致的遍历边界（不要“优化”边界，否则可能改变候选集合）
            for (Node node = segment.segmentHead; node.segment != segment.next && node.next != null; node = node.next) {
                int insertCost = InsertionObjective.objAfterInsertCustomer(node, customerNode, dataModel, sa);
                if (insertCost < leastInsertCost) {
                    leastInsertCost = insertCost;
                    bestPrevNode = node;
                }
            }

            int maxQuantitySupply = 0;
            for (int p = 0; p < dataModel.nrProducts; p++) {
                maxQuantitySupply += maxQSplitSegments.get(i)[p];
            }

            // 排序指标（论文/原实现）：minCost / maxSupply
            double rankNumber = (leastInsertCost * 1.0) / (maxQuantitySupply * 1.0);
            ranked.add(new RankedSplitSegment(segment, leastInsertCost, maxQSplitSegments.get(i), maxQuantitySupply, bestPrevNode, rankNumber));
        }

        Collections.sort(ranked);
        return ranked;
    }

    /** 从 ranked 列表的 startIndex 开始生成拆分插入计划（用于 best/2nd/3rd 等不同起点）。 */
    static SplitPlan buildSplitPlan(CustomerNode original, List<RankedSplitSegment> ranked, int startIndex, Instance dataModel) {
        List<Node> bestPrevNodes = new ArrayList<>();
        List<CustomerNode> insertingNodes = new ArrayList<>();

        int[] demand = Arrays.copyOf(original.deliveryQuantity, original.deliveryQuantity.length);
        int count = startIndex;

        boolean continueSplitPlan = true;
        Label:
        while (continueSplitPlan) {
            if (ranked.isEmpty()) break;
            if (count >= ranked.size()) break; // 理论上不应发生：可行性检查应避免

            int[] SDQ = new int[dataModel.nrProducts];
            for (int p = 0; p < dataModel.nrProducts; p++) {
                SDQ[p] = Math.min(demand[p], ranked.get(count).maxQSplitSegments[p]);
                demand[p] -= SDQ[p];
            }

            int q = 0;
            for (int p = 0; p < dataModel.nrProducts; p++) q += SDQ[p];
            if (q > 0) {
                insertingNodes.add(new CustomerNode(original.index, SDQ));
                bestPrevNodes.add(ranked.get(count).bestPrevNode);
            }

            for (int p = 0; p < dataModel.nrProducts; p++) {
                if (demand[p] > 0) {
                    count++;
                    continue Label;
                }
            }
            continueSplitPlan = false;
        }

        return new SplitPlan(insertingNodes, bestPrevNodes);
    }

    /** 计算该拆分插入计划的目标增量（总路程增量 + makespan 增量 * alpha）。 */
    static int computeObjectiveIncrement(SplitPlan plan, Instance dataModel, SimulatedAnnealing sa) {
        int[] insertCosts = new int[plan.insertingNodes.size()];
        int[] routeDuration = new int[sa.routes.length];

        for (int i = 0; i < plan.insertingNodes.size(); i++) {
            Node prevNode = plan.bestPrevNodes.get(i);
            Node nextNode = prevNode.next;
            insertCosts[i] = dataModel.distanceMatrix[prevNode.index][plan.insertingNodes.get(i).index]
                    + dataModel.distanceMatrix[plan.insertingNodes.get(i).index][nextNode.index]
                    - dataModel.distanceMatrix[prevNode.index][nextNode.index];
        }

        for (int i = 0; i < routeDuration.length; i++) {
            routeDuration[i] = sa.routes[i].duration;
            for (int j = 0; j < plan.insertingNodes.size(); j++) {
                if (plan.bestPrevNodes.get(j).route.ID == i) {
                    routeDuration[i] += insertCosts[j];
                }
            }
        }

        int routeDMax = Arrays.stream(routeDuration).max().getAsInt();
        int oldMakespan = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();

        int totalDistanceIncrease = Arrays.stream(insertCosts).sum();
        int makespanPenalty = Math.max(0, (routeDMax - oldMakespan)) * sa.MAKESPAN_MULTIPLIER;
        return totalDistanceIncrease + makespanPenalty;
    }

    /** 从 ranked 的 startIndex 开始，计算“最大供给量总和”（按产品维度）。 */
    static int[] sumMaxSupplyFrom(List<RankedSplitSegment> ranked, int startIndex, int nrProducts) {
        int[] sum = new int[nrProducts];
        for (int i = startIndex; i < ranked.size(); i++) {
            for (int p = 0; p < nrProducts; p++) {
                sum[p] += ranked.get(i).maxQSplitSegments[p];
            }
        }
        return sum;
    }
}


