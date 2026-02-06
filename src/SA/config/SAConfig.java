package SA.config;

/**
 * Centralized configuration for the ALNS+SA heuristic.
 *
 * <p>This class exists to avoid scattered "magic numbers" across the codebase and to make it
 * easier for students to reproduce the paper settings and experiment with parameter changes.</p>
 *
 * <p>Defaults are chosen to match the current implementation and (where applicable) the paper's
 * parameter table (e.g. nonImpmax=1000, timeLimit=3600s, Nsg=100, alpha=100, beta=1).</p>
 */
public class SAConfig {
    /** Objective weight for makespan (alpha in paper). Total travel time has implicit weight 1 (beta). */
    public int makespanMultiplier = 100;

    /** Random seed used by parts of the heuristic. */
    public long randomSeed = 0L;

    /** Number of SA restarts (currently the main loop uses 1 run by default). */
    public int saRestarts = 1;

    /** Initial acceptance probability used by warmup-based temperature estimation (if enabled). */
    public double initialAcceptanceProbability = 0.45;

    /** Initial temperature (paper default: 200). Warmup is currently disabled, so this is used directly. */
    public double initialTemperature = 200.0;

    /** Final temperature (paper uses a cooling rate; current code uses exponential schedule to this value). */
    public double finalTemperature = 1.0;

    /** SA iteration cap is computed as: N * saMaxIterationsScalar. */
    public double saMaxIterationsScalar = 200.0;

    /** Maximum number of nodes removed per iteration: max(N * removeMaxNumRate, 2). */
    public double removeMaxNumRate = 0.45;

    /** Reaction factor for ALNS weight update (paper: xi). */
    public double reactionFactor = 0.5;

    /** Segment length (paper: Nsg). */
    public int segmentIterations = 100;

    /** Number of initial iterations using uniform operator selection (before adaptive weights kick in). */
    public int uniformSelectionIterations = 100;

    /** Reward scores (paper: sigma1/sigma2/sigma3). */
    public int rhoNewBest = 5;
    public int rhoImprovesCurrent = 3;
    public int rhoAcceptedWorse = 1;

    /** Shaw removal similarity parameters (paper: phi/psi/chi-like weights). */
    public double shawDistanceWeight = 3.0;
    public double shawDemandWeight = 1.0;
    public double shawSameTripWeight = 2.0;

    /** Termination conditions (paper: nonImpmax=1000, timeLimit=3600s). */
    public int maxNonImprovingIterations = 1000;
    public long timeLimitMs = 3_600_000L;
}


