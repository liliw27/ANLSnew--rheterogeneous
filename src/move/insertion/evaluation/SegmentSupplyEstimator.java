package move.insertion.evaluation;

import SA.SimulatedAnnealing;
import model.*;

import java.util.*;

/**
 * Segment 供给估计器（从 {@link EvaluateInsertion} 中抽出）。
 *
 * <p>职责：在当前解状态下，针对一个待插入 visit（{@link CustomerNode}），估计每个 segment 的最大可供给量，
 * 并将 segment 划分为：</p>
 * <ul>
 *   <li><b>nonSplitSegments</b>：可“整单插入”(II) 满足全部需求的 segment</li>
 *   <li><b>splitSegments</b>：不能整单满足，但可提供部分供给的 segment（用于 SI/STI）</li>
 * </ul>
 *
 * <p><b>重要</b>：该模块是启发式估计，目的是减少 repair 评估的搜索空间；它不直接修改解结构。</p>
 */
final class SegmentSupplyEstimator {

    private SegmentSupplyEstimator() {}

    static void divideSegments(CustomerNode customerNode,
                               Instance dataModel,
                               SimulatedAnnealing sa,
                               List<Segment> nonSplitSegments,
                               List<Segment> splitSegments,
                               List<Integer[]> maxQSplitSegments) {
        nonSplitSegments.clear();
        splitSegments.clear();
        maxQSplitSegments.clear();

        // 1) 先按“整单是否可行”把 segment 划分为 nonSplit / split
        for (Route route : sa.routes) {
            for (Segment segment = route.routeStart.segment; segment != null; segment = segment.next) {
                Integer[] maxQForCustomer = getMaxQForCustomer(customerNode, dataModel, segment, sortDemandForProduct(customerNode, dataModel));

                boolean split = false;
                for (int p = 0; p < dataModel.nrProducts; p++) {
                    if (maxQForCustomer[p] < customerNode.deliveryQuantity[p]) {
                        split = true;
                        break;
                    }
                }

                if (split) {
                    int s = 0;
                    for (int p = 0; p < dataModel.nrProducts; p++) {
                        s += maxQForCustomer[p];
                    }
                    // 过滤：满载直达 trip 不参与 split（与原实现一致）
                    if (!segment.isFullDirect && s >= 0) {
                        splitSegments.add(segment);
                    }
                } else {
                    nonSplitSegments.add(segment);
                }
            }
        }

        // 2) 为 splitSegments 估计 maxQSplitSegments（原实现里会先用“单产品最大供给”汇总，得到 times，再排序）
        int[] totalQ = new int[dataModel.nrProducts];
        int[] times = new int[dataModel.nrProducts];
        for (Segment segment : splitSegments) {
            Integer[] maxQSP = getMaxQForCustomerSP(customerNode, dataModel, segment);
            for (int p = 0; p < dataModel.nrProducts; p++) {
                totalQ[p] += maxQSP[p];
            }
        }
        for (int p = 0; p < dataModel.nrProducts; p++) {
            // 保持历史行为：直接 floor(totalQ / demand)
            times[p] = (int) Math.floor(totalQ[p] * 1.0 / customerNode.deliveryQuantity[p]);
        }

        List<Map.Entry<Integer, Integer>> order = sortDemandForProduct(times, dataModel.nrProducts);
        for (Segment segment : splitSegments) {
            maxQSplitSegments.add(getMaxQForCustomer(customerNode, dataModel, segment, order));
        }
    }

    /**
     * 用 times 生成产品排序（升序）。
     *
     * <p>保留原实现的排序方向：按 value 从小到大排序。</p>
     */
    private static List<Map.Entry<Integer, Integer>> sortDemandForProduct(int[] times, int nrProducts) {
        Map<Integer, Integer> demandMap = new HashMap<>();
        for (int p = 0; p < nrProducts; p++) {
            demandMap.put(p, times[p]);
        }
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(demandMap.entrySet());
        list.sort(Comparator.comparingInt(Map.Entry::getValue));
        return list;
    }

    /**
     * 按插入节点的需求量降序排序产品（常用于先分配“需求最大的产品”）。
     */
    private static List<Map.Entry<Integer, Integer>> sortDemandForProduct(CustomerNode customerNode, Instance dataModel) {
        Map<Integer, Integer> demandMap = new HashMap<>();
        for (int p = 0; p < dataModel.nrProducts; p++) {
            demandMap.put(p, customerNode.deliveryQuantity[p]);
        }
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(demandMap.entrySet());
        list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        return list;
    }

    /**
     * 估计：如果 segment 只用来供应某一个产品 p，该 segment 在产品 p 上最多可提供多少。
     */
    private static Integer[] getMaxQForCustomerSP(CustomerNode customerNode, Instance dataModel, Segment segment) {
        Vehicle vehicle = dataModel.vehicles.get(segment.segmentHead.route.ID);
        Integer[] maxQForCustomerForP = new Integer[dataModel.nrProducts];
        for (int p = 0; p < dataModel.nrProducts; p++) {
            int c = segment.compartmentResidual;
            maxQForCustomerForP[p] = Math.min(
                    customerNode.deliveryQuantity[p],
                    segment.productResidual[p] + c * vehicle.comCapacity
            );
        }
        return maxQForCustomerForP;
    }

    /**
     * 估计：在“多产品、多舱”下，一个 segment 能为该客户提供的最大供给向量（启发式）。
     */
    private static Integer[] getMaxQForCustomer(CustomerNode customerNode,
                                                Instance dataModel,
                                                Segment segment,
                                                List<Map.Entry<Integer, Integer>> productOrder) {
        Vehicle vehicle = dataModel.vehicles.get(segment.segmentHead.route.ID);
        int c = segment.compartmentResidual;
        Integer[] maxQForCustomerForP = new Integer[dataModel.nrProducts];

        for (Map.Entry<Integer, Integer> mapping : productOrder) {
            int p = mapping.getKey();
            int cn = (int) Math.ceil(1.0 * (customerNode.deliveryQuantity[p] - segment.productResidual[p]) / vehicle.comCapacity);

            if (cn <= c) {
                maxQForCustomerForP[p] = customerNode.deliveryQuantity[p];
                c = c - cn;
            } else {
                maxQForCustomerForP[p] = segment.productResidual[p] + c * vehicle.comCapacity;
                c = 0;
            }
        }
        return maxQForCustomerForP;
    }
}


