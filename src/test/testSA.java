package test;

import SA.config.SAConfig;
import SA.SimulatedAnnealing;
import io.Reader;
import model.Instance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验运行入口 / 命令行入口（教学版）。
 *
 * <p>建议学生先只关心这件事：如何读取算例并运行 {@link SimulatedAnnealing}。</p>
 *
 * <p>本文件默认只做“跑实验 + 打印汇总 +（可选）导出解”。如果你需要复现论文时期的复杂统计输出，
 * 请使用 {@code --mode=legacy}（对应 {@link LegacyPaperStats}）。</p>
 */
public class testSA {

    public static void main(String[] args) throws IOException {
        ExperimentConfig cfg = ExperimentConfig.fromArgs(args);
        if (cfg.help) {
            printUsage();
            return;
        }

        validateConfig(cfg);

        SAConfig saConfig = new SAConfig();
        saConfig.timeLimitMs = getLong(cfg.params, "timeLimitMs", saConfig.timeLimitMs);
        saConfig.maxNonImprovingIterations = getInt(cfg.params, "maxNonImprovingIterations", saConfig.maxNonImprovingIterations);
        saConfig.randomSeed = getLong(cfg.params, "seed", saConfig.randomSeed);
        saConfig.saRestarts = getInt(cfg.params, "saRestarts", saConfig.saRestarts);

        if (cfg.mode == ExperimentMode.LEGACY) {
            LegacyPaperStats.run(
                    cfg.startInstanceNo,
                    cfg.endInstanceNo,
                    cfg.runNum,
                    cfg.instanceDir,
                    cfg.dumpSolution,
                    cfg.solutionDir,
                    saConfig
            );
            return;
        }

        runSimple(cfg, saConfig);
    }

    /**
     * 教学版最常用的运行模式：每个算例跑多次，打印 best/avg/worst 与平均耗时。
     */
    private static void runSimple(ExperimentConfig cfg, SAConfig saConfig) throws IOException {
        int instanceNum = cfg.endInstanceNo - cfg.startInstanceNo + 1;

        List<Double> bestObjList = new ArrayList<>();
        List<Double> aveObjList = new ArrayList<>();
        List<Double> worstObjList = new ArrayList<>();
        List<Double> aveTimeSecondsList = new ArrayList<>();

        for (int i = cfg.startInstanceNo; i <= cfg.endInstanceNo; i++) {
            List<Integer> objectives = new ArrayList<>();
            List<Long> runTimesMs = new ArrayList<>();

            for (int t = 0; t < cfg.runNum; t++) {
                if (cfg.verbose) {
                    System.out.println("instanceNo=" + i + " runNo=" + t);
                }

                Instance instance = readInstance(cfg.instanceDir, i);
                SimulatedAnnealing sa = new SimulatedAnnealing(instance, saConfig);

                long start = System.currentTimeMillis();
                sa.run();
                long elapsed = System.currentTimeMillis() - start;

                objectives.add(sa.objectiveBestSolution);
                runTimesMs.add(elapsed);

                if (cfg.printBestRoutes) {
                    sa.printBestRoutes();
                }

                if (cfg.dumpSolution) {
                    dumpSolution(cfg.solutionDir, i, t, sa, elapsed);
                }
            }

            double aveObj = averageInt(objectives);
            int bestObj = minInt(objectives);
            int worstObj = maxInt(objectives);
            double aveTimeSec = averageLong(runTimesMs) / 1000.0;

            bestObjList.add((double) bestObj);
            aveObjList.add(aveObj);
            worstObjList.add((double) worstObj);
            aveTimeSecondsList.add(aveTimeSec);

            System.out.println("算例 " + i + "：best=" + bestObj + " avg=" + String.format("%.2f", aveObj) +
                    " worst=" + worstObj + " avgTime=" + String.format("%.3f", aveTimeSec) + "s");
        }

        // 总结输出（面向学生：简单直观）
        System.out.println();
        System.out.println("=== 汇总（共 " + instanceNum + " 个算例，每个算例运行 " + cfg.runNum + " 次）===");
        System.out.println("objective：best avg worst time(s)");
        for (int idx = 0; idx < bestObjList.size(); idx++) {
            System.out.println(bestObjList.get(idx).intValue() + " " +
                    String.format("%.2f", aveObjList.get(idx)) + " " +
                    worstObjList.get(idx).intValue() + " " +
                    String.format("%.3f", aveTimeSecondsList.get(idx)));
        }
    }

