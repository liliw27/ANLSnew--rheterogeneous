package io;

import model.Instance;
import model.Vehicle;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * 实例读取器（文本格式）。
 *
 * <p>本项目的实例文件位于 {@code data/Instance/.../*.txt}，但历史上存在<strong>两种</strong>常见格式：</p>
 * <ol>
 *   <li><b>异构车辆格式</b>：头部 + {@code VEHICLE SECTION} + {@code DEMANDS SECTION} + {@code DISTANCE SECTION}</li>
 *   <li><b>同构车辆格式</b>：头部（含 {@code NRCOMPARTMENTS}/{@code CAPACITY}）+ {@code DEMANDS SECTION} + {@code DISTANCE SECTION}</li>
 * </ol>
 *
 * <p><b>重要实现细节</b>：</p>
 * <ul>
 *   <li><b>需求缩放</b>：需求按 {@code ceil(d/1000)} 缩放为“千单位”，降低数值规模（与论文/原实现一致）。</li>
 *   <li><b>车辆排序/重编号</b>：按总容量（舱数*舱容量）降序排序，并重写 {@code vIndex}，便于后续启发式分配。</li>
 *   <li><b>三角不等式修正</b>：对距离矩阵做松弛，使其更接近满足三角不等式（论文常见假设）。</li>
 * </ul>
 *
 * @author 20175993
 * @create 6/21/2018
 * @since 1.0.0
 */
public class Reader
{
    public static Instance readInstance(File file) throws FileNotFoundException
    {
        try (Scanner scanner = new Scanner(file)) {
            // -----------------------------
            // 1) 读取头部（key:value）直到进入 section
            // -----------------------------
            Header header = new Header();
            String line;
            while (scanner.hasNextLine()) {
                line = readLine(scanner);
                if (line.isEmpty()) continue;

                String upper = line.toUpperCase();
                if (upper.startsWith("VEHICLE SECTION")) {
                    header.hasVehicleSection = true;
                    break;
                }
                if (upper.startsWith("DEMANDS SECTION")) {
                    header.hasVehicleSection = false;
                    break;
                }

                header.tryParseHeaderLine(line);
            }

            header.validateOrThrow(file);

            // -----------------------------
            // 2) 读取车辆（可能有 VEHICLE SECTION，也可能没有）
            // -----------------------------
            List<Vehicle> vehicles;
            if (header.hasVehicleSection) {
                vehicles = readVehiclesHeterogeneous(scanner, header.nrVehicles);
                // 读完车辆后，下一行应该是 DEMANDS SECTION
                if (scanner.hasNextLine()) {
                    String maybeDemands = readLine(scanner);
                    if (!maybeDemands.toUpperCase().startsWith("DEMANDS SECTION")) {
                        throw new IllegalArgumentException("实例格式错误：期望 DEMANDS SECTION，但读到：" + maybeDemands);
                    }
                } else {
                    throw new IllegalArgumentException("实例格式错误：缺少 DEMANDS SECTION");
                }
            } else {
                // 同构车辆：文件没有 VEHICLE SECTION，只能用头部 NRCOMPARTMENTS/CAPACITY 生成车辆
                vehicles = buildHomogeneousVehicles(header.nrVehicles, header.nrCompartments, header.capacity);
            }

            // 车辆排序/重编号（对两种格式都做，保持原实现一致）
            vehicles = sortVehiclesByCapacityDescending(vehicles);

            // -----------------------------
            // 3) 读取需求（DEMANDS SECTION 下方固定为 nrStations+2 行）
            // -----------------------------
            int[][] demands = readDemands(scanner, header.nrStations, header.nrProducts);

            // -----------------------------
            // 4) 读取距离矩阵（跳过 DISTANCE SECTION 说明行）
            // -----------------------------
            int[][] distanceMatrix = readDistanceMatrix(scanner, header.nrStations);

            // 三角不等式修正（保留历史边界：只修正到 nrStations+1，最后一行/列通常是“虚拟终点/回仓”）
            relaxTriangleInequality(distanceMatrix, header.nrStations);

            return new Instance(file.getName(), distanceMatrix, header.nrStations, vehicles, header.nrProducts, header.nrTrips, demands);
        }
    }

    public static String readLine(Scanner scanner) {
        return scanner.nextLine().trim();
    }

    // -----------------------------
    // 解析实现细节（为提升可读性拆成小函数）
    // -----------------------------

    private static List<Vehicle> readVehiclesHeterogeneous(Scanner scanner, int nrVehicles) {
        List<Vehicle> vehicles = new ArrayList<>();
        for (int k = 0; k < nrVehicles; k++) {
            if (!scanner.hasNextLine()) {
                throw new IllegalArgumentException("实例格式错误：VEHICLE SECTION 行数不足，期望 " + nrVehicles + " 行");
            }
            String line = readLine(scanner);
            if (line.isEmpty()) {
                k--;
                continue;
            }
            String[] split = line.split("\\s+");
            // 格式：<compartment number> <capacity> <vehicle_id>（后面可能还有字段）
            int compartments = Integer.parseInt(split[0]);
            int capacityPerCompartment = Integer.parseInt(split[1]);
            vehicles.add(new Vehicle(compartments, capacityPerCompartment, k));
        }
        return vehicles;
    }

    private static List<Vehicle> buildHomogeneousVehicles(int nrVehicles, int nrCompartments, int capacity) {
        List<Vehicle> vehicles = new ArrayList<>();
        int scaledCapacity = scaleToThousandsCeil(capacity);
        for (int k = 0; k < nrVehicles; k++) {
            vehicles.add(new Vehicle(nrCompartments, scaledCapacity, k));
        }
        return vehicles;
    }

    private static int[][] readDemands(Scanner scanner, int nrStations, int nrProducts) {
        // 下一行通常是 DEMANDS SECTION（如果前面是同构格式，则 header 已经读到了 DEMANDS SECTION 这一行）
        // 为了健壮性：如果接下来仍然是 section 行，则跳过。
        if (scanner.hasNextLine()) {
            String peek = readLine(scanner);
            if (!peek.toUpperCase().startsWith("DEMANDS SECTION")) {
                // 不是标题行，说明 header 循环已停在 DEMANDS SECTION，此处读到的是第一条 demand，需要回退处理。
                // Scanner 无法回退：因此这里把它当作“第一条 demand 行”直接解析。
                return readDemandsWithFirstLine(peek, scanner, nrStations, nrProducts);
            }
        } else {
            throw new IllegalArgumentException("实例格式错误：缺少 DEMANDS SECTION");
        }

        // 标题行已跳过，读取 nrStations+2 行需求
        int[][] demands = new int[nrStations + 2][nrProducts];
        for (int i = 0; i < nrStations + 2; i++) {
            if (!scanner.hasNextLine()) {
                throw new IllegalArgumentException("实例格式错误：DEMANDS SECTION 行数不足，期望 " + (nrStations + 2) + " 行");
            }
            String line = readLine(scanner);
            if (line.isEmpty()) {
                i--;
                continue;
            }
            fillDemandRow(demands, i, line, nrProducts);
        }
        return demands;
    }

    private static int[][] readDemandsWithFirstLine(String firstLine, Scanner scanner, int nrStations, int nrProducts) {
        int[][] demands = new int[nrStations + 2][nrProducts];
        fillDemandRow(demands, 0, firstLine, nrProducts);

        int row = 1;
        while (row < nrStations + 2) {
            if (!scanner.hasNextLine()) {
                throw new IllegalArgumentException("实例格式错误：DEMANDS SECTION 行数不足，期望 " + (nrStations + 2) + " 行");
            }
            String line = readLine(scanner);
            if (line.isEmpty()) continue;
            fillDemandRow(demands, row, line, nrProducts);
            row++;
        }
        return demands;
    }

    private static void fillDemandRow(int[][] demands, int rowIdx, String line, int nrProducts) {
        String[] split = line.split("\\s+");
        if (split.length < nrProducts + 1) {
            throw new IllegalArgumentException("DEMANDS 行字段数不足：row=" + rowIdx + " line=" + line);
        }
        for (int j = 1; j <= nrProducts; j++) {
            int d = Integer.parseInt(split[j]);
            demands[rowIdx][j - 1] = d > 0 ? scaleToThousandsCeil(d) : 0;
        }
    }

    private static int[][] readDistanceMatrix(Scanner scanner, int nrStations) {
        int n = nrStations + 2;

        // 跳过 DISTANCE SECTION 说明行（或其他空行）直到读到“全是数字”的第一行
        String firstMatrixLine = null;
        while (scanner.hasNextLine()) {
            String line = readLine(scanner);
            if (line.isEmpty()) continue;
            if (looksLikeNumberRow(line)) {
                firstMatrixLine = line;
                break;
            }
        }
        if (firstMatrixLine == null) {
            throw new IllegalArgumentException("实例格式错误：找不到距离矩阵起始行（DISTANCE SECTION）");
        }

        int[][] distanceMatrix = new int[n][n];
        fillDistanceRow(distanceMatrix, 0, firstMatrixLine, n);

        int row = 1;
        while (row < n) {
            if (!scanner.hasNextLine()) {
                throw new IllegalArgumentException("实例格式错误：距离矩阵行数不足，期望 " + n + " 行");
            }
            String line = readLine(scanner);
            if (line.isEmpty()) continue;
            fillDistanceRow(distanceMatrix, row, line, n);
            row++;
        }
        return distanceMatrix;
    }

    private static void fillDistanceRow(int[][] distanceMatrix, int rowIdx, String line, int n) {
        String[] split = line.split("\\s+");
        if (split.length < n) {
            throw new IllegalArgumentException("距离矩阵行字段数不足：row=" + rowIdx + " expected=" + n + " line=" + line);
        }
        for (int j = 0; j < n; j++) {
            distanceMatrix[rowIdx][j] = (int) Math.ceil(Integer.parseInt(split[j]));
        }
    }

    private static boolean looksLikeNumberRow(String line) {
        // 粗略判断：第一段能 parseInt 即视为数字行
        String[] split = line.split("\\s+");
        if (split.length == 0) return false;
        try {
            Integer.parseInt(split[0]);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int scaleToThousandsCeil(int value) {
        if (value <= 0) return 0;
        return (int) Math.ceil(value / 1000.0);
    }

    private static List<Vehicle> sortVehiclesByCapacityDescending(List<Vehicle> vehicles) {
        List<RankedVehicle> ranked = new ArrayList<>();
        for (Vehicle v : vehicles) {
            ranked.add(new RankedVehicle(v, v.comCapacity * v.compartmentNum));
        }
        Collections.sort(ranked);
        vehicles.clear();
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).vehicle.vIndex = i;
            vehicles.add(ranked.get(i).vehicle);
        }
        return vehicles;
    }

    private static void relaxTriangleInequality(int[][] distanceMatrix, int nrStations) {
        for (int i = 0; i < nrStations + 1; i++) {
            for (int j = 0; j < nrStations + 1; j++) {
                for (int k = 0; k < nrStations + 1; k++) {
                    if (i != j && j != k && i != k) {
                        int via = distanceMatrix[i][j] + distanceMatrix[j][k];
                        if (distanceMatrix[i][k] > via) {
                            distanceMatrix[i][k] = via;
                        }
                    }
                }
            }
        }
    }

    private static final class Header {
        int nrTrips = -1;
        int nrVehicles = -1;
        int nrStations = -1;
        int nrCompartments = -1;
        int capacity = -1;
        int nrProducts = -1;
        boolean hasVehicleSection = false;

        void tryParseHeaderLine(String line) {
            // 常见形式：KEY: value
            int colon = line.indexOf(':');
            if (colon < 0) return;
            String key = line.substring(0, colon).trim().toUpperCase();
            String val = line.substring(colon + 1).trim();
            if (val.isEmpty()) return;
            int x;
            try {
                x = Integer.parseInt(val.split("\\s+")[0]);
            } catch (NumberFormatException e) {
                return;
            }
            switch (key) {
                case "TRIPS":
                    nrTrips = x;
                    break;
                case "VEHICLES":
                    nrVehicles = x;
                    break;
                case "NRSTATIONS":
                    nrStations = x;
                    break;
                case "NRCOMPARTMENTS":
                    nrCompartments = x;
                    break;
                case "CAPACITY":
                    capacity = x;
                    break;
                case "NRPRODUCTS":
                    nrProducts = x;
                    break;
                default:
                    // ignore
                    break;
            }
        }

        void validateOrThrow(File file) {
            List<String> missing = new ArrayList<>();
            if (nrTrips < 0) missing.add("TRIPS");
            if (nrVehicles < 0) missing.add("VEHICLES");
            if (nrStations < 0) missing.add("NRSTATIONS");
            if (nrProducts < 0) missing.add("NRPRODUCTS");
            if (!hasVehicleSection) {
                if (nrCompartments < 0) missing.add("NRCOMPARTMENTS");
                if (capacity < 0) missing.add("CAPACITY");
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("实例头部缺少字段 " + missing + " ：" + file.getAbsolutePath());
            }
        }
    }

    private static final class RankedVehicle implements Comparable<RankedVehicle> {
        private final Vehicle vehicle;
        private final Integer rankNumber;

        private RankedVehicle(Vehicle vehicle, int rankNumber) {
            this.vehicle = vehicle;
            this.rankNumber = rankNumber;
        }

        @Override
        public int compareTo(RankedVehicle other) {
            // 降序
            return other.rankNumber.compareTo(this.rankNumber);
        }
    }
}
