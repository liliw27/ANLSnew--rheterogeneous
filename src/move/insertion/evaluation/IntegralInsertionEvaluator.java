package move.insertion.evaluation;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;
import model.Node;
import model.Segment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * II（Integral Insertion / 整单插入）的评估逻辑。
 *
 * <p>为什么要抽出来？因为 {@link EvaluateInsertion} 同时承载了 II/SI/STI 三套评估，会非常长。</p>
 *
 * <p><b>Regret-2 / Regret-3 的“备选插入”语义</b>：</p>
 * <ul>
 *   <li>我们先对每个 {@link Segment} 找到“该 segment 内的最优插入位置与代价”。</li>
 *   <li>然后在不同 segment 之间对这些最优代价排序，从而得到 best / second / third。</li>
 * </ul>
 *
 * <p>这样写比在所有位置上直接做“跨 segment 去重”的 if-else 更清晰，也更不容易写错。</p>
 */
final class IntegralInsertionEvaluator {

    private IntegralInsertionEvaluator() {}

    static Evaluation best(CustomerNode customerNode,
                           Instance dataModel,
                           SimulatedAnnealing sa,
                           List<Segment> nonSplitSegments) {
        List<SegmentCandidate> candidates = computeBestCandidatePerSegment(customerNode, dataModel, sa, nonSplitSegments);
        SegmentCandidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.bestCost;
        Node bestPrev = (best == null) ? null : best.bestPrevNode;
        return new Evaluation(bestPrev, customerNode, bestCost, InsertType.INSERT_NODE);
    }

    static Evaluation bestAndSecond(CustomerNode customerNode,
                                    Instance dataModel,
                                    SimulatedAnnealing sa,
                                    List<Segment> nonSplitSegments) {
        List<SegmentCandidate> candidates = computeBestCandidatePerSegment(customerNode, dataModel, sa, nonSplitSegments);
        SegmentCandidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.bestCost;
        Node bestPrev = (best == null) ? null : best.bestPrevNode;

        int secondCost = (candidates.size() >= 2) ? candidates.get(1).bestCost : Integer.MAX_VALUE;
        return new Evaluation(bestPrev, customerNode, bestCost, secondCost, InsertType.INSERT_NODE);
    }

    static Evaluation bestSecondThird(CustomerNode customerNode,
                                      Instance dataModel,
                                      SimulatedAnnealing sa,
                                      List<Segment> nonSplitSegments) {
        List<SegmentCandidate> candidates = computeBestCandidatePerSegment(customerNode, dataModel, sa, nonSplitSegments);
        SegmentCandidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.bestCost;
        Node bestPrev = (best == null) ? null : best.bestPrevNode;

        int secondCost = (candidates.size() >= 2) ? candidates.get(1).bestCost : Integer.MAX_VALUE;
        int thirdCost = (candidates.size() >= 3) ? candidates.get(2).bestCost : Integer.MAX_VALUE;
        return new Evaluation(bestPrev, customerNode, bestCost, secondCost, thirdCost, InsertType.INSERT_NODE);
    }

    private static List<SegmentCandidate> computeBestCandidatePerSegment(CustomerNode customerNode,
                                                                        Instance dataModel,
                                                                        SimulatedAnnealing sa,
                                                                        List<Segment> nonSplitSegments) {
        List<SegmentCandidate> candidates = new ArrayList<>();

        for (Segment segment : nonSplitSegments) {
            Node bestPrevNodeInSegment = null;
            int bestCostInSegment = Integer.MAX_VALUE;

            // 遍历该 segment 内所有可插入位置：在 node 与 node.next 之间插入
            for (Node node = segment.segmentHead;
                 node.segment != segment.next && node.next != null;
                 node = node.next) {
                int cost = InsertionObjective.objAfterInsertCustomer(node, customerNode, dataModel, sa);
                if (cost < bestCostInSegment) {
                    bestCostInSegment = cost;
                    bestPrevNodeInSegment = node;
                }
            }

            if (bestPrevNodeInSegment != null) {
                candidates.add(new SegmentCandidate(segment, bestPrevNodeInSegment, bestCostInSegment));
            }
        }

        candidates.sort(Comparator.comparingInt(c -> c.bestCost));
        return candidates;
    }

    private static final class SegmentCandidate {
        @SuppressWarnings("unused")
        final Segment segment;
        final Node bestPrevNode;
        final int bestCost;

        SegmentCandidate(Segment segment, Node bestPrevNode, int bestCost) {
            this.segment = segment;
            this.bestPrevNode = bestPrevNode;
            this.bestCost = bestCost;
        }
    }
}


