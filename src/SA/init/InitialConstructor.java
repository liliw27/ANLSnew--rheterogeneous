package SA.init;

import SA.SimulatedAnnealing;
import move.insertion.evaluation.EvaluateInsertion;
import move.insertion.evaluation.Evaluation;
import move.insertion.evaluation.InsertType;
import model.*;

import java.util.*;

/**
 * 初始可行解构造器（Initial Solution Constructor）。
 *
 * <p>目标：在满足多舱（multi-compartment）、多产品、多趟（multi-trip）和可拆分配送（split delivery）约束下，
 * 构造一个可行的初始解供 ALNS+SA 主循环优化。</p>
 *
 * <p><b>实现概要（建议按此顺序阅读代码）</b>：</p>
 * <ol>
 *   <li><b>为每个客户选择一辆“参考车辆”</b>（启发式分配，仅用于估算直达趟次的拆分规则）。</li>
 *   <li><b>为每个客户生成直达趟次（depot→customer→depot）的 visit</b>，分两类：
 *     <ul>
 *       <li><b>满载直达（Full-load direct trips / FTL）</b>：整舱满载的部分，可生成若干个满载 visit。</li>
 *       <li><b>非满载直达（Unfull-load direct trips / LTL）</b>：剩余需求不足以再组成一次满载，但仍可构造一趟直达。</li>
 *     </ul>
 *   </li>
 *   <li><b>将剩余需求作为 reduced instance</b>：对每个仍有剩余需求的客户，生成一个“剩余需求 visit”。</li>
 *   <li><b>依次插入 reduced visits</b>：对每个剩余 visit，调用 {@link move.insertion.evaluation.EvaluateInsertion} 在
 *       II/SI/STI 三种策略中选择增量最小的插入方式。</li>
 * </ol>
 *
 * <p><b>重要实现细节</b>：本构造过程会<b>原地修改</b> {@link model.Instance#demands}，将已被直达趟次覆盖的需求从
 * demands 中扣除，仅保留剩余需求。原始需求在 {@link model.Instance#demands0} 中保留。</p>
 *
 * @author 20175993
 * @create 6/27/2018
 * @since 1.0.0
 */
public class InitialConstructor
{
    public Instance dataModel;
    public SimulatedAnnealing sa;
    public List<CustomerNode> fullDirectNodes = new ArrayList<> ( );
    public List<CustomerNode> unFullDirectNodes = new ArrayList<> ( );
    public List<CustomerNode> reducedInstanceNodes = new ArrayList<> ( );
    private final SplittableRandom rand;

    public InitialConstructor(Instance dataModel, SimulatedAnnealing sa)
    {
        this.dataModel = dataModel;
        this.sa = sa;
        this.rand=new SplittableRandom (sa.RANDOM_SEED);

    }

