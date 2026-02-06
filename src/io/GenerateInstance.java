package io;

import model.Vehicle;

import java.io.*;
import java.util.*;

/**
 * Legacy utility for generating case-study instances from raw schedule-like text files.
 *
 * <p>This is <b>not</b> used by the main solver flow (Reader → SA). It is kept as a helper for data preparation.
 * The original implementation contained a lot of console printing and some raw-type iteration; we keep the logic
 * unchanged but make output optional and remove unchecked/raw-type warnings for readability.</p>
 *
 * @author wangli
 * @create 2018/12/31
 * @since 1.0.0
 */
public class GenerateInstance {

    /** Set to true when you want to inspect intermediate parsing results. */
    private static final boolean VERBOSE_LOG = false;

    private static void log(String msg) {
        if (VERBOSE_LOG) {
            System.out.println(msg);
        }
    }

    public Map<String, String> vehiclesForDepot() throws FileNotFoundException {
        File file = new File("./data/plan22.txt");
        Scanner scanner = new Scanner(file);
        int num = Integer.parseInt(scanner.nextLine());
        Set<String> vehicles = new HashSet<>();
        Map<String, String> depots = new HashMap<>();
        Map<String, List<String>> vehiclesForDepot = new HashMap<>();
        List<String> stringList = new ArrayList<>();
        for (int i = 0; i < num; i++) {

            String line = scanner.nextLine().trim();
            stringList.add(line);
            String[] split = line.split(" ");
            if (!split[7].equals("0号京标(Ⅵ)车用柴油")) {
                if (!vehicles.contains(split[1])) {
                    vehicles.add(split[1]);
                    depots.put(split[1], split[5]);
                }
                else if (!split[5].equals(depots.get(split[1]))) {
//                    vehicles.remove(split[1]);
                    depots.remove(split[1]);
                }
            }
        }
        for (Map.Entry<String, String> entry : depots.entrySet()) {
            if (!vehiclesForDepot.containsKey(entry.getValue())) {
                List<String> strings = new ArrayList<>();
                strings.add(entry.getKey());
                vehiclesForDepot.put(entry.getValue(), strings);
            }
            else {
                vehiclesForDepot.get(entry.getValue()).add(entry.getKey());
            }
        }
        scanner.close();
        Map<String, List<String>> stationsForDepot = new HashMap<>();
        Map<String, Integer[]> demands = new HashMap<>();
        Map<String, Vehicle> vehicleMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : vehiclesForDepot.entrySet()) {
            List<String> stations = new ArrayList<>();
            stationsForDepot.put(entry.getKey(), stations);
            int count = 0;
            for (String s : entry.getValue()) {
                int compNum = Integer.MIN_VALUE;
                int cap = Integer.MIN_VALUE;
                for (int i = 0; i < stringList.size(); i++) {
                    String[] split = stringList.get(i).split(" ");
                    if (split[1].equals(s)) {
                        if (Integer.parseInt(split[4]) > compNum) {
                            compNum = Integer.parseInt(split[4]);
                        }
                        if (Double.parseDouble(split[8]) > cap) {
                            cap = (int) Double.parseDouble(split[8]);
                        }
                        if (stations.contains(split[6])) {
                            if (split[7].equals("92号京标(Ⅵ)车用汽油")) {
                                demands.get(split[6])[0] += (int) Double.parseDouble(split[8]);
                            }
                            else if (split[7].equals("95号京标(Ⅵ)车用汽油")) {
                                demands.get(split[6])[1] += (int) Double.parseDouble(split[8]);
                            }
                        }
                        else {
                            stations.add(split[6]);
                            Integer[] demand = new Integer[2];
                            Arrays.fill(demand, 0);
                            if (split[7].equals("92号京标(Ⅵ)车用汽油")) {
                                demand[0] = (int) Double.parseDouble(split[8]);
                            }
                            else if (split[7].equals("95号京标(Ⅵ)车用汽油")) {
                                demand[1] = (int) Double.parseDouble(split[8]);
                            }
                            demands.put(split[6], demand);
                        }
                    }

                }
                Vehicle vehicle = new Vehicle(compNum, cap, count);
                vehicleMap.put(s, vehicle);
                count++;
            }
        }
        Map<String, List<String>> scheduleString = new HashMap<>();
        for (String s : vehicleMap.keySet()) {
            for (int i = 0; i < stringList.size(); i++) {
                String[] split = stringList.get(i).split(" ");
                if (split[1].equals(s)) {
                    if (scheduleString.keySet().contains(s)) {
                        scheduleString.get(s).add(stringList.get(i));
                    }

                    else {
                        List<String> strings = new ArrayList<>();
                        strings.add(stringList.get(i));
                        scheduleString.put(s, strings);
                    }
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : scheduleString.entrySet()) {
            List<RankedString> rankedStrings = new ArrayList<>();
            for (String s : entry.getValue()) {

                String[] split = s.split(" ");
                RankedString rankedString = new RankedString(s, Integer.parseInt(split[2]));
                rankedStrings.add(rankedString);
            }
            Collections.sort(rankedStrings);
            entry.getValue().clear();
            for (RankedString rankedString : rankedStrings) {
                entry.getValue().add(rankedString.s);
            }
        }
        Map<String, String> schedule = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : scheduleString.entrySet()) {
            String string="";
            Integer prevNo=0;
            for (int i=0;i< entry.getValue().size();i++) {
                String s= entry.getValue().get(i);
                String[] split=s.split(" ");
                if(i==0){
                    string+=split[5]+" "+split[6]+" ";
                    prevNo=Integer.parseInt(split[9]);
                }
                else if(prevNo + 1 == Integer.parseInt(split[9])){
                    string+=split[6]+" ";
                    prevNo=Integer.parseInt(split[9]);
                }
                else if(i== entry.getValue().size()-1){
                    string+=split[5]+" "+split[6]+" ";
                    prevNo=Integer.parseInt(split[9]);
                    string+=split[5]+" ";
                }
                else{
                    string+=split[5]+" "+split[6]+" ";
                    prevNo=Integer.parseInt(split[9]);
                }
            }
            schedule.put(entry.getKey(), string.trim());
            }
        log("demands");
        for (Map.Entry<String, List<String>> entry : stationsForDepot.entrySet()) {
            log(entry.getKey());
            for (String s : entry.getValue()) {
                for (Map.Entry<String, Integer[]> entry1 : demands.entrySet()) {
                    if (s.equals(entry1.getKey())) {
                        log(entry1.getKey() + " " + Arrays.toString(entry1.getValue()));
                    }
                }
            }

        }
        log("stations");
        for (Map.Entry<String, List<String>> entry : stationsForDepot.entrySet()) {
            log(entry.getKey()+"============");
            for (String s : entry.getValue()) {
                for (Map.Entry<String, Integer[]> entry1 : demands.entrySet()) {
                    if (s.equals(entry1.getKey())) {
                        log(entry1.getKey());
                    }
                }
            }

        }
        log("vehicles");
        for (Map.Entry<String, List<String>> entry : vehiclesForDepot.entrySet()) {
            log(entry.getKey());
            for (String s : entry.getValue()) {
                for (Map.Entry<String, Vehicle> entry1 : vehicleMap.entrySet()) {
                    if (s.equals(entry1.getKey())) {
                        log(entry1.getKey() + " " + entry1.getValue());
                    }
                }
            }

        }
        log("schedule");

        for (Map.Entry<String, List<String>> entry : vehiclesForDepot.entrySet()) {
            log(entry.getKey());
            for (String s : entry.getValue()) {
                for (Map.Entry<String, String> entry1 : schedule.entrySet()) {
                    if (s.equals(entry1.getKey())) {
                        log(entry1.getKey() + " " + entry1.getValue());
                    }
                }
            }
        }
        getLatLng(stationsForDepot);
        return depots;
    }
    public void getLatLng(Map<String, List<String>> stationsForDepot) throws FileNotFoundException {
        Map<String,Double[]> latLng=new HashMap<>();
        File file = new File("./data/latAndLng.txt");
        Scanner scanner = new Scanner(file);
        int num = Integer.parseInt(scanner.nextLine());
        for(int i=0;i<num;i++){
            String s=scanner.nextLine().trim();
            String[] split=s.split(" ");
            Double[] latiLng=new Double[2];
            latiLng[0]=Double.parseDouble(split[1]);
            latiLng[1]=Double.parseDouble(split[2]);
            latLng.put(split[0],latiLng);
        }
        scanner.close();
        for (Map.Entry<String, List<String>> entry : stationsForDepot.entrySet()) {
            log(entry.getKey()+"============");
            for (String s : entry.getValue()) {
                for (Map.Entry<String, Double[]> entry1 : latLng.entrySet()) {
                    if (s.equals(entry1.getKey())) {
                        log(entry1.getKey()+" "+Arrays.toString(entry1.getValue()) );
                    }
                }
            }

        }
    }
    public Set<String> getStations() throws FileNotFoundException {
        File file = new File("./data/stations");
        Scanner scanner = new Scanner(file);
        int num = Integer.parseInt(scanner.nextLine());
        Set<String> stations = new HashSet<>();
        for(int i=0;i<num;i++){
            String line=scanner.nextLine().trim();
            String[] split=line.split(" ");
            if(!stations.contains(split[0])){
                stations.add(split[0]);
                log(split[0]);
            }
        }
        scanner.close();
        return stations;
    }
public void getInstance(int i) throws FileNotFoundException {
        File file =new File("./data/case/"+i+".txt");
        Scanner scanner=new Scanner(file);

        ArrayList<Integer[]> demands=new ArrayList<>();
        ArrayList<Vehicle> vehicles=new ArrayList<>();
        Integer[][] distanceMatrix;
        Integer[][] distance=new Integer[1][1];
        int count=0;
        boolean flag=true;
        do{
            String line=scanner.nextLine().trim();
            if(line.substring(line.length()-1,line.length()).equals("]")&&!line.substring(0,1).equals("[")){
                line=line.replaceAll(", ",",");
                String[] split=line.split(" ");
                String s=split[1].substring(1,split[1].length()-1);
                split=s.split(",");
                Integer[] demand=new Integer[2];
                demand[0]=Integer.parseInt(split[0]);
                demand[1]=Integer.parseInt(split[1]);
                demands.add(demand);
            }
            else if(line.substring(0,1).equals("京"))
            {
                String[] split=line.split(" ");
                Vehicle vehicle=new Vehicle(Integer.parseInt(split[1]),Integer.parseInt(split[2])/1000,Integer.parseInt(split[3]));
                vehicles.add(vehicle);

            }
            else if(line.substring(line.length()-1,line.length()).equals("]")&&line.substring(0,1).equals("[")){


                if(flag==true){

                    flag=false;
                    line=line.substring(1,line.length()-1);
                    String[] split=line.split(", ");
                     distance=new Integer[split.length][split.length];
                    for(int j=0;j<split.length;j++){
                        distance[count][j]=Integer.parseInt(split[j]);
                    }

                }
                else{
                    line=line.substring(1,line.length()-1);
                    String[] split=line.split(", ");

                    for(int j=0;j<split.length;j++){
                        distance[count][j]=Integer.parseInt(split[j]);
                    }
                }
                count++;

            }


        }while (scanner.hasNextLine());
scanner.close();
        distanceMatrix=new Integer[distance.length+1][distance.length+1];
        for(int j=0;j<distanceMatrix.length;j++){
            for(int k=0;k<distanceMatrix.length;k++){
                if(j==distance.length){
                    distanceMatrix[j][k]=distanceMatrix[0][k];
                }
                else{
                    if(k==distance.length){
                        distanceMatrix[j][k]=distanceMatrix[j][0];
                    }
                    else{
                        distanceMatrix[j][k]=distance[j][k];
                    }
                }
            }

        }
String s="";

    s+="TRIPS: 6\n";
    s+="VEHICLES: "+vehicles.size()+"\n";
    s+="NRSTATIONS: "+demands.size()+"\n";
    s+="NRCOMPARTMENTS: 3\n";
    s+="CAPACITY: 8000\n";
    s+="NRPRODUCTS: 2\n";
    s+="VEHICLE SECTION <compartment number> <capacity> <vehicle_id>\n";
    for(int j=0;j<vehicles.size();j++){
        s+=vehicles.get(j).compartmentNum+" "+vehicles.get(j).comCapacity+" "+vehicles.get(j).vIndex+"\n";
    }
    s+="DEMANDS SECTION <customer_id> <demand_p1> <demand_p2> ... <demand_NRPRODUCTS>\n";
    s+="0 0 0\n";
    for(int j=0;j<demands.size();j++){
        s+=(j+1)+" "+demands.get(j)[0]+" "+demands.get(j)[1]+"\n";
    }
    s+=(demands.size()+1)+" 0 0\n";
    s+="DISTANCE SECTION (full distance matrix 0=depot 1 2 ... NRSTATION)\n";
    for(int j=0;j<distanceMatrix.length;j++){
        for(int k=0;k<distanceMatrix.length;k++){
            s+=distanceMatrix[j][k]+" ";
        }
        s.trim();
        if(j<distanceMatrix.length-1)
        s+="\n";
    }
    BufferedWriter writer = null;
    try
    {
        writer = new BufferedWriter (new FileWriter("./data/Instance/caseStudy/"+i+".txt"));
        writer.write (s.toString ( ));
        writer.flush ( );
    }
    catch (IOException e)
    {
        // ignored (legacy utility)
    }
    finally
    {
        try
        {
            if (writer != null) writer.close ( );
        }
        catch (IOException e)
        {
            // ignored
        }
    }
}
    private class RankedString implements Comparable<RankedString> {
        private String s;
        private Integer rankNumber;

        private RankedString(String s, int rankNumber) {
            this.s = s;
            this.rankNumber = rankNumber;
        }

        private Integer getRankNumber() {
            return rankNumber;
        }

        public int compareTo(RankedString arg0) {
            return this.getRankNumber().compareTo(arg0.getRankNumber());
        }//increasing order
    }

    public static void main(String[] args) throws FileNotFoundException {
        GenerateInstance generateInstance = new GenerateInstance();
       generateInstance.vehiclesForDepot();
//        generateInstance.getStations();
        for(int i=1;i<29;i++){
            generateInstance.getInstance(i);
        }

    }
}