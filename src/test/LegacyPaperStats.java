package test;

import SA.config.SAConfig;
import SA.SimulatedAnnealing;
import io.Reader;
import model.Instance;
import move.insertion.operators.Insertion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 论文时期的统计输出（遗留代码）。
 *
 * <p>这部分逻辑包含大量“画分布/累计比例/算子分段统计”的实现，变量命名与结构保留历史形态，
 * 主要用于复现实验，不建议作为默认阅读入口。</p>
 *
 * <p>如果你只是想跑算法并看结果，请使用 {@code testSA} 的默认 simple 模式。</p>
 */
public final class LegacyPaperStats {

    private LegacyPaperStats() {}

    public static void run(int startInstanceNo,
                           int endInstanceNo,
                           int runNum,
                           File instanceDir,
                           boolean dumpSolution,
                           File solutionDir,
                           SAConfig saConfig) throws IOException {

        int instanceNum = endInstanceNo - startInstanceNo + 1;

        List<Double> timeConsumptionList = new ArrayList<>();
        List<Double> bestObjList = new ArrayList<>();//每个算例的最优目标值
        List<Double> aveObjList = new ArrayList<>();//每个算例的平均目标值
        List<Double> worstObjList = new ArrayList<>();//每个算例的最差目标值
        List<List<Double>> iteRate = new ArrayList<>();
        List<Double[]> cumulativeBestPercentage = new ArrayList<>();
        List<Double[]> cumulativeIterationPercentage = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            iteRate.add(new ArrayList<>());
            Double[] a = new Double[100];
            Arrays.fill(a, 0.0);
            cumulativeBestPercentage.add(a);
            Double[] b = new Double[100];
            Arrays.fill(b, 0.0);
            cumulativeIterationPercentage.add(b);
        }
        double[] aveIterationsNum = new double[8];//每个算子在所有算例上的平均使用次数
        double[] aveBestNum = new double[8];//每个算子在所有算例上的平均“产生最优解”次数
        double[] aveImproveNum = new double[8];//每个算子在所有算例上的平均“改进当前解”次数
        double[] aveNonImproveNum = new double[8];//每个算子在所有算例上的平均“接受劣解”次数
        int count = 0;