    public void initial()
    {

        // -----------------------------
        // Step 0: Heuristically assign a "reference vehicle" to each customer.
        // -----------------------------
        // This is a constructive heuristic used to decide how to split each customer's demand into
        // direct trips (FTL/LTL) based on the assigned vehicle's compartment number/capacity.
        int m=(int) Math.floor((1.0*dataModel.nrStations)/dataModel.vehicles.size());
        int largerM=dataModel.nrStations%dataModel.vehicles.size();
        List<Customer> customers=new ArrayList<>();
        Vehicle[] vehiclesForC=new Vehicle[dataModel.nrStations];
        Arrays.fill(vehiclesForC,dataModel.vehicles.get(0));


        for(int i=1;i<dataModel.nrStations+1;i++){
            int q=0;
            for(int p=0;p<dataModel.nrProducts;p++){
                q+=dataModel.demands[i][p];
            }
            Customer customer=new Customer(i,q);
            customers.add(customer);
        }
        Collections.sort(customers);
        int[] countForV=new int[dataModel.vehicles.size()];
        for(int k=0;k<countForV.length;k++){
            countForV[k]=m;
            if(k<largerM){
                countForV[k]++;
            }
        }
        int count=0;
        for(int i=0;i<countForV.length;i++){
            for(int j=0;j<countForV[i];j++){
                vehiclesForC[customers.get(count).index-1]=dataModel.vehicles.get(i);
                count++;
            }
        }

        // -----------------------------
        // Step 1: Generate direct trips (FTL/LTL) and reduced-instance residual visits.
        // -----------------------------
        for (int i = 1; i < dataModel.nrStations + 1; i++)
        {
            int count1;
            int count2;
            int[] fullCompartments = new int[dataModel.nrProducts];//the number of full load compartments for product p that the customer i needs.
            int[] compartments = new int[dataModel.nrProducts];//the number of compartments for product p that the customer i needs.
            int totalFullCompartments = 0;
            int totalCompartments = 0;

            // 1.1 Full-load direct trips (FTL): extract the part that can be delivered in full compartments.
            for (int p = 0; p < dataModel.nrProducts; p++)
            {
                fullCompartments[p] = (int) Math.floor (dataModel.demands[i][p] * 1.0 / vehiclesForC[i-1].comCapacity);
                totalFullCompartments += fullCompartments[p];
            }

            count1 = (int) Math.floor (totalFullCompartments * 1.0 / vehiclesForC[i-1].compartmentNum) * vehiclesForC[i-1].compartmentNum;
            while (count1 > 0)
            {
                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    if (fullCompartments[p] <= count1)
                    {
                        dataModel.demands[i][p] = dataModel.demands[i][p] - fullCompartments[p] * vehiclesForC[i-1].comCapacity;
                        count1 = count1 - fullCompartments[p];
                    }
                    else
                    {
                        dataModel.demands[i][p] = dataModel.demands[i][p] - count1 * vehiclesForC[i-1].comCapacity;
                        count1 = 0;
                    }
                }
            }
            count1 = (int) Math.floor (totalFullCompartments * 1.0 / vehiclesForC[i-1].compartmentNum);//how many full load direct trips generated for customer i
            //Generate Direct Trip
            if (count1 > 0)
            {
                int[][] x1 = new int[count1][dataModel.nrProducts];//how many compartments allocate to a direct trip for product p
                int[][] q1 = new int[count1][dataModel.nrProducts];//delivery quantity allocate to a direct trip for product p
                int compatmentResidual = vehiclesForC[i-1].compartmentNum;
                int c = 0;
                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    if (fullCompartments[p] < compatmentResidual)
                    {
                        x1[c][p] = fullCompartments[p];
                        compatmentResidual = compatmentResidual - fullCompartments[p];
                        fullCompartments[p] = 0;

                    }
                    else
                    {
                        x1[c][p] = compatmentResidual;
                        fullCompartments[p] = fullCompartments[p] - compatmentResidual;
                        compatmentResidual = vehiclesForC[i-1].compartmentNum;
                        c++;
                        if (fullCompartments[p] > 0)
                        {
                            p--;
                        }
                        if (c == count1)
                        {
                            break;
                        }
                    }
                }
                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    for (int j = 0; j < count1; j++)
                    {
                        q1[j][p] = x1[j][p] * vehiclesForC[i-1].comCapacity;
                    }
                }
                //full-load direct trip customerNodes
                for (int t = 0; t < count1; t++)
                {
                    CustomerNode customerNode = new CustomerNode (i, q1[t]);
                    fullDirectNodes.add (customerNode);
                }
            }
            // record maximal demands without full direct nodes
            int s=0;
            for (int p = 0; p < dataModel.nrProducts; p++)
            {
                s+=dataModel.demands[i][p];
            }
            if(dataModel.demandsWithoutFullDTMax<s){
                dataModel.demandsWithoutFullDTMax=s;
            }
            // 1.2 Unfull-load direct trips (LTL): build (at most) several direct trips that are not fully loaded.
            int[] d = new int[dataModel.nrProducts];

            for (int p = 0; p < dataModel.nrProducts; p++)
            {
                d[p] = dataModel.demands[i][p];
                compartments[p] = (int) Math.ceil (dataModel.demands[i][p] * 1.0 /vehiclesForC[i-1].comCapacity);
                totalCompartments += compartments[p];
            }
            count2 = (int) Math.ceil (totalCompartments * 1.0 / vehiclesForC[i-1].compartmentNum - 1) * vehiclesForC[i-1].compartmentNum;
            //Generate Direct Trip
            count2 = (int) Math.ceil (totalCompartments * 1.0 / vehiclesForC[i-1].compartmentNum - 1);
            if (count2 > 0)
            {
                int[][] x2 = new int[count2][dataModel.nrProducts];//how many compartments allocate to a direct trip for product p
                int[][] q2 = new int[count2][dataModel.nrProducts];//delivery quantity allocate to a direct trip for product p
                int compatmentResidual = vehiclesForC[i-1].compartmentNum;
                int c = 0;
                int[] compartments0 = new int[dataModel.nrProducts];
                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    compartments0[p] = compartments[p];
                }

                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    if (compartments[p] < compatmentResidual)
                    {
                        x2[c][p] = compartments[p];
                        compatmentResidual = compatmentResidual - compartments[p];
                        compartments[p] = 0;

                    }
                    else
                    {
                        x2[c][p] = compatmentResidual;
                        compartments[p] = compartments[p] - compatmentResidual;
                        compatmentResidual = vehiclesForC[i-1].compartmentNum;
                        c++;
                        if (c == count2)
                        {
                            break;
                        }
                        if (compartments[p] > 0)
                        {
                            p--;
                        }
                    }
                }


                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    for (int j = 0; j < count2; j++)
                    {
                        q2[j][p] = x2[j][p] * vehiclesForC[i-1].comCapacity;
                    }
                }
                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    for (int j = 0; j < count2; j++)
                    {
                        if (q2[j][p] > 0)
                        {
                            q2[j][p] = q2[j][p] - (compartments0[p] * vehiclesForC[i-1].comCapacity - d[p]);
                            break;
                        }
                    }
                }
                for (int p = 0; p < dataModel.nrProducts; p++)
                {
                    int qDirect = 0;
                    for (int j = 0; j < count2; j++)
                    {
                        qDirect += q2[j][p];
                    }
                    dataModel.demands[i][p] -= qDirect;
                }
                //Generate unfull-load direct trip customerNodes
                for (int t = 0; t < count2; t++)
                {
                    CustomerNode customerNode = new CustomerNode (i, q2[t]);
                    unFullDirectNodes.add (customerNode);
                }
            }
        }
        // -----------------------------
        // Step 2: Build reduced instance visits (residual demand after direct trips).
        // -----------------------------
        for (int i = 1; i < dataModel.nrStations + 1; i++)
        {
            int s=0;
            for(int p=0;p<dataModel.nrProducts;p++){
                s+=dataModel.demands[i][p];
            }
            if(s>0){
                CustomerNode customerNode = new CustomerNode (i, dataModel.demands[i]);
                reducedInstanceNodes.add (customerNode);
            }

        }

        // -----------------------------
        // Step 3: Build an initial feasible schedule (routes + insert visits).
        // -----------------------------
        // 3.1 Create one empty route per vehicle.
        for (int i = 0; i < dataModel.vehicles.size(); i++)
        {
            sa.routes[i] = new Route (i, dataModel);

        }
        // 3.2 Insert full direct trips: each such visit is placed as a direct trip for its assigned vehicle.

        for (CustomerNode customerNode : fullDirectNodes)
        {
            int k=vehiclesForC[customerNode.index-1].vIndex;
            if (sa.routes[k].nrNodes==2)
            {
                Node prevNode = sa.routes[k].routeEnd.prev;
                prevNode.route.insertCustomerNode (prevNode, customerNode);
            }
            else
            {
                Node prevNode = sa.routes[k].routeEnd.prev;
                prevNode.route.insertDepot (prevNode);
                prevNode = sa.routes[k].routeEnd.prev;
                prevNode.route.insertCustomerNode (prevNode, customerNode);
            }

//            customerNode.segment.isFullDirect = true;
        }
        // 3.3 Insert unfull direct trips (also as direct trips).
        for (CustomerNode customerNode : unFullDirectNodes)
        {
            int k=vehiclesForC[customerNode.index-1].vIndex;
            if (sa.routes[k].nrNodes==2)
            {
                Node prevNode = sa.routes[k].routeEnd.prev;
                prevNode.route.insertCustomerNode (prevNode, customerNode);
            }
            else
            {
                Node prevNode = sa.routes[k].routeEnd.prev;
                prevNode.route.insertDepot (prevNode);
                prevNode = sa.routes[k].routeEnd.prev;
                prevNode.route.insertCustomerNode (prevNode, customerNode);
            }

        }
        // 3.4 Insert reduced residual-demand visits using EvaluateInsertion (II / SI / STI).
        List<RankedCustomer> rankedCustomers = new ArrayList<> ( );
        for (CustomerNode customer : reducedInstanceNodes)
        {
            int s = 0;
            for (int p = 0; p < dataModel.nrProducts; p++)
            {
                s += customer.deliveryQuantity[p];
            }
            if (s > 0)
            {
                RankedCustomer rankedCustomer = new RankedCustomer (customer, dataModel.distanceMatrix[0][customer.index]);
                rankedCustomers.add (rankedCustomer);
            }

        }
        Collections.sort (rankedCustomers);

