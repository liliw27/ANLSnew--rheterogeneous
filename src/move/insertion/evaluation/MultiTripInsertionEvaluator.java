package move.insertion.evaluation;

import SA.SimulatedAnnealing;
import model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * STI（Split-Trip Insertion / 拆趟插入、也常被称为 multi-trip insertion）的评估逻辑。
 *
 * <p>在 FRP 的多趟/多舱场景中，有时“直接把客户插入现有 trip”不可行（或代价很高）。
 * STI 的思路是：在某个位置插入一个 depot 边界，把 trip 拆开（或相当于新开一趟），再把客户插入到新 trip 中。</p>
 *
 * <p>本项目中 STI 有两种变体（与 {@link InsertType} 对应）：</p>
 * <ul>
 *   <li><b>MTID</b>（{@link InsertType#INSERT_MULTITRIPID}）：先插 customer，再插 depot 边界</li>
 *   <li><b>MTDI</b>（{@link InsertType#INSERT_MULTITRIPDI}）：先插 depot 边界，再插 customer</li>
 * </ul>
 *
 * <p>本类只负责“枚举候选位置并计算插入代价”，不修改解。</p>
 */
final class MultiTripInsertionEvaluator {
    private MultiTripInsertionEvaluator() {}

    static Evaluation bestMTID(CustomerNode customerNode,
                               Instance dataModel,
                               SimulatedAnnealing sa,
                               List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTID(customerNode, dataModel, sa, splitSegments);
        Candidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.cost;
        Node bestPrev = (best == null) ? null : best.prevNode;
        return new Evaluation(bestPrev, customerNode, bestCost, InsertType.INSERT_MULTITRIPID);
    }

    static Evaluation bestMTDI(CustomerNode customerNode,
                               Instance dataModel,
                               SimulatedAnnealing sa,
                               List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTDI_Best(customerNode, dataModel, sa, splitSegments);
        Candidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.cost;
        Node bestPrev = (best == null) ? null : best.prevNode;
        return new Evaluation(bestPrev, customerNode, bestCost, InsertType.INSERT_MULTITRIPDI);
    }

    static Evaluation bestAndSecondMTID(CustomerNode customerNode,
                                        Instance dataModel,
                                        SimulatedAnnealing sa,
                                        List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTID(customerNode, dataModel, sa, splitSegments);
        Candidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.cost;
        Node bestPrev = (best == null) ? null : best.prevNode;
        int secondCost = (candidates.size() >= 2) ? candidates.get(1).cost : Integer.MAX_VALUE;
        return new Evaluation(bestPrev, customerNode, bestCost, secondCost, InsertType.INSERT_MULTITRIPID);
    }

    static Evaluation bestAndSecondMTDI(CustomerNode customerNode,
                                        Instance dataModel,
                                        SimulatedAnnealing sa,
                                        List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTDI_R2R3(customerNode, dataModel, sa, splitSegments);
        Candidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.cost;
        Node bestPrev = (best == null) ? null : best.prevNode;
        int secondCost = (candidates.size() >= 2) ? candidates.get(1).cost : Integer.MAX_VALUE;
        return new Evaluation(bestPrev, customerNode, bestCost, secondCost, InsertType.INSERT_MULTITRIPDI);
    }

    static Evaluation bestSecondThirdMTID(CustomerNode customerNode,
                                          Instance dataModel,
                                          SimulatedAnnealing sa,
                                          List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTID(customerNode, dataModel, sa, splitSegments);
        Candidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.cost;
        Node bestPrev = (best == null) ? null : best.prevNode;
        int secondCost = (candidates.size() >= 2) ? candidates.get(1).cost : Integer.MAX_VALUE;
        int thirdCost = (candidates.size() >= 3) ? candidates.get(2).cost : Integer.MAX_VALUE;
        return new Evaluation(bestPrev, customerNode, bestCost, secondCost, thirdCost, InsertType.INSERT_MULTITRIPID);
    }

    static Evaluation bestSecondThirdMTDI(CustomerNode customerNode,
                                          Instance dataModel,
                                          SimulatedAnnealing sa,
                                          List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTDI_R2R3(customerNode, dataModel, sa, splitSegments);
        Candidate best = candidates.isEmpty() ? null : candidates.get(0);
        int bestCost = (best == null) ? Integer.MAX_VALUE : best.cost;
        Node bestPrev = (best == null) ? null : best.prevNode;
        int secondCost = (candidates.size() >= 2) ? candidates.get(1).cost : Integer.MAX_VALUE;
        int thirdCost = (candidates.size() >= 3) ? candidates.get(2).cost : Integer.MAX_VALUE;
        return new Evaluation(bestPrev, customerNode, bestCost, secondCost, thirdCost, InsertType.INSERT_MULTITRIPDI);
    }

    /**
     * MTID 的候选集合：对每个 segment 取其最优插入位置 + “最短 route 上新开一趟”的候选。
     */
    private static List<Candidate> candidatesForMTID(CustomerNode customerNode,
                                                     Instance dataModel,
                                                     SimulatedAnnealing sa,
                                                     List<Segment> splitSegments) {
        List<Candidate> candidates = new ArrayList<>();

        for (Segment segment : splitSegments) {
            Vehicle vehicle = dataModel.vehicles.get(segment.segmentHead.route.ID);
            int[] prefixQty = new int[dataModel.nrProducts];

            Node bestPrev = null;
            int bestCost = Integer.MAX_VALUE;

            for (Node node = segment.segmentHead;
                 node.next != null && node.next.segment == segment;
                 node = node.next) {
                if (node instanceof CustomerNode) {
                    CustomerNode cn = (CustomerNode) node;
                    for (int p = 0; p < dataModel.nrProducts; p++) {
                        prefixQty[p] += cn.deliveryQuantity[p];
                    }
                }

                int compartmentsNeeded = compartmentsNeeded(prefixQty, customerNode.deliveryQuantity, vehicle.comCapacity);
                if (compartmentsNeeded > vehicle.compartmentNum) {
                    // prefix 只会越来越大，因此后续位置也不会再可行
                    break;
                }

                int cost = InsertionObjective.objAfterInsertCustomerThenDepot(node, customerNode, dataModel, sa);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPrev = node;
                }
            }

            if (bestPrev != null) {
                candidates.add(new Candidate(segment, bestPrev, bestCost));
            }
        }

        Candidate newTrip = candidateOnShortestRoute_MTID(customerNode, dataModel, sa);
        if (newTrip != null) {
            candidates.add(newTrip);
        }

        candidates.sort(Comparator.comparingInt(c -> c.cost));
        return candidates;
    }

    /**
     * MTDI 的候选集合（best 版本）：
     * 对每个 segment 取其最优插入位置 + “最短 route 上新开一趟”的候选。
     *
     * <p>注意：为保持历史行为一致，best 版本在 shortest-route 候选上使用的是
     * {@code objAfterInsertCustomerThenDepot}（见旧代码 {@code evaluateMTDI()}）。</p>
     */
    private static List<Candidate> candidatesForMTDI_Best(CustomerNode customerNode,
                                                          Instance dataModel,
                                                          SimulatedAnnealing sa,
                                                          List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTDI_Internal(customerNode, dataModel, sa, splitSegments);
        Candidate newTrip = candidateOnShortestRoute_MTDI_Best(customerNode, dataModel, sa);
        if (newTrip != null) {
            candidates.add(newTrip);
        }
        candidates.sort(Comparator.comparingInt(c -> c.cost));
        return candidates;
    }

    /**
     * MTDI 的候选集合（R2/R3 版本）：与 best 不同的是 shortest-route 候选上使用
     * {@code objAfterInsertDepotThenCustomer}（与旧代码 {@code evaluateMTDIR2/3()} 对齐）。</p>
     */
    private static List<Candidate> candidatesForMTDI_R2R3(CustomerNode customerNode,
                                                          Instance dataModel,
                                                          SimulatedAnnealing sa,
                                                          List<Segment> splitSegments) {
        List<Candidate> candidates = candidatesForMTDI_Internal(customerNode, dataModel, sa, splitSegments);
        Candidate newTrip = candidateOnShortestRoute_MTDI_R2R3(customerNode, dataModel, sa);
        if (newTrip != null) {
            candidates.add(newTrip);
        }
        candidates.sort(Comparator.comparingInt(c -> c.cost));
        return candidates;
    }

    /**
     * MTDI 在每个 segment 内找到最优位置（不包含 shortest-route 候选，供 best/R2/R3 共用）。
     */
    private static List<Candidate> candidatesForMTDI_Internal(CustomerNode customerNode,
                                                              Instance dataModel,
                                                              SimulatedAnnealing sa,
                                                              List<Segment> splitSegments) {
        List<Candidate> candidates = new ArrayList<>();

        for (Segment segment : splitSegments) {
            Vehicle vehicle = dataModel.vehicles.get(segment.segmentHead.route.ID);

            int[] totalQty = sumDeliveredInSegment(segment, dataModel.nrProducts);
            int[] prefixQty = new int[dataModel.nrProducts];

            Node bestPrev = null;
            int bestCost = Integer.MAX_VALUE;

            for (Node node = segment.segmentHead.next;
                 node.next != null && node.segment == segment;
                 node = node.next) {
                // prefixQty = 从 segmentHead 到当前 node（含）累计的投放量
                if (node instanceof CustomerNode) {
                    CustomerNode cn = (CustomerNode) node;
                    for (int p = 0; p < dataModel.nrProducts; p++) {
                        prefixQty[p] += cn.deliveryQuantity[p];
                    }
                }

                // quantity2：旧代码里是 node.next 到 segment 结束（不含 node）的投放量
                int[] suffixAfterNode = new int[dataModel.nrProducts];
                for (int p = 0; p < dataModel.nrProducts; p++) {
                    suffixAfterNode[p] = totalQty[p] - prefixQty[p];
                }

                int compartmentsNeeded = compartmentsNeeded(suffixAfterNode, customerNode.deliveryQuantity, vehicle.comCapacity);
                if (compartmentsNeeded > vehicle.compartmentNum) {
                    continue;
                }

                int cost = InsertionObjective.objAfterInsertDepotThenCustomer(node, customerNode, dataModel, sa);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPrev = node;
                }
            }

            if (bestPrev != null) {
                candidates.add(new Candidate(segment, bestPrev, bestCost));
            }
        }

        return candidates;
    }

    private static Candidate candidateOnShortestRoute_MTID(CustomerNode customerNode,
                                                           Instance dataModel,
                                                           SimulatedAnnealing sa) {
        Route routeShort = shortestRoute(sa.routes);
        Vehicle vehicle = dataModel.vehicles.get(routeShort.ID);
        int compartmentsNeeded = compartmentsNeeded(null, customerNode.deliveryQuantity, vehicle.comCapacity);
        if (compartmentsNeeded > vehicle.compartmentNum) return null;

        Node node = routeShort.routeStart;
        int cost = InsertionObjective.objAfterInsertCustomerThenDepot(node, customerNode, dataModel, sa);
        return new Candidate(null, node, cost);
    }

    private static Candidate candidateOnShortestRoute_MTDI_Best(CustomerNode customerNode,
                                                                Instance dataModel,
                                                                SimulatedAnnealing sa) {
        Route routeShort = shortestRoute(sa.routes);
        Vehicle vehicle = dataModel.vehicles.get(routeShort.ID);
        int compartmentsNeeded = compartmentsNeeded(null, customerNode.deliveryQuantity, vehicle.comCapacity);
        if (compartmentsNeeded > vehicle.compartmentNum) return null;

        Node node = routeShort.routeEnd.prev;
        // 与旧实现 evaluateMTDI() 保持一致：这里使用的是 “customer then depot”
        int cost = InsertionObjective.objAfterInsertCustomerThenDepot(node, customerNode, dataModel, sa);
        return new Candidate(null, node, cost);
    }

    private static Candidate candidateOnShortestRoute_MTDI_R2R3(CustomerNode customerNode,
                                                                Instance dataModel,
                                                                SimulatedAnnealing sa) {
        Route routeShort = shortestRoute(sa.routes);
        Vehicle vehicle = dataModel.vehicles.get(routeShort.ID);
        int compartmentsNeeded = compartmentsNeeded(null, customerNode.deliveryQuantity, vehicle.comCapacity);
        if (compartmentsNeeded > vehicle.compartmentNum) return null;

        Node node = routeShort.routeEnd.prev;
        int cost = InsertionObjective.objAfterInsertDepotThenCustomer(node, customerNode, dataModel, sa);
        return new Candidate(null, node, cost);
    }

    private static Route shortestRoute(Route[] routes) {
        Route best = routes[0];
        int bestDuration = Integer.MAX_VALUE;
        for (Route r : routes) {
            if (r.duration < bestDuration) {
                bestDuration = r.duration;
                best = r;
            }
        }
        return best;
    }

    /**
     * 计算“在已有投放量 baseQty 的基础上，再投放 demandQty 后”需要的舱位数：
     * \(\sum_p \lceil (baseQty[p] + demandQty[p]) / cap \rceil\)。
     */
    private static int compartmentsNeeded(int[] baseQtyOrNull, int[] demandQty, int cap) {
        int compartments = 0;
        for (int p = 0; p < demandQty.length; p++) {
            int base = (baseQtyOrNull == null) ? 0 : baseQtyOrNull[p];
            compartments += (int) Math.ceil(1.0 * (base + demandQty[p]) / cap);
        }
        return compartments;
    }

    private static int[] sumDeliveredInSegment(Segment segment, int nrProducts) {
        int[] total = new int[nrProducts];
        for (Node node = segment.segmentHead; node != null && node.segment == segment; node = node.next) {
            if (node instanceof CustomerNode) {
                CustomerNode cn = (CustomerNode) node;
                for (int p = 0; p < nrProducts; p++) {
                    total[p] += cn.deliveryQuantity[p];
                }
            }
        }
        return total;
    }

    private static final class Candidate {
        @SuppressWarnings("unused")
        final Segment segment;
        final Node prevNode;
        final int cost;

        Candidate(Segment segment, Node prevNode, int cost) {
            this.segment = segment;
            this.prevNode = prevNode;
            this.cost = cost;
        }
    }
}