    private static Instance readInstance(File instanceDir, int instanceNo) throws IOException {
        File file = new File(instanceDir, instanceNo + ".txt");
        if (!file.exists()) {
            throw new FileNotFoundException("找不到算例文件：" + file.getAbsolutePath());
        }
        return Reader.readInstance(file);
    }

    /**
     * 将一次运行的最优解导出为文本（用于复现/调试）。
     */
    private static void dumpSolution(File solutionDir, int instanceNo, int runNo, SimulatedAnnealing sa, long elapsedMs) throws IOException {
        String outName = "instance" + instanceNo + "_run" + runNo + ".txt";
        File outFile = new File(solutionDir, outName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
            for (int k = 0; k < sa.dataModel.vehicles.size(); k++) {
                writer.write("k" + k + ": " + sa.routes[k] + "\n");
            }
            writer.write(sa.makespanBestSolution + " " + sa.objectiveBestSolution + "\n");
            writer.write(elapsedMs + "\n");
        }
    }

    private static void validateConfig(ExperimentConfig cfg) throws IOException {
        if (cfg.endInstanceNo < cfg.startInstanceNo) {
            throw new IllegalArgumentException("参数错误：end 必须 >= start");
        }
        if (cfg.runNum <= 0) {
            throw new IllegalArgumentException("参数错误：runs 必须 > 0");
        }
        if (!cfg.instanceDir.exists() || !cfg.instanceDir.isDirectory()) {
            throw new FileNotFoundException("找不到算例目录：" + cfg.instanceDir.getAbsolutePath());
        }
        if (cfg.dumpSolution) {
            if (!cfg.solutionDir.exists() && !cfg.solutionDir.mkdirs()) {
                throw new IOException("无法创建解输出目录：" + cfg.solutionDir.getAbsolutePath());
            }
        }
    }

    private enum ExperimentMode {
        SIMPLE,
        LEGACY
    }

    /**
     * 运行参数（教学版）。
     *
     * <p>支持两种参数形式：</p>
     * <ul>
     *   <li>兼容旧版的位置参数：{@code <start> <end> <runs> <instanceDir?>}</li>
     *   <li>命名参数：{@code --start=1 --end=28 --runs=2 --instanceDir=...}</li>
     * </ul>
     */
    static final class ExperimentConfig {
        final Map<String, String> params;

        final int startInstanceNo;
        final int endInstanceNo;
        final int runNum;
        final File instanceDir;

        final boolean dumpSolution;
        final File solutionDir;

        final boolean printBestRoutes;
        final boolean verbose;
        final boolean help;

        final ExperimentMode mode;

        private ExperimentConfig(Map<String, String> params,
                                 int startInstanceNo,
                                 int endInstanceNo,
                                 int runNum,
                                 File instanceDir,
                                 boolean dumpSolution,
                                 File solutionDir,
                                 boolean printBestRoutes,
                                 boolean verbose,
                                 boolean help,
                                 ExperimentMode mode) {
            this.params = params;
            this.startInstanceNo = startInstanceNo;
            this.endInstanceNo = endInstanceNo;
            this.runNum = runNum;
            this.instanceDir = instanceDir;
            this.dumpSolution = dumpSolution;
            this.solutionDir = solutionDir;
            this.printBestRoutes = printBestRoutes;
            this.verbose = verbose;
            this.help = help;
            this.mode = mode;
        }

        static ExperimentConfig fromArgs(String[] args) {
            String projectRoot = System.getProperty("user.dir");

            int startInstanceNo = 1;
            int endInstanceNo = 28;
            int runNum = 2;
            File instanceDir = new File(projectRoot + File.separator + "data" + File.separator + "Instance" + File.separator + "caseStudy");

            boolean dumpSolution = false;
            File solutionDir = new File(projectRoot + File.separator + "output" + File.separator + "solutions");

            boolean printBestRoutes = false;
            boolean verbose = false;

            Map<String, String> params = parseArgs(args);

            boolean help = getBool(params, "help", false);
            startInstanceNo = getInt(params, "start", startInstanceNo);
            endInstanceNo = getInt(params, "end", endInstanceNo);
            runNum = getInt(params, "runs", runNum);
            instanceDir = new File(getString(params, "instanceDir", instanceDir.getPath()));
            dumpSolution = getBool(params, "dumpSolution", dumpSolution);
            solutionDir = new File(getString(params, "solutionDir", solutionDir.getPath()));

            printBestRoutes = getBool(params, "printBestRoutes", printBestRoutes);
            verbose = getBool(params, "verbose", verbose);

            String modeStr = getString(params, "mode", "simple").trim().toLowerCase();
            ExperimentMode mode = modeStr.equals("legacy") ? ExperimentMode.LEGACY : ExperimentMode.SIMPLE;

            return new ExperimentConfig(
                    params,
                    startInstanceNo,
                    endInstanceNo,
                    runNum,
                    instanceDir,
                    dumpSolution,
                    solutionDir,
                    printBestRoutes,
                    verbose,
                    help,
                    mode
            );
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null || args.length == 0) return map;