//        //step2:generate "nrIniTrips" direct trips with "seed" customers
//        int nrIniTrips;//the number of initial trips with "seed" customers
//        int totalCompartments = 0;
//        for (int p = 0; p < dataModel.nrProducts; p++)
//        {
//            int d = 0;
//            for (int i = 1; i <= dataModel.nrStations; i++)
//            {
//                d += dataModel.demands[i][p];
//            }
//            totalCompartments += (int) Math.ceil (d * 1.0 / dataModel.capacity);
//        }
//        nrIniTrips = (int) Math.ceil (totalCompartments * 1.0 / dataModel.nrCompartments);
//
//        for (int i = 0; i < nrIniTrips; i++)
//        {
//            CustomerNode customerNode = rankedCustomers.remove (0).customer;
//            if (flag == false)
//            {
//                Node prevNode = sa.routes[k].routeEnd.prev;
//                prevNode.route.insertCustomerNode (prevNode, customerNode);
//            }
//            else
//            {
//                Node prevNode = sa.routes[k].routeEnd.prev;
//                prevNode.route.insertDepot (prevNode);
//                prevNode = sa.routes[k].routeEnd.prev;
//                prevNode.route.insertCustomerNode (prevNode, customerNode);
//            }
//            k++;
//            if (k == sa.routes.length)
//            {
//                k = 0;
//                flag = true;
//            }
//        }
////        sa.printRoutes ();
//Step3.Sequentially insert a customer from the list into the existing trips at the best position
//        String s="";
//        for (int j = 0; j < rankedCustomers.size ( ); j++){
//            s+=rankedCustomers.get (j).customer;
//        }
//        System.out.println ( s);
//        sa.unServedNodes=new ArrayList<> ();
//        for (int j = 0; j < rankedCustomers.size ( ); j++){
//           sa.unServedNodes.add (rankedCustomers.get (j).customer);
//        }
//        sa.nodesFullLoad.addAll (fullDirectNodes);
//        sa.nodes.addAll (unFullDirectNodes);
//        sa.nodes.addAll (reducedInstanceNodes);
//        // 历史遗留：曾使用 MoveInsert 接口（已移除）。保留该行仅作为“旧版本参考”。
//        // new GreedyInsertion(sa, dataModel);
//        nextInsert.move ();
        for (int j = 0; j < rankedCustomers.size ( ); j++)
        {
            CustomerNode customerNode = rankedCustomers.get (j).customer;
            double r=this.rand.nextDouble ();
            EvaluateInsertion evaluateInsertion = new EvaluateInsertion (customerNode, dataModel, sa,r);
            Evaluation eval = evaluateInsertion.evaluate ( );
            if (eval.insertType == InsertType.INSERT_NODE)
            {
                Node prevNode = eval.prevNode;
                prevNode.route.insertCustomerNode (prevNode, customerNode);
            }
            else if (eval.insertType == InsertType.INSERT_SPLITDELIVERY)
            {
                reducedInstanceNodes.remove (customerNode);

                for (int i = 0; i < eval.prevNodesSD.size ( ); i++)
                {
                    Node prevNode = eval.prevNodesSD.get (i);
                    Node node = eval.insertingNodes.get (i);
                    reducedInstanceNodes.add ((CustomerNode) node);
                    prevNode.route.insertCustomerNode (prevNode, node);
                }
            }
            else if (eval.insertType == InsertType.INSERT_MULTITRIPID)
            {
                Node prevNode = eval.prevNode;
                prevNode.route.insertDepot (prevNode);
                prevNode.route.insertCustomerNode (prevNode, customerNode);

            }
            else if (eval.insertType == InsertType.INSERT_MULTITRIPDI)
            {
                Node prevNode = eval.prevNode;
                prevNode.route.insertDepot (prevNode);
                prevNode.route.insertCustomerNode (prevNode.next, customerNode);
            }
        }
        sa.nodesFullLoad.addAll (fullDirectNodes);
        sa.nodes.addAll (unFullDirectNodes);
        sa.nodes.addAll (reducedInstanceNodes);
        sa.nodes.addAll (fullDirectNodes);

    }

    private class Customer implements Comparable<Customer>
    {
        private int index;
//        private int[] demand=new int[dataModel.nrProducts];
        private Integer rankNumber;

        private Customer(int index, int rankNumber)
        {
            this.index=index;
//            this.demand=demand;
            this.rankNumber = rankNumber;
        }

        private Integer getRankNumber()
        {
            return rankNumber;
        }

        public int compareTo(Customer arg0)
        {
            return arg0.getRankNumber ( ).compareTo (this.getRankNumber ( ));
        }//decreasing order
    }

    private class RankedCustomer implements Comparable<RankedCustomer>
    {
        private CustomerNode customer;
        private Integer rankNumber;

        private RankedCustomer(CustomerNode customer, int rankNumber)
        {
            this.customer = customer;
            this.rankNumber = rankNumber;
        }

        private Integer getRankNumber()
        {
            return rankNumber;
        }

        public int compareTo(RankedCustomer arg0)
        {
            return arg0.getRankNumber ( ).compareTo (this.getRankNumber ( ));
        }//decreasing order
    }
}
