package move.insertion.operators;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;
import move.insertion.evaluation.EvaluateInsertion;
import move.insertion.evaluation.Evaluation;
import move.insertion.util.InsertionExecutor;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Regret-3 Insertion（RI-3）修复算子。
 *
 * <p>实现：通过 {@link EvaluateInsertion#evaluateR3()} 的 {@link Evaluation#differenceValue} 作为 regret 指标，
 * 每轮选择 differenceValue 最小的 visit 执行插入。</p>
 */
public class Regret3Insertion extends Insertion {
    protected final SimulatedAnnealing sa;
    protected final Instance dataModel;
    private final SplittableRandom rand;

    public Regret3Insertion(SimulatedAnnealing sa, Instance dataModel) {
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
        while (!sa.unServedNodes.isEmpty()) {
            Evaluation eval = regret3Evaluate();
            InsertionExecutor.applyEvaluation(sa, dataModel, eval);
        }
        InsertionExecutor.mergeAdjacentDuplicateVisits(sa, dataModel);
    }

    public Evaluation regret3Evaluate() {
        Evaluation bestEval = null;
        CustomerNode bestC = null;
        int bestDifferenceValue = Integer.MAX_VALUE;

        for (CustomerNode node : sa.unServedNodes) {
            double r = this.rand.nextDouble();
            EvaluateInsertion evaluateInsertion = new EvaluateInsertion(node, dataModel, sa, r);
            Evaluation e = evaluateInsertion.evaluateR3();
            if (e.differenceValue < bestDifferenceValue) {
                bestDifferenceValue = e.differenceValue;
                bestEval = e;
                bestC = node;
            }
        }

        sa.unServedNodes.remove(bestC);
        return bestEval;
    }
}


