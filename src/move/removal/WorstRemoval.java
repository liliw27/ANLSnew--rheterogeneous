package move.removal;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;
import model.Node;
import model.Route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Worst Removal（WR）移除算子。
 *
 * <p><b>核心思想</b>：迭代地删除“对当前目标贡献最大”的 visit（删除后目标下降最大），以便 repair 阶段重新安置这些 visits。</p>
 *
 * <p><b>与目标函数的关系</b>：代码的目标为 {@code makespan * multiplier + sum(routeDuration)}。
 * 因此在评估删除某个节点的收益时：</p>
 * <ul>
 *   <li>必然减少该 route 的距离（本节点两条边替换成一条边的节省）</li>
 *   <li>若该节点位于 makespan route 上，且删除可降低 makespan，则额外获得乘子收益</li>
 * </ul>
 *
 * <p><b>注意</b>：本实现跳过 full-direct segment 的节点（{@code node.segment.isFullDirect==false}）。</p>
 *
 * @author 20175993
 * @create 7/2/2018
 * @since 1.0.0
 */
public class WorstRemoval extends Removal
{
    protected SimulatedAnnealing sa;
    private final SplittableRandom rand;
    protected final Instance dataModel;

    public WorstRemoval(SimulatedAnnealing sa, Instance datamodel)
    {
        this.sa = sa;
        this.rand = sa.rand;
        this.dataModel = datamodel;
    }

    @Override
    public void move()
    {
        sa.unServedNodes = new ArrayList<> ( );
        int nrNodesToDelete = rand.nextInt (sa.REMOVE_MAXNUM);
        int count = 0;
        if(nrNodesToDelete<2){
            nrNodesToDelete = 2;
        }
        while (count < nrNodesToDelete)
        {

            worstremoval ( );
            count++;
        }


//        sa.printRoutes ();
        // Debug output kept disabled by default
    }

    public void worstremoval()
    {
        int maximalReducedObjValue = -Integer.MAX_VALUE;
        CustomerNode customerNode = null;
        int oldMakespan = Arrays.stream (sa.routes).mapToInt (r -> r.duration).max ( ).getAsInt ( );
        int secondMakespan = -Integer.MAX_VALUE;
        for (Route route : sa.routes)
        {
            if (route.duration == oldMakespan)
            {
                continue;
            }
            if (secondMakespan < route.duration)
            {
                secondMakespan = route.duration;
            }
        }
        for (Route route : sa.routes)
        {
            for (Node node = route.routeStart.next; node != route.routeEnd; node = node.next)
            {
                if (node.segment.isFullDirect == false && node instanceof CustomerNode)
                {
                    int removalReducedCost = dataModel.distanceMatrix[node.prev.index][node.index] + dataModel.distanceMatrix[node.index][node.next.index] - dataModel.distanceMatrix[node.prev.index][node.next.index];
                    int reducedObjValue = removalReducedCost;
                    if (oldMakespan == node.route.duration)
                        reducedObjValue += Math.min(removalReducedCost,oldMakespan-secondMakespan) * sa.MAKESPAN_MULTIPLIER;
                    if (maximalReducedObjValue < reducedObjValue)
                    {
                        maximalReducedObjValue = reducedObjValue;
                        customerNode = (CustomerNode) node;
                    }
                }
            }
        }
        sa.unServedNodes.add (customerNode);
        customerNode.route.removeNode (customerNode);

    }
}

