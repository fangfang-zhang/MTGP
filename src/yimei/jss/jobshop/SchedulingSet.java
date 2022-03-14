package yimei.jss.jobshop;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.rule.AbstractRule;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * The set of scheduling problems. The set includes:
 *   1. A list of simulations.
 *   2. Number of replications for each simulation.
 *   3. The objective lower bound matrix: (i,j) - the lower bound of objective i in replication j.
 *
 * Created by YiMei on 28/09/16.
 */
public class SchedulingSet {

    private List<Simulation> simulations;
    private List<Integer> replications;
    private RealMatrix objectiveLowerBoundMtx;

    public SchedulingSet(List<Simulation> simulations,
                         List<Integer> replications,
                         List<Objective> objectives) {
        this.simulations = simulations;
        this.replications = replications;
        createObjectiveLowerBoundMatrix(objectives);
        
        //fzhang 2018.12.20 if we do not want use the lowerBounds, just comment this
        //lowerBoundsFromBenchmarkRule(objectives);
    }

    public List<Simulation> getSimulations() {
        return simulations;
    }

    public List<Integer> getReplications() {
        return replications;
    }

    public RealMatrix getObjectiveLowerBoundMtx() {
        return objectiveLowerBoundMtx;
    }

    public double getObjectiveLowerBound(int row, int col) {
        return objectiveLowerBoundMtx.getEntry(row, col);
    }

    public void setReplications(List<Integer> replications) {
        this.replications = replications;
    }

    public void setRule(AbstractRule rule) {
        for (Simulation simulation : simulations) {
            simulation.setSequencingRule(rule);
        }
    }

    public void rotateSeed(List<Objective> objectives) {
        for (Simulation simulation : simulations) {
            simulation.rotateSeed();
        }

        //fzhang 2018.12.20  if we do not use lowerBounds, just comment it
        //lowerBoundsFromBenchmarkRule(objectives);
    }

    private void createObjectiveLowerBoundMatrix(List<Objective> objectives) {
        int rows = objectives.size();
        int cols = 0;
        for (int rep : replications) {
            cols += rep;
        }
        objectiveLowerBoundMtx = new Array2DRowRealMatrix(rows, cols);
    }

    private void lowerBoundsFromBenchmarkRule(List<Objective> objectives) {
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            AbstractRule benchmarkSeqRule = objective.benchmarkSequencingRule();
            AbstractRule benchmarkRoutingRule = objective.benchmarkRoutingRule();

            int col = 0;
            for (int j = 0; j < simulations.size(); j++) {
                Simulation simulation = simulations.get(j);
                simulation.setSequencingRule(benchmarkSeqRule);
                simulation.setRoutingRule(benchmarkRoutingRule);
                simulation.rerun(); //this will make sure benchmark rules affect everything

                double value = simulation.objectiveValue(objective);
                objectiveLowerBoundMtx.setEntry(i, col, value);
//                System.out.println("objective1LowerBound: "+ this.getObjectiveLowerBound(i, col));
                
                col ++;

                for (int k = 1; k < replications.get(j); k++) {
                    simulation.rerun();
                    value = simulation.objectiveValue(objective);
                    objectiveLowerBoundMtx.setEntry(i, col, value);
                    col ++;
                }
                simulation.reset();
            }

        }
    }

    public SchedulingSet surrogate(int numWorkCenters, int numJobsRecorded,
                                   int warmupJobs, List<Objective> objectives) {
        List<Simulation> surrogateSimulations = new ArrayList<>();
        List<Integer> surrogateReplications = new ArrayList<>();

        for (int i = 0; i < simulations.size(); i++) {
            surrogateSimulations.add(
                    simulations.get(i).surrogate(
                    numWorkCenters, numJobsRecorded, warmupJobs));
            surrogateReplications.add(1);
        }

        return new SchedulingSet(surrogateSimulations,
                surrogateReplications, objectives);
    }

    public SchedulingSet surrogateBusy(int numWorkCenters, int numJobsRecorded,
                                   int warmupJobs, List<Objective> objectives) {
        List<Simulation> surrogateSimulations = new ArrayList<>();
        List<Integer> surrogateReplications = new ArrayList<>();

        for (int i = 0; i < simulations.size(); i++) {
            surrogateSimulations.add(
                    simulations.get(i).surrogateBusy(
                            numWorkCenters, numJobsRecorded, warmupJobs));
            surrogateReplications.add(1);
        }

        return new SchedulingSet(surrogateSimulations,
                surrogateReplications, objectives);
    }

    public static SchedulingSet dynamicFullSet(long simSeed,
                                               double utilLevel,
                                               double dueDateFactor,
                                               List<Objective> objectives,
                                               int reps) {
        List<Simulation> simulations = new ArrayList<>();
        
        //original
      /*  simulations.add(
                DynamicSimulation.standardFull(simSeed, null, null, 10, 4000, 1000,
                        utilLevel, dueDateFactor));*/
        
      //fzhang 2019.2.12 test should be also with 5000 jobs  
        simulations.add(
                DynamicSimulation.standardFull(simSeed, null, null, 10, 5000, 1000,
                        utilLevel, dueDateFactor));
        List<Integer> replications = new ArrayList<>();
        replications.add(reps);

        return new SchedulingSet(simulations, replications, objectives);
    }

    public static SchedulingSet dynamicMissingSet(long simSeed,
                                                  double utilLevel,
                                                  double dueDateFactor,
                                                  List<Objective> objectives,
                                                  int reps) {
        List<Simulation> simulations = new ArrayList<>();
      //original  
    /*    simulations.add(
                DynamicSimulation.standardMissing(simSeed, null, null, 10, 4000, 1000,
                        utilLevel, dueDateFactor));*/
        
        //fzhang 2019.2.12 test should be also with 5000 jobs    
        simulations.add(
                DynamicSimulation.standardMissing(simSeed, null, null, 10, 5000, 1000,
                        utilLevel, dueDateFactor));
        
        List<Integer> replications = new ArrayList<>();
        replications.add(reps);
        return new SchedulingSet(simulations, replications, objectives);
    }

    public static SchedulingSet generateSet(long simSeed,
                                            String scenario,
                                            String setName,
                                            List<Objective> objectives,
                                            int replications) {
        if (scenario.equals(Scenario.DYNAMIC_JOB_SHOP.getName())) {
            String[] parameters = setName.split("-");  //for example: missing-0.95-4.0      utilLevel = parameters[1] = 0.85
            double utilLevel = Double.valueOf(parameters[1]);
            double dueDateFactor = Double.valueOf(parameters[2]); //dueDateFactor = parameters[2] = 4

            if (parameters[0].equals("missing")) {
                return SchedulingSet.dynamicMissingSet(simSeed, utilLevel, dueDateFactor, objectives, replications);
            }
            else if (parameters[0].equals("full")) {
                return SchedulingSet.dynamicFullSet(simSeed, utilLevel, dueDateFactor, objectives, replications);
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchedulingSet that = (SchedulingSet) o;

        if (!simulations.equals(that.simulations)) return false;
        if (!replications.equals(that.replications)) return false;
        return objectiveLowerBoundMtx.equals(that.objectiveLowerBoundMtx);
    }

    @Override
    public int hashCode() {
        int result = simulations.hashCode();
        result = 31 * result + replications.hashCode();
        result = 31 * result + objectiveLowerBoundMtx.hashCode();
        return result;
    }
}
