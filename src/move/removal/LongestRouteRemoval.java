package move.removal;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Node;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Longest Route Removal（LRR）移除算子。
 *
 * <p><b>与论文对齐</b>：论文的 LRR 会移除 makespan 路线（最长路线）上的一批 visits，以便 repair 阶段重排这些 visits，
 * 从而有机会降低 makespan。</p>
 *
 * <p><b>实现</b>：找到当前最长 route（duration 等于 makespan 的那条），移除其中所有非 full-direct 的 {@link CustomerNode}。
 * full-direct（直达趟次）通常不希望被破坏（代码通过 {@code node.segment.isFullDirect} 过滤）。</p>
 *
 * @author 20175993
 * @create 7/3/2018
 * @since 1.0.0
 */
public class LongestRouteRemoval extends Removal
{
    protected SimulatedAnnealing sa;

    public LongestRouteRemoval(SimulatedAnnealing sa)
    {
        this.sa = sa;
    }

    @Override
    public void move()
    {
        sa.unServedNodes = new ArrayList<> ( );
        int oldMakespan = Arrays.stream (sa.routes).mapToInt (r -> r.duration).max ( ).getAsInt ( );
        int longestRoute = 0;
        for (int i = 0; i < sa.routes.length; i++)
        {
            if (oldMakespan == sa.routes[i].duration)
            {
                longestRoute = i;
                break;
            }
        }
        for (Node node = sa.routes[longestRoute].routeStart.next; node != sa.routes[longestRoute].routeEnd; node = node.next)
        {
            if(node instanceof CustomerNode&&node.segment.isFullDirect==false){
                CustomerNode customerNode = (CustomerNode) node;
                sa.unServedNodes.add (customerNode);
                Node prevNode = node.prev;
                node.route.removeNode (node);
                node = prevNode;
            }
        }
    }

}
