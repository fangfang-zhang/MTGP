package yimei.jss.algorithm.featureweighted;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import yimei.jss.helper.Flag;
import yimei.jss.jobshop.FlexibleStaticInstance;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.AbstractRule;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.StaticSimulation;
import yimei.jss.simulation.state.SystemState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//import yimei.jss.algorithm.featureweighted.multiPopCoevolutionaryEvaluator;

/**
 * Created by dyska on 4/07/17.
 */
public class MultipleRuleEvaluationModel extends AbstractEvaluationModel{

    /**
     * The starting seed of the simulation models.
     */
    public final static String P_SIM_SEED = "sim-seed";

    //defined by fzhang, 26.4.2018
    int countBadrun = 0;
    int countInd = 0;
    List<Integer> genNumBadRun = new ArrayList<>();
    protected long jobSeed;

    //modified by fzhang 21.5.2018 to get the number of finished jobs
    protected SystemState systemState;

    /**
     * Whether to rotate the simulation seed or not.
     */
    public final static String P_ROTATE_SIM_SEED = "rotate-sim-seed";
    public final static String P_SIM_MODELS = "sim-models";
    public final static String P_SIM_NUM_MACHINES = "num-machines";
    public final static String P_SIM_NUM_JOBS = "num-jobs";
    public final static String P_SIM_WARMUP_JOBS = "warmup-jobs";
    public final static String P_SIM_MIN_NUM_OPERATIONS = "min-num-operations";
    public final static String P_SIM_MAX_NUM_OPERATIONS = "max-num-operations";
    public final static String P_SIM_UTIL_LEVEL = "util-level";
    public final static String P_SIM_DUE_DATE_FACTOR = "due-date-factor";
    public final static String P_SIM_REPLICATIONS = "replications";

	private static final EvolutionState EvolutionState = null;

    protected SchedulingSet schedulingSet;
    protected long simSeed;
    protected boolean rotateSimSeed;

    public SchedulingSet getSchedulingSet() {
        return schedulingSet;
    }

    public long getSimSeed() {
        return simSeed;
    }

    public boolean isRotateSimSeed() {
        return rotateSimSeed;
    }

    @Override
    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        // Get the seed for the simulation.
        Parameter p = base.push(P_SIM_SEED);
        simSeed = state.parameters.getLongWithDefault(p, null, 0);

        // Get the simulation models.
        p = base.push(P_SIM_MODELS);
        int numSimModels = state.parameters.getIntWithDefault(p, null, 0);

        if (numSimModels == 0) {
            System.err.println("ERROR:");
            System.err.println("No simulation model is specified.");
            System.exit(1);
        }

        List<Simulation> trainSimulations = new ArrayList<>();
        List<Integer> replications = new ArrayList<>();
        for (int x = 0; x < numSimModels; x++) {
            // Read this simulation model
            Parameter b = base.push(P_SIM_MODELS).push("" + x);
            // Number of machines
            p = b.push(P_SIM_NUM_MACHINES);
            int numMachines = state.parameters.getIntWithDefault(p, null, 10);
            // Number of jobs
            p = b.push(P_SIM_NUM_JOBS);
            int numJobs = state.parameters.getIntWithDefault(p, null, 5000);
            // Number of warmup jobs
            p = b.push(P_SIM_WARMUP_JOBS);
            int warmupJobs = state.parameters.getIntWithDefault(p, null, 1000);
            // Min number of operations
            p = b.push(P_SIM_MIN_NUM_OPERATIONS);
            int minNumOperations = state.parameters.getIntWithDefault(p, null, 1);
            // Max number of operations
            p = b.push(P_SIM_MAX_NUM_OPERATIONS);
            int maxNumOperations = state.parameters.getIntWithDefault(p, null, numMachines);
            // Utilization level
            p = b.push(P_SIM_UTIL_LEVEL);
            double utilLevel = state.parameters.getDoubleWithDefault(p, null, 0.85);
            // Due date factor
            p = b.push(P_SIM_DUE_DATE_FACTOR);
            double dueDateFactor = state.parameters.getDoubleWithDefault(p, null, 4.0);
            // Number of replications
            p = b.push(P_SIM_REPLICATIONS);
            int rep = state.parameters.getIntWithDefault(p, null, 1);

            Simulation simulation = null;
            //only expecting filePath parameter for Static FJSS, so can use this
            String filePath = state.parameters.getString(new Parameter("filePath"), null);
            if (filePath == null) {
                //Dynamic Simulation
                simulation = new DynamicSimulation(simSeed,
                        null, null, numMachines, numJobs, warmupJobs,
                        minNumOperations, maxNumOperations,
                        utilLevel, dueDateFactor, false);
            } else {
                FlexibleStaticInstance instance = FlexibleStaticInstance.readFromAbsPath(filePath);
                simulation = new StaticSimulation(null, null, instance);
            }
            trainSimulations.add(simulation);
            replications.add(new Integer(rep));
        }

        schedulingSet = new SchedulingSet(trainSimulations, replications, objectives);