        for (int i = startInstanceNo; i <= endInstanceNo; i++) {
            List<Integer> totalIt = new ArrayList<>();
            List<List<Double>> iterationNum = new ArrayList<>();
            List<List<Double>> bestNum = new ArrayList<>();
            List<List<Double>> improveNum = new ArrayList<>();
            List<List<Double>> nonImproveNum = new ArrayList<>();
            List<Integer> objective = new ArrayList<>();
            List<Long> timeC = new ArrayList<>();
            List<Integer[]> bestIteration;
            bestIteration = new ArrayList<>();
            for (int u = 0; u < 8; u++) {
                iteRate.add(new ArrayList<>());
                Integer[] a = new Integer[10];
                Arrays.fill(a, 0);
                bestIteration.add(a);
            }

            int sumBest = 0;
            for (int t = 0; t < runNum; t++) {
                System.out.println("instanceNo:" + i + " runNo:" + t);
                File file = new File(instanceDir + File.separator + i + ".txt");
                if (!file.exists()) {
                    throw new FileNotFoundException("找不到算例文件：" + file.getAbsolutePath());
                }
                Instance instance = Reader.readInstance(file);
                SimulatedAnnealing sa = new SimulatedAnnealing(instance, saConfig);
                long time = System.currentTimeMillis();
                long timeConsumption = 0;
                sa.run();
                totalIt.add(sa.iterations);
                List<Double> itNum = new ArrayList<>();
                iterationNum.add(itNum);
                List<Double> bNum = new ArrayList<>();
                bestNum.add(bNum);
                List<Double> imNum = new ArrayList<>();
                improveNum.add(imNum);
                List<Double> nImNum = new ArrayList<>();
                nonImproveNum.add(nImNum);
                timeConsumption += System.currentTimeMillis() - time;

                StringBuilder solutionText = null;
                if (dumpSolution) {
                    solutionText = new StringBuilder();
                    for (int k = 0; k < instance.vehicles.size(); k++) {
                        solutionText.append("k").append(k).append(": ").append(sa.routes[k]).append("\n");
                    }
                    solutionText.append(sa.makespanBestSolution).append(" ").append(sa.objectiveBestSolution).append("\n");
                    solutionText.append(timeConsumption).append("\n");
                }
                objective.add(sa.objectiveBestSolution);
                timeC.add(timeConsumption);
                for (int j = 0; j < sa.insertionList.size(); j++) {
                    itNum.add(sa.insertionList.get(j).iterationNum);
                    bNum.add(sa.insertionList.get(j).bestNum);
                    imNum.add(sa.insertionList.get(j).improvedNum);
                    nImNum.add(sa.insertionList.get(j).nonImprovedNum);
                    if (dumpSolution) {
                        solutionText.append(sa.insertionList.get(j).iterationNum).append(" ")
                                .append(sa.insertionList.get(j).bestNum).append(" ")
                                .append(sa.insertionList.get(j).improvedNum).append(" ")
                                .append(sa.insertionList.get(j).nonImprovedNum).append("\n");
                    }
                }
                for (int j = 0; j < sa.removalList.size(); j++) {
                    itNum.add(sa.removalList.get(j).iterationNum);
                    bNum.add(sa.removalList.get(j).bestNum);
                    imNum.add(sa.removalList.get(j).improvedNum);
                    nImNum.add(sa.removalList.get(j).nonImprovedNum);
                    if (dumpSolution) {
                        solutionText.append(sa.removalList.get(j).iterationNum).append(" ")
                                .append(sa.removalList.get(j).bestNum).append(" ")
                                .append(sa.removalList.get(j).improvedNum).append(" ")
                                .append(sa.removalList.get(j).nonImprovedNum).append("\n");
                    }
                }

                if (dumpSolution) {
                    String outName = "instance" + i + "_run" + t + ".txt";
                    File outFile = new File(solutionDir + File.separator + outName);
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                        writer.write(solutionText.toString());
                    }
                }

                // 统计：计算“每个算子在迭代进程中产生 best 的分布比例”
                int itPercent;//每 1% 对应多少次迭代
                itPercent = (int) Math.ceil(sa.iterations * 1.0 / 100);

                int removalBestNum = 0;
                int insertionBestNum = 0;
                for (int l = 0; l < sa.removalList.size(); l++) {
                    removalBestNum += sa.removalList.get(l).bestRecord.size();
                }
                for (int l = 0; l < sa.insertionList.size(); l++) {
                    insertionBestNum += sa.insertionList.get(l).bestRecord.size();
                }
                if (removalBestNum > 0) {
                    count++;
                }
                List<Double[]> cumulativeBestPercentage0 = new ArrayList<>();
                for (int w = 0; w < 8; w++) {
                    Double[] a = new Double[100];
                    Arrays.fill(a, 0.0);
                    cumulativeBestPercentage0.add(a);
                }
                for (int k = 0; k < 100; k++) {
                    for (int l = 0; l < sa.removalList.size(); l++) {
                        for (int p = 0; p < sa.removalList.get(l).iterationRecord.size(); p++) {
                            if (sa.removalList.get(l).iterationRecord.get(p) <= (k + 1) * itPercent) {
                                cumulativeIterationPercentage.get(l)[k] += 1.0 / sa.iterations;
                            }
                        }
                        for (int p = 0; p < sa.removalList.get(l).bestRecord.size(); p++) {
                            if (sa.removalList.get(l).bestRecord.get(p) <= (k + 1) * itPercent) {
                                cumulativeBestPercentage0.get(l)[k] += 1;
                            }
                        }
                    }
                    for (int l = 0; l < sa.insertionList.size(); l++) {
                        for (int p = 0; p < sa.insertionList.get(l).iterationRecord.size(); p++) {
                            if (sa.insertionList.get(l).iterationRecord.get(p) <= (k + 1) * itPercent) {
                                cumulativeIterationPercentage.get(l + sa.removalList.size())[k] += 1.0 / sa.iterations;
                            }
                        }
                        for (int p = 0; p < sa.insertionList.get(l).bestRecord.size(); p++) {
                            if (sa.insertionList.get(l).bestRecord.get(p) <= (k + 1) * itPercent) {
                                cumulativeBestPercentage0.get(l + sa.removalList.size())[k] += 1;
                            }
                        }
                    }
                }

                for (int k = 0; k < 10; k++) {
                    for (int l = 0; l < sa.removalList.size(); l++) {
                        for (int p = 0; p < sa.removalList.get(l).bestRecord.size(); p++) {
                            if (sa.removalList.get(l).bestRecord.get(p) > 10 * (k) * itPercent && sa.removalList.get(l).bestRecord.get(p) <= 10 * (k + 1) * itPercent) {
                                bestIteration.get(l)[k] += 1;
                            }
                        }
                    }
                    for (int l = 0; l < sa.insertionList.size(); l++) {
                        for (int p = 0; p < sa.insertionList.get(l).bestRecord.size(); p++) {
                            if (sa.insertionList.get(l).bestRecord.get(p) > (k) * 10 * itPercent && sa.insertionList.get(l).bestRecord.get(p) <= (k + 1) * 10 * itPercent) {
                                bestIteration.get(l + sa.removalList.size())[k] += 1;
                            }
                        }
                    }
                }

                for (Insertion insertion : sa.insertionList) {
                    sumBest += insertion.bestRecord.size();
                }

                for (int k = 0; k < 100; k++) {
                    for (int l = 0; l < sa.removalList.size(); l++) {
                        if (removalBestNum != 0) {
                            cumulativeBestPercentage.get(l)[k] += cumulativeBestPercentage0.get(l)[k] / (1.0 * removalBestNum);
                        }
                    }
                    for (int l = 0; l < sa.insertionList.size(); l++) {
                        if (insertionBestNum != 0) {
                            cumulativeBestPercentage.get(l + sa.removalList.size())[k] += cumulativeBestPercentage0.get(l + sa.removalList.size())[k] / (1.0 * insertionBestNum);
                        }
                    }
                }
            }

