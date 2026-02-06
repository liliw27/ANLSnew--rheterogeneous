package move.removal;

import SA.SimulatedAnnealing;
import model.CustomerNode;

import java.util.ArrayList;
import java.util.SplittableRandom;

/**
 * Random Removal（RR）移除算子。
 *
 * <p><b>与论文/ALNS 对齐</b>：属于 destroy operators，用于“破坏”当前解，把若干 visit 移出解并放入
 * {@link SimulatedAnnealing#unServedNodes}，后续由 repair/insertion 算子重新插入。</p>
 *
 * <p><b>实现</b>：从 {@link SimulatedAnnealing#nodes}（当前解中所有可被移除的 CustomerNode）随机选取一批，
 * 并从对应 {@link model.Route} 链表中删除。</p>
 *
 * @author 20175993
 * @create 6/21/2018
 * @since 1.0.0
 */
public class RandomRemoval extends Removal
{
   protected SimulatedAnnealing sa;
    private final SplittableRandom rand;

public RandomRemoval(SimulatedAnnealing sa){
    this.sa=sa;
    this.rand=sa.rand;
}

    @Override
    public void move()
    {
        sa.unServedNodes=new ArrayList<> ();
        // Note: REMOVE_MAXNUM is computed from problem size (N) in SimulatedAnnealing.
        int nrNodesToDelete=rand.nextInt (sa.REMOVE_MAXNUM);
        if(nrNodesToDelete<3){
            nrNodesToDelete = 3;
        }
        while(sa.unServedNodes.size ()!=nrNodesToDelete){
            int r=rand.nextInt (sa.nodes.size ());
            if(sa.unServedNodes.contains (sa.nodes.get (r))){
                continue;
            }
            sa.unServedNodes.add (sa.nodes.get (r));
        }

        // Remove selected nodes from the current schedule
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
}