        p = base.push(P_ROTATE_SIM_SEED);
        rotateSimSeed = state.parameters.getBoolean(p, null, false);
    }

    //========================rule evaluatation============================
    @Override
    public void evaluate(List<Fitness> currentFitnesses,
                         List<AbstractRule> rules,
                         EvolutionState state) {
        //expecting 2 rules here - one routing rule and one sequencing rule
        if (rules.size() != currentFitnesses.size() || rules.size() != 2) {
            System.out.println("Rule evaluation failed!");
            System.out.println("Expecting 2 rules, only 1 found.");
            return;
        }
        //System.out.println(rules.size()); //2 repeat
        if(Flag.value == false)
           countInd++;

        AbstractRule sequencingRule = rules.get(0); // for each arraylist in list, they have two elements, the first one is sequencing rule and the second one is routing rule
        AbstractRule routingRule = rules.get(1);
        //System.out.println(objectives.size()); //1  repeat
        //code taken from Abstract Rule
        double[] fitnesses = new double[objectives.size()];

        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;

        //System.out.println(simulations.size()); // 1 repeat
        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat

        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);

            //========================change here======================================
            simulation.setSequencingRule(sequencingRule); //indicate different individuals
            simulation.setRoutingRule(routingRule);
            //System.out.println(simulation);
            simulation.run();

            for (int i = 0; i < objectives.size(); i++) {
            	//fzhang 2018.10.23  cancel normalization process
//                double normObjValue = simulation.objectiveValue(objectives.get(i))  // this line: the value of makespan
//                        / schedulingSet.getObjectiveLowerBound(i, col);

                double ObjValue = simulation.objectiveValue(objectives.get(i));  // this line: the value of makespan

               // multiPopCoevolutionaryEvaluator evalutor = new multiPopCoevolutionaryEvaluator();
                //in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
                    for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                        if (w.numOpsInQueue() > 100) {
                            //this was a bad run
                            //fzhang 2018.10.23  cancel normalization process
//                        normObjValue = Double.MAX_VALUE;
                            //ObjValue = Double.MAX_VALUE;
                            ObjValue = Double.MAX_VALUE;

                            //System.out.println(systemState.getJobsInSystem().size());
                            //System.out.println(systemState.getJobsCompleted().size());
                            //normObjValue = normObjValue*(systemState.getJobsInSystem().size()/systemState.getJobsCompleted().size());
                            if(Flag.value == false)
                               countBadrun++;
                            break;
                        }
                    }

              //fzhang 2018.10.23  cancel normalization process
//                fitnesses[i] += normObjValue;  //the value of fitness is the normalization of the objective value
                fitnesses[i] += ObjValue;
//                System.out.println(fitnesses[i]);
            }
            col++;

            //schedulingSet.getReplications().get(j) = 1, only calculate once, skip this part here
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                simulation.rerun();

                for (int i = 0; i < objectives.size(); i++) {
//                    double normObjValue = simulation.objectiveValue(objectives.get(i))
//                            / schedulingSet.getObjectiveLowerBound(i, col);
//                    fitnesses[i] += normObjValue;

                  //fzhang 2018.10.23  cancel normalization process
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
                    fitnesses[i] += ObjValue; //one object corresponding to one fitness

                }

                col++;
            }

            simulation.reset();
        }

      //modified by fzhang 18.04.2018  in order to check this loop works or not after add filter part: does not work
       // if(countBadrun>0) {
        //System.out.println(state.generation);
     	//System.out.println("The number of badrun grasped in model: "+ countBadrun);
       // }

        for (int i = 0; i < fitnesses.length; i++) {
            fitnesses[i] /= col;
        }

        for (Fitness fitness: currentFitnesses) {
            MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
            f.setObjectives(state, fitnesses);
        }

        //modified by fzhang, write bad run times to *.csv
       // if(countInd % 512 == 0) {
     /*   if(countInd % state.population.subpops[0].individuals.length == 0 && Flag.value == false) {
            genNumBadRun.add(countBadrun);
            countBadrun = 0;
         }*/

        // if(countInd == 1024*512)
      /* if(countInd == state.population.subpops[0].individuals.length*state.population.subpops.length*state.numGenerations)
         WriteCountBadrun(state,null);*/
    }


    //modified by fzhang 26.4.2018   write bad run times to *.csv
    public void WriteCountBadrun(EvolutionState state, final Parameter base) {

    	Parameter p;
		// Get the job seed.
		p = new Parameter("seed").push(""+0);
        jobSeed = state.parameters.getLongWithDefault(p, null, 0);
        File countBadRunFile = new File("job." + jobSeed + ".BadRun.csv");

     	try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(countBadRunFile));
			writer.write("generation,numBadRunSequening, numBadRunRouting,numTotalBadRun");
			writer.newLine();
           /* for(int cutPoint = 0; cutPoint < genNumBadRun.size()/2; cutPoint++) {
   	 	        writer.write(cutPoint + "," +genNumBadRun.get(2*cutPoint)+ "," + genNumBadRun.get(2*cutPoint+1) + ","
                 + (genNumBadRun.get(2*cutPoint)+genNumBadRun.get(2*cutPoint+1)));*/

            for(int cutPoint = 0; cutPoint < genNumBadRun.size(); cutPoint+=2) {
                writer.write(cutPoint/2 + "," +genNumBadRun.get(cutPoint)+ "," + genNumBadRun.get(cutPoint+1) + ","
                        + (genNumBadRun.get(cutPoint)+genNumBadRun.get(cutPoint+1)));

   		    writer.newLine();
            }
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }


    @Override
    public boolean isRotatable() {
        return rotateSimSeed;
    }

    @Override
    public void rotate() {
        schedulingSet.rotateSeed(objectives);
    }

	@Override
	public void normObjective(List<Fitness> fitnesses, List<AbstractRule> rule, ec.EvolutionState state) {
		// TODO Auto-generated method stub

	}
}
