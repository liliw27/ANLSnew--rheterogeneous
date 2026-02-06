package move.insertion.evaluation;

import SA.SimulatedAnnealing;
import model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 插入评估器：给定一个待插入的 {@link CustomerNode}（一次 visit），在当前解中寻找“最划算”的插入方式与位置。
 *
 * <p><b>与论文对齐</b>：论文中 repair 会在以下三种策略之间选择（本类将三者统一比较）：</p>
 * <ul>
 *   <li><b>II（Integral Insertion）</b>：将 visit 完整插入某个 trip（代码：{@link InsertType#INSERT_NODE}）</li>
 *   <li><b>SI（Split Insertion）</b>：将 visit 拆成多个小 visit，分散插入多个 trip（{@link InsertType#INSERT_SPLITDELIVERY}）</li>
 *   <li><b>STI（Split Trip Insertion）</b>：通过插入 depot 将一个 trip 拆成两趟/或新开一趟，再插入（
 *       {@link InsertType#INSERT_MULTITRIPID}/{@link InsertType#INSERT_MULTITRIPDI}）</li>
 * </ul>
 *
 * <p><b>关键思想</b>：本类不直接修改解结构，而是枚举候选插法并计算“目标增量/插入代价”。真正的插入动作由
 * Greedy/Regret/Trips 等算子在拿到 {@link Evaluation} 后执行。</p>
 */
public class EvaluateInsertion {
    public final CustomerNode customerNode;
    public final Instance dataModel;
    public final SimulatedAnnealing sa;

    /**
     * nonSplitSegments：能在“资源可行”的情况下完整满足 customerNode 的 segment 集合（适用于 II）。
     * splitSegments：无法一次性完整满足，但可能提供部分供给的 segment 集合（适用于 SI / STI）。
     */
    public List<Segment> nonSplitSegments = new ArrayList<>();
    public List<Segment> splitSegments = new ArrayList<>();
    /** 对应 splitSegments：每个 segment 在当前剩余资源下“最多能供应”的多产品数量向量（启发式计算）。 */
    public List<Integer[]> maxQSplitSegments = new ArrayList<>();

    int leastInsertCostSD = Integer.MAX_VALUE;
    int thirdInsertCostSD = Integer.MAX_VALUE;
    int secondInsertCostSD = Integer.MAX_VALUE;
    double r;

    public EvaluateInsertion(CustomerNode customerNode, Instance dataModel, SimulatedAnnealing simulatedAnnealing, double r) {
        this.customerNode = customerNode;
        this.dataModel = dataModel;
        this.sa = simulatedAnnealing;
        this.r = r;
    }

    public Evaluation evaluate() {
        // 1) 将当前解中的所有 segment 划分成两类：
        // - nonSplitSegments：可“整单插入”(II) 完整容纳该 visit 的需求
        // - splitSegments：无法整单容纳，但可能提供部分供给（用于 SI / STI）
        this.divideSegments();

        // 2) 评估 II（Integral Insertion / 整单插入）
        Evaluation evaluationNonS = evaluateNonS();

        // 3) 评估 SI（Split Insertion / 拆分配送插入多个 segment）
        Evaluation evaluationSD = evaluateSD();

        // 4) 评估 STI（Split-Trip Insertion / 拆趟插入：插入 depot，改变 trip 切分）
        Evaluation evaluationMT = evaluateMT();

        int min = Math.min(evaluationNonS.insertCost, evaluationSD.insertCost);
        int min1 = Math.min(min, evaluationMT.insertCost);

        if (min1 == evaluationNonS.insertCost) {
            return evaluationNonS;
        } else if (min1 == evaluationSD.insertCost) {
            return evaluationSD;
        } else {
            return evaluationMT;
        }
    }

    public Evaluation evaluateR2() {
        this.divideSegments();

        Evaluation evaluationNonS = evaluateNonSR2();
        Evaluation evaluationSD = evaluateSDR2();
        Evaluation evaluationMT = evaluateMTR2();

        int[] values = new int[6];
        values[0] = evaluationNonS.insertCost;
        values[1] = evaluationNonS.secondLeastInsertCost;
        values[2] = evaluationSD.insertCost;
        values[3] = evaluationSD.secondLeastInsertCost;
        values[4] = evaluationMT.insertCost;
        values[5] = evaluationMT.secondLeastInsertCost;
        Arrays.sort(values);

        int difference = values[0] - values[1];

        if (values[0] == evaluationNonS.insertCost) {
            return new Evaluation(difference, evaluationNonS);
        } else if (values[0] == evaluationSD.insertCost || values[0] == evaluationSD.secondLeastInsertCost) {
            return new Evaluation(difference, evaluationSD);
        } else {
            return new Evaluation(difference, evaluationMT);
        }
    }

    public Evaluation evaluateR3() {
        this.divideSegments();

        Evaluation evaluationNonS = evaluateNonSR3();
        Evaluation evaluationSD = evaluateSDR3();
        Evaluation evaluationMT = evaluateMTR3();

        int[] values = new int[9];
        values[0] = evaluationNonS.insertCost;
        values[1] = evaluationNonS.secondLeastInsertCost;
        values[2] = evaluationNonS.thirdLeastInsertCost;
        values[3] = evaluationSD.insertCost;
        values[4] = evaluationSD.secondLeastInsertCost;
        values[5] = evaluationSD.thirdLeastInsertCost;
        values[6] = evaluationMT.insertCost;
        values[7] = evaluationMT.secondLeastInsertCost;
        values[8] = evaluationMT.thirdLeastInsertCost;
        Arrays.sort(values);

        int difference = values[0] - values[2];

        if (values[0] == evaluationNonS.insertCost) {
            return new Evaluation(difference, evaluationNonS);
        } else if (values[0] == evaluationSD.insertCost || values[0] == evaluationSD.secondLeastInsertCost || values[0] == evaluationSD.thirdLeastInsertCost) {
            return new Evaluation(difference, evaluationSD);
        } else {
            return new Evaluation(difference, evaluationMT);
        }
    }

    /**
     * 在两种拆趟策略之间二选一。
     *
     * <p>历史行为：用构造时给定的随机数 {@link #r} 做阈值判断，r<=0.5 选 ID，否则选 DI。</p>
     */
    private Evaluation evaluateMT() {
        return (r <= 0.5)
                ? MultiTripInsertionEvaluator.bestMTID(customerNode, dataModel, sa, splitSegments)
                : MultiTripInsertionEvaluator.bestMTDI(customerNode, dataModel, sa, splitSegments);
    }

    private Evaluation evaluateMTR2() {
        return (r <= 0.5)
                ? MultiTripInsertionEvaluator.bestAndSecondMTID(customerNode, dataModel, sa, splitSegments)
                : MultiTripInsertionEvaluator.bestAndSecondMTDI(customerNode, dataModel, sa, splitSegments);
    }

    private Evaluation evaluateMTR3() {
        return (r <= 0.5)
                ? MultiTripInsertionEvaluator.bestSecondThirdMTID(customerNode, dataModel, sa, splitSegments)
                : MultiTripInsertionEvaluator.bestSecondThirdMTDI(customerNode, dataModel, sa, splitSegments);
    }

    private void divideSegments() {
        SegmentSupplyEstimator.divideSegments(
                customerNode,
                dataModel,
                sa,
                nonSplitSegments,
                splitSegments,
                maxQSplitSegments
        );
    }

    public int getObjAfterInsertNode(Node prevNode) {
        return InsertionObjective.objAfterInsertCustomer(prevNode, customerNode, dataModel, sa);
    }

    public int getObjAfterInsertNodeDpot(Node prevNode) {
        return InsertionObjective.objAfterInsertCustomerThenDepot(prevNode, customerNode, dataModel, sa);
    }

    public int getObjAfterInsertDpotNode(Node prevNode) {
        return InsertionObjective.objAfterInsertDepotThenCustomer(prevNode, customerNode, dataModel, sa);
    }

    public Evaluation evaluateNonS() {
        return IntegralInsertionEvaluator.best(customerNode, dataModel, sa, nonSplitSegments);
    }

    public Evaluation evaluateSD() {
        boolean ifSplitFeasible = isSplitInsertionFeasible();
        List<Node> bestPrevNodeSD = new ArrayList<>();
        List<CustomerNode> insertingNodes = new ArrayList<>();
        if (ifSplitFeasible) {
            List<SplitInsertionPlanner.RankedSplitSegment> rankedSplitSegments =
                    SplitInsertionPlanner.rankSplitSegments(customerNode, splitSegments, maxQSplitSegments, dataModel, sa);

            SplitInsertionPlanner.SplitPlan plan =
                    SplitInsertionPlanner.buildSplitPlan(customerNode, rankedSplitSegments, 0, dataModel);

            insertingNodes.addAll(plan.insertingNodes);
            bestPrevNodeSD.addAll(plan.bestPrevNodes);
            leastInsertCostSD = SplitInsertionPlanner.computeObjectiveIncrement(plan, dataModel, sa);
        }
        return new Evaluation(bestPrevNodeSD, customerNode, insertingNodes, leastInsertCostSD, InsertType.INSERT_SPLITDELIVERY);
    }

    /**
     * SI 可行性检查：对每个产品 p，要求 \(\sum_{seg \in splitSegments} maxQ(seg,p) \ge demand(p)\)。
     */
    private boolean isSplitInsertionFeasible() {
        int[] sumResidualForProd = new int[dataModel.nrProducts];
        for (int i = 0; i < splitSegments.size(); i++) {
            for (int p = 0; p < dataModel.nrProducts; p++) {
                sumResidualForProd[p] += maxQSplitSegments.get(i)[p];
            }
        }
        return supplyCoversDemand(sumResidualForProd);
    }

    private boolean supplyCoversDemand(int[] supply) {
        for (int p = 0; p < dataModel.nrProducts; p++) {
            if (supply[p] < customerNode.deliveryQuantity[p]) {
                return false;
            }
        }
        return true;
    }

    public Evaluation evaluateNonSR2() {
        return IntegralInsertionEvaluator.bestAndSecond(customerNode, dataModel, sa, nonSplitSegments);
    }

    public Evaluation evaluateSDR2() {
        boolean ifSplitFeasible = isSplitInsertionFeasible();
        List<Node> bestPrevNodeSD = new ArrayList<>();
        List<CustomerNode> insertingNodes = new ArrayList<>();
        List<Node> bestPrevNodeSDR2 = new ArrayList<>();
        List<CustomerNode> insertingNodesR2 = new ArrayList<>();
        if (ifSplitFeasible) {
            List<SplitInsertionPlanner.RankedSplitSegment> rankedSplitSegments =
                    SplitInsertionPlanner.rankSplitSegments(customerNode, splitSegments, maxQSplitSegments, dataModel, sa);

            SplitInsertionPlanner.SplitPlan bestPlan =
                    SplitInsertionPlanner.buildSplitPlan(customerNode, rankedSplitSegments, 0, dataModel);
            insertingNodes.addAll(bestPlan.insertingNodes);
            bestPrevNodeSD.addAll(bestPlan.bestPrevNodes);
            leastInsertCostSD = SplitInsertionPlanner.computeObjectiveIncrement(bestPlan, dataModel, sa);

            int[] remainingSupply = SplitInsertionPlanner.sumMaxSupplyFrom(rankedSplitSegments, 1, dataModel.nrProducts);
            ifSplitFeasible = supplyCoversDemand(remainingSupply);
            if (ifSplitFeasible) {
                SplitInsertionPlanner.SplitPlan secondPlan =
                        SplitInsertionPlanner.buildSplitPlan(customerNode, rankedSplitSegments, 1, dataModel);
                insertingNodesR2.addAll(secondPlan.insertingNodes);
                bestPrevNodeSDR2.addAll(secondPlan.bestPrevNodes);
                secondInsertCostSD = SplitInsertionPlanner.computeObjectiveIncrement(secondPlan, dataModel, sa);
            }
        }

        return new Evaluation(bestPrevNodeSD, customerNode, insertingNodes, leastInsertCostSD, secondInsertCostSD, InsertType.INSERT_SPLITDELIVERY);
    }

    public Evaluation evaluateNonSR3() {
        return IntegralInsertionEvaluator.bestSecondThird(customerNode, dataModel, sa, nonSplitSegments);
    }

    public Evaluation evaluateSDR3() {
        boolean ifSplitFeasible = isSplitInsertionFeasible();
        List<Node> bestPrevNodeSD = new ArrayList<>();
        List<CustomerNode> insertingNodes = new ArrayList<>();
        List<Node> bestPrevNodeSDR2 = new ArrayList<>();
        List<CustomerNode> insertingNodesR2 = new ArrayList<>();
        List<Node> bestPrevNodeSDR3 = new ArrayList<>();
        List<CustomerNode> insertingNodesR3 = new ArrayList<>();
        if (ifSplitFeasible) {
            List<SplitInsertionPlanner.RankedSplitSegment> rankedSplitSegments =
                    SplitInsertionPlanner.rankSplitSegments(customerNode, splitSegments, maxQSplitSegments, dataModel, sa);

            SplitInsertionPlanner.SplitPlan bestPlan =
                    SplitInsertionPlanner.buildSplitPlan(customerNode, rankedSplitSegments, 0, dataModel);
            insertingNodes.addAll(bestPlan.insertingNodes);
            bestPrevNodeSD.addAll(bestPlan.bestPrevNodes);
            leastInsertCostSD = SplitInsertionPlanner.computeObjectiveIncrement(bestPlan, dataModel, sa);

            int[] remainingSupply2 = SplitInsertionPlanner.sumMaxSupplyFrom(rankedSplitSegments, 1, dataModel.nrProducts);
            ifSplitFeasible = supplyCoversDemand(remainingSupply2);
            if (ifSplitFeasible) {
                SplitInsertionPlanner.SplitPlan secondPlan =
                        SplitInsertionPlanner.buildSplitPlan(customerNode, rankedSplitSegments, 1, dataModel);
                insertingNodesR2.addAll(secondPlan.insertingNodes);
                bestPrevNodeSDR2.addAll(secondPlan.bestPrevNodes);
                secondInsertCostSD = SplitInsertionPlanner.computeObjectiveIncrement(secondPlan, dataModel, sa);
            }

            int[] remainingSupply3 = SplitInsertionPlanner.sumMaxSupplyFrom(rankedSplitSegments, 2, dataModel.nrProducts);
            ifSplitFeasible = supplyCoversDemand(remainingSupply3);
            if (ifSplitFeasible) {
                SplitInsertionPlanner.SplitPlan thirdPlan =
                        SplitInsertionPlanner.buildSplitPlan(customerNode, rankedSplitSegments, 2, dataModel);
                insertingNodesR3.addAll(thirdPlan.insertingNodes);
                bestPrevNodeSDR3.addAll(thirdPlan.bestPrevNodes);
                thirdInsertCostSD = SplitInsertionPlanner.computeObjectiveIncrement(thirdPlan, dataModel, sa);
            }
        }

        return new Evaluation(bestPrevNodeSD, customerNode, insertingNodes, leastInsertCostSD, secondInsertCostSD, thirdInsertCostSD, InsertType.INSERT_SPLITDELIVERY);
    }
}


