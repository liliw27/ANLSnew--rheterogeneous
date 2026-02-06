package move.removal;

import SA.SimulatedAnnealing;
import model.*;

import java.util.ArrayList;
import java.util.SplittableRandom;

/**
 * TripsRemoval（trip/segment 级别的 destroy 算子）。
 *
 * <p><b>动机</b>：前面的 removal 算子大多是“节点级别”（移除若干 {@link CustomerNode}）。
 * 本算子改为“整段 trip/segment 级别”的破坏：直接从 {@link model.Route} 中移除一个或多个 {@link Segment}，
 * 并把这些 segment 放入 {@link SA.SimulatedAnnealing#removedSegments}，后续由 {@link move.insertion.TripsInsertion}
 * 重新分配到其它车辆上。</p>
 *
 * <p><b>与论文对齐</b>：论文中 Trip Reallocation（TR）会选取若干 trips 移出并重新分配到当前最短 itinerary 的车辆。
 * 本实现是一个简化版本，仍保留“移除一批 trips → repair 时按最短路线回插”的核心结构。</p>
 *
 * <p><b>两种移除策略</b>（随机选择其一）：</p>
 * <ul>
 *   <li>A：反复从当前最长路线中找“最长的 trip”移除</li>
 *   <li>B：随机选择一条路线、随机选择一个 segment 移除</li>
 * </ul>
 *
 * <p><b>注意</b>：本算子操作的是 Segment 链表结构（通过 {@link Route#removeSegment(Segment)}），
 * 因此对链表一致性要求更高；建议保持 {@code SimulatedAnnealing.verifyRouteState()} 断言开启。</p>
 *
 * @author 20175993
 * @create 7/4/2018
 * @since 1.0.0
 */
public class TripsRemoval extends Removal
{
    protected SimulatedAnnealing sa;
    private final SplittableRandom rand;
    protected final Instance dataModel;

    public TripsRemoval(SimulatedAnnealing sa, Instance datamodel)
    {
        this.sa = sa;
        this.rand = sa.rand;
        this.dataModel = datamodel;
    }

    @Override
    public void move() {
        // Step 1: determine how many segments (trips) to remove
        int tripsTotalNum=0;
        int tripsRemoveNum=0;
        for(Route route:sa.routes){
            tripsTotalNum+=route.segments;
        }
        // remove up to ~45% of all trips (at least 1 if possible)
        tripsRemoveNum = rand.nextInt(Math.max((int) Math.ceil(tripsTotalNum * 0.45), 1));
        sa.removedSegments=new ArrayList<>();// the segments to be removed
        // Step 2: choose a removal strategy
        //A. the longest trip in longest route
        //B. randomly
        double r=rand.nextDouble();
        if(r<0.5){
            while (tripsRemoveNum>0) {
                tripsRemoveNum--;
                // find the longest route
                int longestD = -Integer.MAX_VALUE;
                Route longestR = sa.routes[0];
                for (Route route : sa.routes) {
                    if (route.duration > longestD) {
                        longestD = route.duration;
                        longestR = route;
                    }
                }
                if(longestR.segments<2){
                    break;
                }
                // find the longest trip (segment) inside that route
                int longestTr=0;
                Segment longestS=longestR.routeStart.segment;
                int dT=0;
                for(Node node =longestR.routeStart;node!=longestR.routeEnd;node=node.next){
                    if(node!=longestR.routeStart&&node instanceof DepotNode){

                        if(longestTr<dT){
                            longestTr=dT;
                            longestS=node.prev.segment;
                        }
                        dT=0;
                    }
                    else{
                        dT+=dataModel.distanceMatrix[node.index][node.next.index];
                    }
                }
                longestR.removeSegment(longestS);
                sa.removedSegments.add(longestS);


            }

        }
        else{
            while (tripsRemoveNum>0){
                tripsRemoveNum--;
                int rNo=rand.nextInt(sa.routes.length);
                Route route=sa.routes[rNo];
                int sNo=rand.nextInt(route.segments);
                int count=0;
                for(Node node=route.routeStart;node!=route.routeEnd;node=node.next){
                    if(node instanceof DepotNode){
                        if(count==sNo){
                            route.removeSegment(node.segment);
                            sa.removedSegments.add(node.segment);
                        }
                        else
                        count++;
                    }
                }
            }
        }

    }


}
