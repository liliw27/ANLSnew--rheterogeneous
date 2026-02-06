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
 * Regret-2 Insertion（带 split preference 的变体）修复算子。
 *
 * <p>该算子本质上仍使用 regret-2 的选择指标，但使用一条独立的随机数流（由 RANDOM_SEED 控制复现）。</p>
 */
public class Regret2InsertionSPPref extends Insertion {
    protected final SimulatedAnnealing sa;
    protected final Instance dataModel;
    private final SplittableRandom rand;

    public Regret2InsertionSPPref(SimulatedAnnealing sa, Instance dataModel) {
        this.sa = sa;
        this.dataModel = dataModel;
        this.rand = new SplittableRandom(sa.RANDOM_SEED);
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
            Evaluation eval = regret2Evaluate();
            InsertionExecutor.applyEvaluation(sa, dataModel, eval);
        }
        InsertionExecutor.mergeAdjacentDuplicateVisits(sa, dataModel);
    }

    public Evaluation regret2Evaluate() {
        Evaluation bestEval = null;
        CustomerNode bestC = null;
        int bestDifferenceValue = Integer.MAX_VALUE;

        for (CustomerNode node : sa.unServedNodes) {
            double r = this.rand.nextDouble();
            EvaluateInsertion evaluateInsertion = new EvaluateInsertion(node, dataModel, sa, r);
            Evaluation e = evaluateInsertion.evaluateR2();
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


