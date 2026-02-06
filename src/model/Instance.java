package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable-ish instance data container.
 *
 * <p>Holds the distance matrix, vehicle fleet definition, and customer demands.
 * Note: {@link #demands} is intentionally mutable because the initial solution constructor modifies demands in-place
 * (and stores an original copy in {@link #demands0}).</p>
 *
 * @author 20175993
 * @create 6/21/2018
 * @since 1.0.0
 */
public class Instance
{

    public final String name;
    public final int[][] distanceMatrix;
    public final int nrStations;
    public final List<Vehicle> vehicles;
    public final int nrProducts;
    //    public final int nrTrips;
    /* demand per customer per product demand[customer][product] */
    public int[][] demands;
    public int[][] demands0;
    public int distanceMax;
    public int demandsWithoutFullDTMax=-Integer.MAX_VALUE;
//    public final Graph<Integer, DefaultWeightedEdge> routingGraph;

    public Instance(String name, int[][] distanceMatrix, int nrStations, List<Vehicle> vehicles, int nrProducts, int nrTrips, int[][] demands)
    {
        this.name = name.substring (0, name.length ( ) - 4);
        this.distanceMatrix = distanceMatrix;
        this.nrStations = nrStations;
        this.vehicles = vehicles;
        this.nrProducts = nrProducts;
//        this.nrTrips = nrTrips;
        this.demands = demands;
        this.demands0=new int[demands.length][];
        for(int i=0;i<demands.length;i++){
            this.demands0[i]=new int[demands[i].length];
            for(int p=0;p<demands[i].length;p++){
                demands0[i][p]=demands[i][p];
            }
        }
        distanceMax=-Integer.MAX_VALUE;
        for(int i=0;i<distanceMatrix.length;i++){
            for(int j=0;j<distanceMatrix[i].length;j++){
                if(distanceMax<distanceMatrix[i][j]){
                    distanceMax=distanceMatrix[i][j];
                }
            }
        }
    }

    public String toString()
    {
        String s = name + "\n";
        s += "nrStations: " + nrStations + "\n" + "nrProducts:" + nrProducts + "\n" ;
        s += "Distance matrix:\n";
        for (int i = 0; i < nrStations + 2; i++)
            s += Arrays.toString (distanceMatrix[i]) + "\n";
        for (int i = 0; i < nrStations + 2; i++)
            s += Arrays.toString (demands[i]) + "\n";
        return s;
    }
}