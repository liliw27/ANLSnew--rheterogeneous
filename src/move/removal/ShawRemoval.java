package move.removal;

import SA.SimulatedAnnealing;
import model.CustomerNode;
import model.Instance;

import java.util.ArrayList;
import java.util.SplittableRandom;

/**
 * Shaw Removal（SR）移除算子。
 *
 * <p><b>与论文/经典 ALNS 对齐</b>：Shaw removal 会优先移除“相似”的 visits，从而在 repair 阶段形成更大的“局部重排空间”。</p>
 *
 * <p><b>实现概述</b>：</p>
 * <ol>
 *   <li>随机选择一个 seed visit v</li>
 *   <li>重复选择与当前 v 最相似的 visit，直到达到移除数量</li>
 *   <li>把这些 visits 从路线中删除，并放入 {@link SA.SimulatedAnnealing#unServedNodes}</li>
 * </ol>
 *
 * <p><b>相似度函数</b>：当前实现综合了三项（并做了归一化）：</p>
 * <ul>
 *   <li>客户间距离（越近越相似）</li>
 *   <li>需求向量差异（L1 距离，越小越相似）</li>
 *   <li>是否同一 trip/segment（同一 segment 更相似）</li>
 * </ul>
 *
 * <p>权重参数来自 {@link SA.SimulatedAnnealing#shawParameter1/#shawParameter2/#shawParameter3}。</p>
 *
 * @author 20175993
 * @create 7/4/2018
 * @since 1.0.0
 */
public class ShawRemoval extends Removal
    {
    protected SimulatedAnnealing sa;
    private final SplittableRandom rand;
    private final Instance dataModel;

    public ShawRemoval(SimulatedAnnealing sa, Instance dataModel)
    {
        this.sa = sa;
        this.rand = sa.rand;
        this.dataModel = dataModel;
    }

    @Override
    public void move()
    {
        sa.unServedNodes = new ArrayList<> ( );
        int nrNodesToDelete = rand.nextInt (sa.REMOVE_MAXNUM);
        while(nrNodesToDelete==0){
            nrNodesToDelete = rand.nextInt (sa.REMOVE_MAXNUM);
        }
        if(nrNodesToDelete<2){
            nrNodesToDelete = 2;
        }
        int r = rand.nextInt (sa.nodes.size ( ));
        CustomerNode customerNode = sa.nodes.get (r);
        sa.unServedNodes.add (customerNode);
        while (sa.unServedNodes.size ( ) != nrNodesToDelete)
        {
            CustomerNode mostSimilarNode = this.shawEvaluate (customerNode);
            sa.unServedNodes.add (mostSimilarNode);
            customerNode=mostSimilarNode;
        }

        for (CustomerNode node : sa.unServedNodes)
        {
            node.route.removeNode (node);
        }

    }

    public CustomerNode shawEvaluate(CustomerNode node)
    {
        double minValue = Double.MAX_VALUE;
        CustomerNode mostSimilarNode = sa.nodes.get (0);
        for (CustomerNode customerNode : sa.nodes)
        {
            if (sa.unServedNodes.contains (customerNode))
            {
                //do nothing
            }
            else
            {

                double simValue = this.getSimValue (node, customerNode);
                if (simValue < minValue)
                {
                    minValue = simValue;
                    mostSimilarNode = customerNode;
                }
            }
        }
        return mostSimilarNode;
    }

    public double getSimValue(CustomerNode node1, CustomerNode node2)
    {
        int sumq = 0;
        for (int p = 0; p < dataModel.nrProducts; p++)
        {
            sumq += Math.abs (node1.deliveryQuantity[p] - node2.deliveryQuantity[p]);
        }
//        double simValue = sa.shawParameter2 *Math.abs (dataModel.distanceMatrix[node1.index][0]-dataModel.distanceMatrix[0][node2.index])  + sa.shawParameter1 * sumq;

//        double simValue = 1.0*sa.shawParameter1 *Math.abs (dataModel.distanceMatrix[node1.index][node2.index]) /dataModel.distanceMax + 1.0*sa.shawParameter2 * sumq/dataModel.demandsWithoutFullDTMax;
        int temp=-1;
        if(node1.segment==node2.segment){
            temp=1;
        }
        double simValue = 1.0*sa.shawParameter1 *Math.abs (dataModel.distanceMatrix[node1.index][node2.index]) /dataModel.distanceMax + 1.0*sa.shawParameter2 * sumq/dataModel.demandsWithoutFullDTMax+1.0*sa.shawParameter3*temp;

        return simValue;
    }
}