            String s0 = "";
            s0 += "best " + sumBest / runNum + "\n";
            for (int k = 0; k < 10; k++) {
                for (int l = 0; l < bestIteration.size(); l++) {
                    s0 += (bestIteration.get(l)[k] * 1.0) / runNum + " ";
                }
                s0 = s0.trim();
                s0 += "\n";
            }
            System.out.println(s0);

            Double[] aveIteration = new Double[iterationNum.get(0).size()];
            Double[] aveBest = new Double[iterationNum.get(0).size()];
            Double[] aveImp = new Double[iterationNum.get(0).size()];
            Double[] aveNonImp = new Double[iterationNum.get(0).size()];
            Arrays.fill(aveIteration, 0.0);
            Arrays.fill(aveBest, 0.0);
            Arrays.fill(aveImp, 0.0);
            Arrays.fill(aveNonImp, 0.0);

            double aveObj = 0;
            double aveTime = 0;
            double bestObj = objective.get(0);
            double worstObj = objective.get(0);
            for (int j = 0; j < iterationNum.size(); j++) {
                for (int k = 0; k < iterationNum.get(j).size(); k++) {
                    aveIteration[k] += iterationNum.get(j).get(k);
                    iteRate.get(k).add(iterationNum.get(j).get(k) / (1.0 * totalIt.get(j)));
                    aveBest[k] += bestNum.get(j).get(k);
                    aveImp[k] += improveNum.get(j).get(k);
                    aveNonImp[k] += nonImproveNum.get(j).get(k);
                }
                aveObj += objective.get(j) * 1.0;
                aveTime += timeC.get(j) * 1.0 / 1000;
            }
            aveObj /= iterationNum.size();
            aveTime /= iterationNum.size();
            for (int k = 0; k < aveIteration.length; k++) {
                aveIteration[k] /= iterationNum.size();

                aveIterationsNum[k] += aveIteration[k];
                aveBest[k] /= iterationNum.size();
                aveBestNum[k] += aveBest[k];
                aveImp[k] /= iterationNum.size();
                aveImproveNum[k] += aveImp[k];
                aveNonImp[k] /= iterationNum.size();
                aveNonImproveNum[k] += aveNonImp[k];
            }
            for (int k = 1; k < iterationNum.size(); k++) {
                if (bestObj > objective.get(k)) {
                    bestObj = objective.get(k);
                }
            }
            for (int k = 1; k < iterationNum.size(); k++) {
                if (worstObj < objective.get(k)) {
                    worstObj = objective.get(k);
                }
            }
            bestObjList.add(bestObj);
            aveObjList.add(aveObj);
            worstObjList.add(worstObj);
            timeConsumptionList.add(aveTime);
        }

        System.out.println("result conclusion: ");
        for (int k = 0; k < aveIterationsNum.length; k++) {
            aveIterationsNum[k] /= instanceNum;
            aveBestNum[k] /= instanceNum;
            aveImproveNum[k] /= instanceNum;
            aveNonImproveNum[k] /= instanceNum;
        }
        for (int k = 0; k < 100; k++) {
            for (int l = 0; l < cumulativeIterationPercentage.size(); l++) {
                cumulativeIterationPercentage.get(l)[k] /= (instanceNum * runNum);
                cumulativeBestPercentage.get(l)[k] /= (count);
            }
        }
        String s = "";
        s += "objective: best;ave;worst;time\n";
        for (int i = 0; i < bestObjList.size(); i++) {
            s += bestObjList.get(i) + " " + aveObjList.get(i) + " " + worstObjList.get(i) + " " + timeConsumptionList.get(i) + "\n";
        }
        s += "\n operator: iter;best;imp;nonImp\n";
        for (int k = 0; k < aveIterationsNum.length; k++) {
            s += aveIterationsNum[k] + " " + aveBestNum[k] + " " + aveImproveNum[k] + " " + aveNonImproveNum[k] + "\n";
        }
        s += "\n how many percent each operator is used by each run\n";
        for (int k = 0; k < iteRate.size(); k++) {
            for (int i = 0; i < iteRate.get(k).size(); i++) {
                s += String.format("%.2f", iteRate.get(k).get(i)) + " ";
            }
            s = s.trim();
            s += "\n";
        }
        s += "\n cumulative best\n";
        for (int i = 0; i < cumulativeBestPercentage.size(); i++) {
            for (int k = 0; k < 100; k++) {
                s += String.format("%.2f", cumulativeBestPercentage.get(i)[k] * 100) + " ";
            }
            s = s.trim();
            s += "\n";
        }
        s += "\n cumulative iteration\n";
        for (int i = 0; i < cumulativeIterationPercentage.size(); i++) {
            for (int k = 0; k < 100; k++) {
                s += String.format("%.2f", cumulativeIterationPercentage.get(i)[k] * 100) + " ";
            }
            s = s.trim();
            s += "\n";
        }

        System.out.println(s);
    }
}


