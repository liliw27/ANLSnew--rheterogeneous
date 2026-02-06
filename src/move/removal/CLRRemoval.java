package move.removal;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Node;
import model.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * CLRRemoval（“Customers/Visits in Long Routes Removal”的简化实现）。
 *
 * <p><b>与论文对齐</b>：论文中有一种 destroy 算子会从“若干条最长路线”中随机移除一部分 visits（VLRR）。
 * 该算子旨在给 repair 阶段提供对 makespan 路线附近的更大重排空间，从而有机会降低 makespan。</p>
 *
 * <p><b>实现步骤</b>：</p>
 * <ol>
 *   <li>按 route 时长降序排序，取最长的前一半 routes</li>
 *   <li>收集这些 routes 中的所有 {@link CustomerNode}</li>
 *   <li>从该集合中随机挑选若干个节点，放入 {@link SA.SimulatedAnnealing#unServedNodes}</li>
 *   <li>从原 route 链表中删除这些节点</li>
 * </ol>
 *
 * <p><b>注意</b>：本算子没有过滤 full-direct 节点（与 {@link LongestRouteRemoval}/{@link WorstRemoval} 不同）。
 * 如果未来希望“直达趟次不被破坏”，可以在 step2 收集节点时加上 {@code node.segment.isFullDirect==false} 的过滤。</p>
 *
 * @author 20175993
 * @create 7/4/2018
 * @since 1.0.0
 */
public class CLRRemoval extends Removal
{
    protected SimulatedAnnealing sa;
    private final SplittableRandom rand;

    public CLRRemoval(SimulatedAnnealing sa){
        this.sa=sa;
        this.rand=sa.rand;
    }

    @Override
    public void move()
    {
        // Step 1: rank routes by duration and pick the longest half
        List<RankedRoute> rankedRoutes=new ArrayList<>();
        for(Route route:sa.routes){
            RankedRoute rankedRoute=new RankedRoute(route,route.duration);
            rankedRoutes.add(rankedRoute);
        }
        Collections.sort(rankedRoutes);
        int routeNum=sa.routes.length/2;
        List<CustomerNode> customersInLongRoutes=new ArrayList<>();
        // Step 2: collect customers in the selected long routes
        for(int i=0;i<routeNum;i++){
            for(Node node=rankedRoutes.get(i).route.routeStart.next;node.next != null && node != rankedRoutes.get(i).route.routeEnd; node = node.next){
                if(node instanceof CustomerNode){
                    customersInLongRoutes.add((CustomerNode) node);
                }
            }
        }
        // Step 3: sample a subset of customers to delete and put them into unServedNodes
        sa.unServedNodes=new ArrayList<>();
        int nrNodesToDelete=rand.nextInt ((int)(0.5*customersInLongRoutes.size()));

        if(nrNodesToDelete<2){
            nrNodesToDelete = 2;
        }
        while(sa.unServedNodes.size ()!=nrNodesToDelete){
            int r=rand.nextInt (customersInLongRoutes.size ());
            if(sa.unServedNodes.contains (customersInLongRoutes.get (r))){
                continue;
            }
            sa.unServedNodes.add(customersInLongRoutes.get(r));

        }

        // Step 4: remove selected nodes from the current schedule
        for(CustomerNode node:sa.unServedNodes){
            node.route.removeNode (node);
        }
//        sa.printRoutes ();
//        String s="==REMOVE:";
//        for (int j = 0; j < sa.unServedNodes.size ( ); j++){
//            s+=sa.unServedNodes.get (j);
//        }
//        System.out.println ( s);
    }
    private class RankedRoute implements Comparable<RankedRoute>
    {
        private Route route;
        private Integer rankNumber;

        private RankedRoute(Route route, int rankNumber)
        {
            this.route = route;
            this.rankNumber = rankNumber;
        }

        private Integer getRankNumber()
        {
            return rankNumber;
        }

        public int compareTo(RankedRoute arg0)
        {
            return arg0.getRankNumber ( ).compareTo (this.getRankNumber ( ));
        }//decreasing order
    }
}