        // 兼容旧版的位置参数（仅当不存在命名参数时启用）
        boolean hasNamed = false;
        for (String a : args) {
            if (a != null && a.startsWith("--")) {
                hasNamed = true;
                break;
            }
        }
        if (!hasNamed) {
            if (args.length >= 1) map.put("start", args[0]);
            if (args.length >= 2) map.put("end", args[1]);
            if (args.length >= 3) map.put("runs", args[2]);
            if (args.length >= 4) map.put("instanceDir", args[3]);
            return map;
        }

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;
            if (!a.startsWith("--")) continue;
            String keyVal = a.substring(2);
            if (keyVal.isEmpty()) continue;

            String key;
            String val;
            int eq = keyVal.indexOf('=');
            if (eq >= 0) {
                key = keyVal.substring(0, eq).trim();
                val = keyVal.substring(eq + 1).trim();
            } else {
                key = keyVal.trim();
                // 没写值的 flag（例如 --help）视为 true
                val = "true";
                if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                    val = args[i + 1].trim();
                    i++;
                }
            }
            if (!key.isEmpty()) map.put(key, val);
        }
        return map;
    }

    private static int getInt(Map<String, String> params, String key, int defaultVal) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) return defaultVal;
        return Integer.parseInt(v.trim());
    }

    private static long getLong(Map<String, String> params, String key, long defaultVal) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) return defaultVal;
        return Long.parseLong(v.trim());
    }

    private static boolean getBool(Map<String, String> params, String key, boolean defaultVal) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) return defaultVal;
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y");
    }

    private static String getString(Map<String, String> params, String key, String defaultVal) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) return defaultVal;
        return v.trim();
    }

    private static double averageInt(List<Integer> xs) {
        if (xs.isEmpty()) return 0.0;
        long sum = 0;
        for (int x : xs) sum += x;
        return sum * 1.0 / xs.size();
    }

    private static double averageLong(List<Long> xs) {
        if (xs.isEmpty()) return 0.0;
        long sum = 0;
        for (long x : xs) sum += x;
        return sum * 1.0 / xs.size();
    }

    private static int minInt(List<Integer> xs) {
        int m = xs.get(0);
        for (int x : xs) m = Math.min(m, x);
        return m;
    }

    private static int maxInt(List<Integer> xs) {
        int m = xs.get(0);
        for (int x : xs) m = Math.max(m, x);
        return m;
    }

    private static void printUsage() {
        System.out.println("用法：");
        System.out.println("  位置参数（兼容旧版）：<start> <end> <runs> <instanceDir?>");
        System.out.println("  命名参数（推荐）：");
        System.out.println("    --start=1 --end=28 --runs=2 \\");
        System.out.println("    --instanceDir=./data/Instance/caseStudy \\");
        System.out.println("    --dumpSolution=true --solutionDir=./output/solutions");
        System.out.println();
        System.out.println("常用可选项：");
        System.out.println("  --printBestRoutes=true   打印最优解路线（可能很长，默认 false）");
        System.out.println("  --verbose=true           打印更详细的运行信息（默认 false）");
        System.out.println("  --mode=simple|legacy     simple 为教学版；legacy 运行论文时期统计（默认 simple）");
        System.out.println();
        System.out.println("可选 SA/ALNS 参数（用于冒烟/复现实验）：");
        System.out.println("  --timeLimitMs=1200 --maxNonImprovingIterations=60 --seed=1 --saRestarts=1");
        System.out.println();
        System.out.println("Flags：");
        System.out.println("  --help");
    }
}

 
