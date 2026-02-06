package SA.verify;

import SA.SimulatedAnnealing;

import model.CustomerNode;
import model.Node;
import model.Route;
import model.Segment;

import java.util.Arrays;

/**
 * 当前解状态的一致性/不变量检查器（教学版）。
 *
 * <p>这是本项目最重要的“护栏”。如果你要修改任何 destroy/repair 算子（或 Route/Segment 的结构操作），
 * 强烈建议在开发期间保持该检查开启（见 run() 中的 assert）。</p>
 *
 * <p><b>检查内容</b>：</p>
 * <ul>
 *   <li><b>Route 时长不变量</b>：{@code Route.duration} 等于链表相邻节点距离之和</li>
 *   <li><b>Route 节点数不变量</b>：{@code Route.nrNodes} 等于从 {@code routeStart} 走到 {@code routeEnd}
 *       的节点总数（包含首尾 depot）</li>
 *   <li><b>Trip/segment 数量不变量</b>：{@code Route.segments} 等于遍历时遇到的 segment 数</li>
 *   <li><b>Segment 资源不变量</b>：对每个 segment，{@code segment.productUsed[p]} 等于该 segment 内所有
 *       {@link CustomerNode} 的 {@code deliveryQuantity[p]} 之和</li>
 *   <li><b>Segment 节点计数不变量</b>：{@code segment.customersInSegment} 等于该 segment 内的节点数
 *       （本实现中包含 depot 节点）</li>
 *   <li><b>目标值不变量</b>：{@code currentObjective} 等于
 *       {@code makespan * MAKESPAN_MULTIPLIER + sum(route.duration)}</li>
 * </ul>
 *
 * <p><b>常见失败原因</b>（学生最容易踩坑）：</p>
 * <ul>
 *   <li>插入/删除节点时忘记更新 {@code Route.duration}/{@code nrNodes}/{@code segments}</li>
 *   <li>移动节点时未同步 segment 归属（忘记调用 {@code Segment.addNode/removeNode}）</li>
 *   <li>插入/删除 depot 后未把后续节点合并/迁移到正确的 segment</li>
 *   <li>链表指针改了，但 {@code node.segment}/{@code node.route} 引用残留旧值</li>
 * </ul>
 */
public final class SolutionVerifier {

    private SolutionVerifier() {}

    public static boolean verify(SimulatedAnnealing sa) {
        for (Route r : sa.routes) {
            int duration = 0;
            int nodesInRoute = 1;
            int segments = 1;
            int[] capacityUsedInSegment = new int[sa.dataModel.nrProducts];
            int customersInSegment = 0;

            Node node = r.routeStart;
            Segment segment = r.routeStart.segment;

            if (r.routeStart == null) throw new NullPointerException("Route 起点(routeStart)为 null");
            if (r.routeEnd == null) throw new NullPointerException("Route 终点(routeEnd)为 null");
            if (segment == null) throw new NullPointerException("segment 为 null（routeStart.segment 未初始化）");

            while (node != r.routeEnd) {
                int travelTime = sa.dataModel.distanceMatrix[node.index][node.next.index];
                duration += travelTime;
                nodesInRoute++;

                if (node.segment != segment) {
                    // 跨越了 depot 边界：说明刚结束一个 segment，现在校验刚结束的 segment。
                    for (int p = 0; p < sa.dataModel.nrProducts; p++) {
                        if (segment.productUsed[p] != capacityUsedInSegment[p]) {
                            throw new RuntimeException(
                                    "Segment 产品用量不一致：product=" + p +
                                            "，期望 segment.productUsed=" + segment.productUsed[p] +
                                            "，实际按节点累加=" + capacityUsedInSegment[p]
                            );
                        }
                    }
                    capacityUsedInSegment = new int[sa.dataModel.nrProducts];
                    if (segment.customersInSegment != customersInSegment) {
                        throw new RuntimeException(
                                "Segment 节点计数不一致：期望 segment.customersInSegment=" + segment.customersInSegment +
                                        "，实际遍历计数=" + customersInSegment
                        );
                    }

                    segments++;
                    segment = node.segment;
                    customersInSegment = 1;
                } else {
                    if (node.index > 0) {
                        for (int p = 0; p < sa.dataModel.nrProducts; p++) {
                            capacityUsedInSegment[p] += ((CustomerNode) node).deliveryQuantity[p];
                        }
                    }
                    customersInSegment++;
                }

                node = node.next;
            }

            // 校验最后一个 segment
            customersInSegment++; // count r.routeEnd
            for (int p = 0; p < sa.dataModel.nrProducts; p++) {
                if (segment.productUsed[p] != capacityUsedInSegment[p]) {
                    throw new RuntimeException(
                            "Segment 产品用量不一致（最后一段）：product=" + p +
                                    "，期望 segment.productUsed=" + segment.productUsed[p] +
                                    "，实际按节点累加=" + capacityUsedInSegment[p]
                    );
                }
            }
            if (segment.customersInSegment != customersInSegment) {
                throw new RuntimeException(
                        "Segment 节点计数不一致（最后一段）：期望 segment.customersInSegment=" + segment.customersInSegment +
                                "，实际遍历计数=" + customersInSegment
                );
            }

            if (r.duration != duration) {
                throw new RuntimeException(
                        "Route 时长不一致：期望 r.duration=" + r.duration + "，实际按距离累加=" + duration
                );
            }
            if (r.nrNodes != nodesInRoute) {
                throw new RuntimeException(
                        "Route 节点数不一致：期望 r.nrNodes=" + r.nrNodes + "，实际遍历计数=" + nodesInRoute
                );
            }
            if (r.segments != segments) {
                throw new RuntimeException(
                        "Route segment 数不一致：期望 r.segments=" + r.segments + "，实际遍历计数=" + segments
                );
            }
        }

        // 校验当前目标值
        int makespan = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();
        int sumOfComplTimes = Arrays.stream(sa.routes).mapToInt(r -> r.duration).sum();

        int obj = makespan * sa.MAKESPAN_MULTIPLIER + sumOfComplTimes;
        if (sa.getCurrentObjective() != obj) {
            throw new RuntimeException(
                    "目标值不一致：期望 currentObjective=" + sa.getCurrentObjective() + "，实际计算=" + obj +
                            "（makespan=" + makespan + "，sumDuration=" + sumOfComplTimes +
                            "，MAKESPAN_MULTIPLIER=" + sa.MAKESPAN_MULTIPLIER + "）"
            );
        }
        return true;
    }
}


