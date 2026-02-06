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
 * Greedy Insertion（GI）修复算子。
 *
 * <p><b>与论文对齐</b>：对应 repair operators 中的 Greedy Insertion（GI）。采用“全局贪心”策略：</p>
 * <ul>
 *   <li>对每个待插入 visit（{@link SimulatedAnnealing#unServedNodes}），调用 {@link EvaluateInsertion} 评估其最优插入方式</li>
 *   <li>选择插入代价（目标增量）最小的那个 visit 先插入</li>
 *   <li>重复直到 unServedNodes 为空</li>
 * </ul>
 *
 * <p>插入动作由 {@link Evaluation#insertType} 决定（在 {@link EvaluateInsertion} 中比较 II/SI/STI）。</p>
 */
public class GreedyInsertion extends Insertion {
    protected final SimulatedAnnealing sa;
    protected final Instance dataModel;
    private final SplittableRandom rand;

    public GreedyInsertion(SimulatedAnnealing sa, Instance dataModel) {
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
            // 选择“下一个要插入的 visit”：全局最小插入代价
            Evaluation eval = greedyEvaluate();

            // 统计：该次插入使用了哪种插入策略（II/SI/STI）
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

        // 后处理：合并相邻重复 visit（同 customer index / depot）
        InsertionExecutor.mergeAdjacentDuplicateVisits(sa, dataModel);
    }

    public Evaluation greedyEvaluate() {
        Evaluation bestEval = null;
        CustomerNode bestC = null;
        int leastInsertCost = Integer.MAX_VALUE;

        // 枚举每个未服务 visit，取插入代价（目标增量）最小者
        for (CustomerNode node : sa.unServedNodes) {
            double r = this.rand.nextDouble();
            EvaluateInsertion evaluateInsertion = new EvaluateInsertion(node, dataModel, sa, r);
            Evaluation e = evaluateInsertion.evaluate();
            if (e.insertCost < leastInsertCost) {
                leastInsertCost = e.insertCost;
                bestEval = e;
                bestC = node;
            }
        }
        sa.unServedNodes.remove(bestC);
        return bestEval;
    }
}


