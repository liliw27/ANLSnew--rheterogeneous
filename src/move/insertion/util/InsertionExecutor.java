package move.insertion.util;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;
import model.Node;
import model.Route;
import move.insertion.evaluation.Evaluation;
import move.insertion.evaluation.InsertType;

/**
 * insertion 算子的公共“执行层”工具：根据 {@link Evaluation} 修改解结构，并做必要的后处理。
 *
 * <p>目的：Greedy/Regret 等算子本质差异只在“怎么选下一个要插入的 visit”，
 * 但它们执行插入与后处理（合并相邻重复 visit）的代码高度重复。将重复逻辑集中到这里，
 * 便于阅读与后续扩展。</p>
 */
public final class InsertionExecutor {
    private InsertionExecutor() {}

    /**
     * 按 {@link Evaluation#insertType} 对当前解执行一次插入动作（会就地修改 route/segment/node 结构）。
     *
     * <p>注意：该方法不会从 {@link SimulatedAnnealing#unServedNodes} 中移除节点；
     * 这是“选择策略”的责任（例如 Greedy/Regret 选择完 bestC 后再 remove）。</p>
     */
    public static void applyEvaluation(SimulatedAnnealing sa, Instance dataModel, Evaluation eval) {
        if (eval == null) {
            throw new IllegalArgumentException("eval 不能为空");
        }

        if (eval.insertType == InsertType.INSERT_NODE) {
            Node prevNode = eval.prevNode;
            CustomerNode customerNode = eval.insertingNode;
            if (prevNode == null) {
                // 理论上不应发生：整单插入必须有具体插入位置
                throw new IllegalStateException("INSERT_NODE 返回了 null prevNode");
            }
            prevNode.route.insertCustomerNode(prevNode, customerNode);
            return;
        }

        if (eval.insertType == InsertType.INSERT_SPLITDELIVERY) {
            // SI：原节点会被拆成多个小 visit，因此需要把原节点从 sa.nodes 中移除，再添加新节点
            sa.nodes.remove(eval.insertingNode);
            for (int i = 0; i < eval.prevNodesSD.size(); i++) {
                Node prevNode = eval.prevNodesSD.get(i);
                CustomerNode node = eval.insertingNodes.get(i);
                sa.nodes.add(node);
                prevNode.route.insertCustomerNode(prevNode, node);
            }
            return;
        }

        if (eval.insertType == InsertType.INSERT_MULTITRIPID) {
            // MTID：在位置处插入 depot 边界，然后把 customer 插在边界之前（旧实现行为）
            Node prevNode = eval.prevNode;
            CustomerNode customerNode = eval.insertingNode;
            prevNode.route.insertDepot(prevNode);
            prevNode.route.insertCustomerNode(prevNode, customerNode);
            return;
        }

        if (eval.insertType == InsertType.INSERT_MULTITRIPDI) {
            // MTDI：在位置处插入 depot 边界，然后把 customer 插在边界之后
            Node prevNode = eval.prevNode;
            CustomerNode customerNode = eval.insertingNode;
            prevNode.route.insertDepot(prevNode);
            prevNode.route.insertCustomerNode(prevNode.next, customerNode);
            return;
        }

        throw new IllegalStateException("未知 insertType: " + eval.insertType);
    }

    /**
     * 后处理：合并 route 中相邻且 {@code index} 相同的节点（包括 depot=0 与 customer>0）。
     *
     * <p>这个逻辑来自原始实现（多个算子里重复粘贴）。其目标是：
     * 在插入过程中可能产生“同一客户被连续访问”的碎片化 visit，把它们合并成一个节点，以保持解更干净。</p>
     */
    public static void mergeAdjacentDuplicateVisits(SimulatedAnnealing sa, Instance dataModel) {
        for (Route route : sa.routes) {
            if (route.nrNodes <= 2) continue;

            for (Node node = route.routeStart.next;
                 node.next != null && node != route.routeEnd;
                 node = node.next) {
                if (node.index != node.next.index) continue;

                // Case A: 连续 depot（index=0）
                if (node.index == 0) {
                    // 若 node 后就是 routeEnd，则直接删掉 node（避免尾部出现多余 depot）
                    if (node.next == route.routeEnd) {
                        route.removeNode(node);
                        break;
                    }

                    // 否则删除“两个 depot 中的一个”，保持边界数不膨胀
                    node = node.next;
                    route.removeNode(node.prev);
                    node = node.prev;
                    continue;
                }

                // Case B: 连续 customer visit（同 index）
                Node prevNode = node.prev;
                Node nextNode = node.next;

                route.removeNode(node);
                sa.nodes.remove(nextNode);
                route.removeNode(nextNode);
                sa.nodes.remove(node);

                int[] q = new int[dataModel.nrProducts];
                for (int p = 0; p < dataModel.nrProducts; p++) {
                    q[p] = ((CustomerNode) node).deliveryQuantity[p] + ((CustomerNode) nextNode).deliveryQuantity[p];
                }

                CustomerNode merged = new CustomerNode(node.index, q);
                route.insertCustomerNode(prevNode, merged);
                sa.nodes.add(merged);
                node = merged;
            }
        }
    }
}


