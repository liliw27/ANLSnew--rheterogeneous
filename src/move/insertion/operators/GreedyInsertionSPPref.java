package move.insertion.operators;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;
import move.insertion.evaluation.EvaluateInsertion;
import move.insertion.evaluation.Evaluation;
import move.insertion.evaluation.InsertType;
import move.insertion.util.InsertionExecutor;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Greedy Insertion with Split Preference（GSP）修复算子。
 *
 * <p><b>与 {@link GreedyInsertion} 的差异</b>：在选择“下一个要插入的 visit”时，偏好 split insertion：</p>
 * <ul>
 *   <li>如果当前 best 已经是 split，则只在其它 split 候选中比较代价</li>
 *   <li>否则，只要出现任何 split 候选，就会优先选择 split 候选（即使其代价不最小）</li>
 * </ul>
 */
public class GreedyInsertionSPPref extends Insertion {
    protected final SimulatedAnnealing sa;
    protected final Instance dataModel;
    private final SplittableRandom rand;

    public GreedyInsertionSPPref(SimulatedAnnealing sa, Instance dataModel) {
        this.sa = sa;
        this.dataModel = dataModel;
        this.rand = sa.rand;
    }

    @Override
    public int getObjective() {
        int makespanAfterMove = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();
        int sumOfComplTimesAfterMove = Arrays.stream(sa.routes).mapToInt(r -> r.duration).sum();
        return makespanAfterMove * sa.MAKESPAN_MULTIPLIER + sumOfComplTimesAfterMove;
    }

    @Override
    public void move() {
        if (sa.unServedNodes.isEmpty()) {
            sa.count5++;
        }

        while (!sa.unServedNodes.isEmpty()) {
            Evaluation eval = greedyEvaluate();

            if (eval.insertType == InsertType.INSERT_NODE) {
                sa.count1++;
            } else if (eval.insertType == InsertType.INSERT_SPLITDELIVERY) {
                sa.count2++;
            } else if (eval.insertType == InsertType.INSERT_MULTITRIPID) {
                sa.count3++;
            } else if (eval.insertType == InsertType.INSERT_MULTITRIPDI) {
                sa.count4++;
            }

            InsertionExecutor.applyEvaluation(sa, dataModel, eval);
        }

        InsertionExecutor.mergeAdjacentDuplicateVisits(sa, dataModel);
    }

    public Evaluation greedyEvaluate() {
        Evaluation bestEval;
        CustomerNode bestC;
        int leastInsertCost;

        // 先用第一个未服务节点初始化 best（保持旧实现行为）
        CustomerNode node0 = sa.unServedNodes.get(0);
        double r0 = this.rand.nextDouble();
        EvaluateInsertion evaluateInsertion0 = new EvaluateInsertion(node0, dataModel, sa, r0);
        Evaluation e0 = evaluateInsertion0.evaluate();
        bestEval = e0;
        bestC = node0;
        leastInsertCost = e0.insertCost;

        for (int i = 1; i < sa.unServedNodes.size(); i++) {
            CustomerNode node = sa.unServedNodes.get(i);
            double r = this.rand.nextDouble();
            EvaluateInsertion evaluateInsertion = new EvaluateInsertion(node, dataModel, sa, r);
            Evaluation e = evaluateInsertion.evaluate();

            if (bestEval.insertType == InsertType.INSERT_SPLITDELIVERY) {
                if (e.insertType == InsertType.INSERT_SPLITDELIVERY && e.insertCost < leastInsertCost) {
                    leastInsertCost = e.insertCost;
                    bestEval = e;
                    bestC = node;
                }
            } else if (e.insertType == InsertType.INSERT_SPLITDELIVERY || e.insertCost < leastInsertCost) {
                leastInsertCost = e.insertCost;
                bestEval = e;
                bestC = node;
            }
        }
        sa.unServedNodes.remove(bestC);
        return bestEval;
    }
}


